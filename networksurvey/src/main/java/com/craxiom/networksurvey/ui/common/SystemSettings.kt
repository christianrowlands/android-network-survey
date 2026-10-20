package com.craxiom.networksurvey.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.craxiom.networksurvey.R
import timber.log.Timber

/**
 * Opens a system settings screen by its intent action; when the phone has no such screen (some
 * vendor or managed builds), says so instead of doing nothing.
 */
fun openSystemSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action))
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "Could not open %s", action)
        Toast.makeText(context, R.string.settings_not_available, Toast.LENGTH_SHORT).show()
    }
}

/** Opens the location settings, where the user can turn location on. */
fun openLocationSettings(context: Context) = openSystemSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS)

/**
 * Opens Android's Wi-Fi panel (a sheet over the app, Android 10+) or the Wi-Fi settings, where the
 * user can turn Wi-Fi on. Apps cannot turn Wi-Fi on themselves since Android 10.
 */
fun openWifiSettings(context: Context) = openSystemSettings(
    context,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS,
)
