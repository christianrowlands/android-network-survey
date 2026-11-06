package com.craxiom.networksurvey.scout.link

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The phone side checks every Scout entry point makes before it touches Bluetooth: the adapter,
 * whether it is on, and the Nearby devices permissions (BLUETOOTH_CONNECT and BLUETOOTH_SCAN,
 * which only exist from Android 12; older versions grant Bluetooth at install and scan with the
 * location permission).
 */
object ScoutBluetooth {
    /** @return The phone's Bluetooth adapter, or null on a phone without one. */
    @JvmStatic
    fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    /** @return True when the phone has Bluetooth and it is on. */
    @JvmStatic
    fun isEnabled(context: Context): Boolean = try {
        adapter(context)?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }

    /** @return True when the app may connect to Bluetooth devices. */
    @JvmStatic
    fun hasConnectPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || isGranted(context, Manifest.permission.BLUETOOTH_CONNECT)

    /** @return True when the app may scan for Bluetooth devices. */
    @JvmStatic
    fun hasScanPermission(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        isGranted(context, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
