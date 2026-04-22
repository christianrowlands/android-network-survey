package com.craxiom.networksurvey.data.oui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import timber.log.Timber

/**
 * Watches for connectivity restoration. On [android.net.ConnectivityManager.NetworkCallback.onAvailable]
 * the [OuiRepository]'s transient L1 entries (OFFLINE / TRANSIENT_FAILURE) are flushed so the next
 * UI request can re-try instead of serving stale negative cache.
 *
 * Registered once by [OuiRepository.getInstance] on first access. Never unregistered, process lifetime.
 */
class OuiConnectivityMonitor(
    context: Context,
    private val onNetworkAvailable: () -> Unit
) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Timber.d("Network available; clearing OUI transient cache")
            try {
                onNetworkAvailable()
            } catch (t: Throwable) {
                Timber.w(t, "onNetworkAvailable callback failed")
            }
        }
    }

    fun start() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (t: Throwable) {
            Timber.w(t, "Failed to register network callback")
        }
    }
}
