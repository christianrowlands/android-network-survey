package com.craxiom.networksurvey.gpstest.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** What stops the GNSS view from showing satellites, in the order it is checked. */
enum class GnssScanState {
    /** Precise location is not allowed. */
    PERMISSION_NEEDED,

    /** Location is off; the GNSS receiver reports nothing to apps then. */
    LOCATION_OFF,

    /** Nothing is in the way. */
    READY,
}

/** Reads whether the phone can report satellites. An interface so tests can fake it. */
fun interface GnssEnvironment {
    /** @return The first thing that stops the GNSS view, or [GnssScanState.READY]. */
    fun scanState(): GnssScanState

    companion object {
        /**
         * What reading satellites needs: fine location. Coarse is asked with it, since Android 12
         * and later ignore a request for fine location alone.
         */
        val PERMISSIONS: Array<String> = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}

/** The [GnssEnvironment] of this phone. */
class AndroidGnssEnvironment(private val context: Context) : GnssEnvironment {
    override fun scanState(): GnssScanState {
        val missing = GnssEnvironment.PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) return GnssScanState.PERMISSION_NEEDED
        val locationManager = context.getSystemService(LocationManager::class.java)
        if (locationManager != null && !LocationManagerCompat.isLocationEnabled(locationManager)) {
            return GnssScanState.LOCATION_OFF
        }
        return GnssScanState.READY
    }
}
