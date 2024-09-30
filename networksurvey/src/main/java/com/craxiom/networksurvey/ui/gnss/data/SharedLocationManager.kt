package com.craxiom.networksurvey.ui.gnss.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.location.LocationListenerCompat
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.util.hasPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import timber.log.Timber

private const val TAG = "SharedLocationManager"

/**
 * Wraps the LocationManager in callbackFlow
 *
 * Derived in part from https://github.com/android/location-samples/blob/main/LocationUpdatesBackgroundKotlin/app/src/main/java/com/google/android/gms/location/sample/locationupdatesbackgroundkotlin/data/MyLocationManager.kt
 * and https://github.com/googlecodelabs/kotlin-coroutines/blob/master/ktx-library-codelab/step-06/myktxlibrary/src/main/java/com/example/android/myktxlibrary/LocationUtils.kt
 */
class SharedLocationManager(
    private val context: Context,
    externalScope: CoroutineScope,
    prefs: SharedPreferences
) {
    private val _receivingLocationUpdates: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    val receivingLocationUpdates: StateFlow<Boolean>
        get() = _receivingLocationUpdates

    @ExperimentalCoroutinesApi
    @SuppressLint("MissingPermission")
    private val _locationUpdates = callbackFlow {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // Use LocationListenerCompat to avoid crashes on API Level 30 and lower (#627)
        val callback = LocationListenerCompat { location ->
            //Log.d(TAG, "New location: ${location.toNotificationTitle()}")
            // Send the new location to the Flow observers
            trySend(location)
        }

        if (!context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            !context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) close()

        Log.d(
            TAG,
            "Starting location updates with minTime=${
                minTimeMillis(
                    context,
                    prefs
                )
            }ms and minDistance=${minDistance(context, prefs)}m"
        )
        _receivingLocationUpdates.value = true

        try {
            val hasGspProvider: Boolean
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hasGspProvider = locationManager.hasProvider(LocationManager.GPS_PROVIDER)
            } else {
                hasGspProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER) != null
            }

            if (hasGspProvider) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minTimeMillis(context, prefs),
                    minDistance(context, prefs),
                    callback,
                    context.mainLooper
                )
            } else {
                Timber.e("No GPS provider available")
                close()

            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in location flow: $e")
            close(e) // in case of exception, close the Flow
        }

        awaitClose {
            Log.d(TAG, "Stopping location updates")
            _receivingLocationUpdates.value = false
            locationManager.removeUpdates(callback) // clean up when Flow collection ends
        }
    }.shareIn(
        externalScope,
        replay = 0,
        started = SharingStarted.WhileSubscribed()
    )

    @ExperimentalCoroutinesApi
    fun locationFlow(): Flow<Location> {
        return _locationUpdates
    }
}

const val SECONDS_TO_MILLISECONDS = 1000

/**
 * Returns the minTime between location updates used for the LocationListener in milliseconds
 */
fun minTimeMillis(context: Context, prefs: SharedPreferences): Long {
    val minTimeDouble: Double =
        prefs.getString(context.getString(R.string.pref_key_gps_min_time), "1")
            ?.toDouble() ?: 1.0
    return (minTimeDouble * SECONDS_TO_MILLISECONDS).toLong()
}

/**
 * Returns the minDistance between location updates used for the LocationLitsener in meters
 */
fun minDistance(context: Context, prefs: SharedPreferences): Float {
    return prefs.getString(context.getString(R.string.pref_key_gps_min_distance), "0")?.toFloat()
        ?: 0.0f
}