package com.craxiom.networksurvey.ui.wifi.model

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.craxiom.networksurvey.util.PreferenceUtils

/** What stops the phone from scanning for Wi-Fi, in the order the screens report it. */
enum class WifiScanState {
    PERMISSION_NEEDED,
    LOCATION_OFF,

    /** Wi-Fi is off and so is Wi-Fi scanning (Location > Wi-Fi scanning), so no scan can run. */
    WIFI_OFF,
    READY,
}

/** Reads whether the phone can scan for Wi-Fi. An interface so tests can fake it. */
fun interface WifiEnvironment {
    /** @return The first thing that stops a scan, or [WifiScanState.READY]. */
    fun scanState(): WifiScanState

    companion object {
        /**
         * What a Wi-Fi scan needs granted: precise location, without which Android withholds scan
         * results. Coarse location is asked with it, since Android 12 and later ignore a request for
         * fine location alone.
         */
        val PERMISSIONS: Array<String> =
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        /**
         * Broadcast when Wi-Fi scanning while Wi-Fi is off is turned on or off, on the Android
         * versions that send it; elsewhere the resume check catches the change.
         */
        private const val ACTION_WIFI_SCAN_AVAILABILITY_CHANGED = "android.net.wifi.action.WIFI_SCAN_AVAILABILITY_CHANGED"

        /** The broadcasts that can change [scanState]; a permission change is caught on resume. */
        val ACTIONS: List<String> = listOf(
            WifiManager.WIFI_STATE_CHANGED_ACTION,
            LocationManager.PROVIDERS_CHANGED_ACTION,
            ACTION_WIFI_SCAN_AVAILABILITY_CHANGED,
        )
    }
}

/**
 * The [WifiEnvironment] of this phone. Wi-Fi off only blocks a scan when Wi-Fi scanning is off
 * too: with it on, Android keeps scanning with the radio off (checked on Android 16).
 */
class AndroidWifiEnvironment(private val context: Context) : WifiEnvironment {
    override fun scanState(): WifiScanState {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return WifiScanState.PERMISSION_NEEDED
        val locationManager = context.getSystemService(LocationManager::class.java)
        if (locationManager != null && !LocationManagerCompat.isLocationEnabled(locationManager)) {
            return WifiScanState.LOCATION_OFF
        }
        val wifiManager = context.getSystemService(WifiManager::class.java) ?: return WifiScanState.WIFI_OFF
        return if (wifiManager.isWifiEnabled || wifiManager.isScanAlwaysAvailable) WifiScanState.READY else WifiScanState.WIFI_OFF
    }
}

/** What the throttling check reads from the phone. */
data class WifiThrottleInputs(
    /** Android 11+: whether Android throttles Wi-Fi scans (a developer option). */
    val throttleEnabled: Boolean,
    val developerOptionsOn: Boolean,
    /** The user or the MDM chose never to see the warning. */
    val ignored: Boolean,
)

/** Reads [WifiThrottleInputs]. An interface so tests can fake it. */
fun interface WifiThrottleProbe {
    fun read(): WifiThrottleInputs
}

/** The [WifiThrottleProbe] of this phone. */
class AndroidWifiThrottleProbe(private val context: Context) : WifiThrottleProbe {
    override fun read(): WifiThrottleInputs {
        val wifiManager = context.getSystemService(WifiManager::class.java)
        return WifiThrottleInputs(
            throttleEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifiManager?.isScanThrottleEnabled == true,
            developerOptionsOn = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0,
            ignored = PreferenceUtils.getIgnoreWifiThrottlingWarningPreference(false, context),
        )
    }
}
