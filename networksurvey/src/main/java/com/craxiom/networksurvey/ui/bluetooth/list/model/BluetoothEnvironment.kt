package com.craxiom.networksurvey.ui.bluetooth.list.model

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.craxiom.networksurvey.AppPermissions
import timber.log.Timber

/** Reads whether the phone can scan for Bluetooth devices. An interface so tests can fake it. */
fun interface BluetoothEnvironment {
    /** @return The first thing that stops a scan, or [BluetoothScanState.READY]. */
    fun scanState(): BluetoothScanState

    companion object {
        /**
         * What a Bluetooth scan needs granted: Nearby devices from Android 12, and fine location on
         * every version, because the manifest does not declare BLUETOOTH_SCAN as never used for
         * location, so Android withholds scan results from an app without it. Coarse location is
         * asked with it, since Android 12 and later ignore a request for fine location alone.
         */
        val PERMISSIONS: Array<String> = AppPermissions.BLUETOOTH_PERMISSIONS +
            Manifest.permission.ACCESS_FINE_LOCATION + Manifest.permission.ACCESS_COARSE_LOCATION
    }
}

/** The [BluetoothEnvironment] of this phone. */
class AndroidBluetoothEnvironment(private val context: Context) : BluetoothEnvironment {
    override fun scanState(): BluetoothScanState {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return BluetoothScanState.NOT_SUPPORTED
        val missing = BluetoothEnvironment.PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) return BluetoothScanState.PERMISSION_NEEDED
        val locationManager = context.getSystemService(LocationManager::class.java)
        if (locationManager != null && !LocationManagerCompat.isLocationEnabled(locationManager)) {
            return BluetoothScanState.LOCATION_OFF
        }
        val enabled = try {
            adapter.isEnabled
        } catch (e: SecurityException) {
            Timber.w(e, "Could not read whether Bluetooth is on")
            false
        }
        return if (enabled) BluetoothScanState.READY else BluetoothScanState.BLUETOOTH_OFF
    }
}
