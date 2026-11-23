package com.craxiom.networksurvey.ui.wifi.model

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import java.util.Locale

/**
 * The BSSID of the access point the phone is connected to, so the Wi-Fi screens can mark it.
 *
 * Android 12+ reads it from a Wi-Fi network callback, which reports a roam to another access point
 * as it happens; Android 8 to 11 reads the connection info on each Wi-Fi network state broadcast.
 * Without the location permission Android hides the BSSID, which reads as not connected.
 */
object ConnectedWifi {

    /** What Android reports in place of the BSSID when the app may not see it. */
    private const val HIDDEN_BSSID = "02:00:00:00:00:00"

    /**
     * @return The connected access point's BSSID in lowercase, or null when it disconnects (or when
     * Android hides it). Emits the current access point soon after collection starts (Android 12+
     * emits nothing while not connected), then each change, until collection stops.
     */
    fun bssids(context: Context): Flow<String?> {
        val appContext = context.applicationContext
        val flow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) callbackBssids(appContext) else broadcastBssids(appContext)
        return flow.distinctUntilChanged()
    }

    /** @return The BSSID in lowercase, or null when it is missing or hidden. */
    internal fun normalize(bssid: String?): String? =
        bssid?.lowercase(Locale.ROOT)?.takeUnless { it.isBlank() || it == HIDDEN_BSSID }

    /** @return True when [bssid] is the [connected] access point's (case is ignored). */
    fun isConnected(bssid: String, connected: String?): Boolean =
        connected != null && bssid.equals(connected, ignoreCase = true)

    @RequiresApi(Build.VERSION_CODES.S)
    private fun callbackBssids(context: Context): Flow<String?> = callbackFlow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        // More than one Wi-Fi network can be up at once (Wi-Fi STA + STA), so each keeps its own BSSID
        val byNetwork = mutableMapOf<Network, String?>()
        fun emit() {
            trySend(byNetwork.values.firstNotNullOfOrNull { it })
        }

        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                byNetwork[network] = normalize((capabilities.transportInfo as? WifiInfo)?.bssid)
                emit()
            }

            override fun onLost(network: Network) {
                byNetwork.remove(network)
                emit()
            }
        }
        // Any Wi-Fi network, including one without internet access (a lab AP, for example)
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        // Android reports each connected Wi-Fi network as soon as the callback is registered
        try {
            connectivity.registerNetworkCallback(request, callback)
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not watch the connected Wi-Fi network")
            close()
            return@callbackFlow
        }
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }

    @Suppress("DEPRECATION") // WifiManager.connectionInfo is the only source before Android 12
    private fun broadcastBssids(context: Context): Flow<String?> = callbackFlow {
        val wifiManager = context.getSystemService(WifiManager::class.java)
        fun read(): String? = try {
            normalize(wifiManager?.connectionInfo?.bssid)
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not read the connected Wi-Fi network")
            null
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(read())
            }
        }
        trySend(read())
        // NETWORK_STATE_CHANGED_ACTION also fires on a roam to another access point
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
