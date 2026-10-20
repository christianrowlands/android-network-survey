package com.craxiom.networksurvey.ui.cellular.details

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.craxiom.networksurvey.ui.cellular.details.model.CellularScanState

/** Reads whether the phone can report cells. An interface so tests can fake it. */
fun interface CellularEnvironment {
    /** @return The first thing that stops the Cellular view, or [CellularScanState.READY]. */
    fun scanState(): CellularScanState

    companion object {
        /**
         * What reading cells needs: fine location (Android withholds cell details without it) and
         * phone state (the SIMs and the technology). Coarse location is asked with fine, since
         * Android 12 and later ignore a request for fine location alone.
         */
        val PERMISSIONS: Array<String> = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
        )
    }
}

/** The [CellularEnvironment] of this phone. */
class AndroidCellularEnvironment(private val context: Context) : CellularEnvironment {
    override fun scanState(): CellularScanState {
        val missing = CellularEnvironment.PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) return CellularScanState.PERMISSION_NEEDED
        // Android gives apps no cell details while location is off.
        val locationManager = context.getSystemService(LocationManager::class.java)
        if (locationManager != null && !LocationManagerCompat.isLocationEnabled(locationManager)) {
            return CellularScanState.LOCATION_OFF
        }
        val airplaneMode = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        return if (airplaneMode) CellularScanState.AIRPLANE_MODE else CellularScanState.READY
    }
}
