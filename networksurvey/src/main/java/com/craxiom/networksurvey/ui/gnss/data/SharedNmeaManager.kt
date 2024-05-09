package com.craxiom.networksurvey.ui.gnss.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.gnss.model.NmeaWithTime
import com.craxiom.networksurvey.util.PreferenceUtils
import com.craxiom.networksurvey.util.hasPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import timber.log.Timber

/**
 * Wraps NMEA updates in callbackFlow
 *
 * Derived in part from https://github.com/android/location-samples/blob/main/LocationUpdatesBackgroundKotlin/app/src/main/java/com/google/android/gms/location/sample/locationupdatesbackgroundkotlin/data/MyLocationManager.kt
 * and https://github.com/googlecodelabs/kotlin-coroutines/blob/master/ktx-library-codelab/step-06/myktxlibrary/src/main/java/com/example/android/myktxlibrary/LocationUtils.kt
 */
class SharedNmeaManager(
    private val context: Context,
    externalScope: CoroutineScope,
    prefs: SharedPreferences
) {
    @ExperimentalCoroutinesApi
    @SuppressLint("MissingPermission")
    private val _nmeaUpdates = callbackFlow {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val callback = OnNmeaMessageListener { message: String, timestamp: Long ->
            PreferenceUtils.saveInt(
                prefs,
                context.getString(R.string.capability_key_nmea),
                PreferenceUtils.CAPABILITY_SUPPORTED
            )
            val nmeaWithTime = NmeaWithTime(timestamp, message)
            //Log.d(TAG, "New nmea: ${nmeaWithTime}")
            // Send the new NMEA info to the Flow observers
            trySend(nmeaWithTime)
        }

        if (!context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            !context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) close()

        Timber.d("Starting NMEA updates")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.addNmeaListener(ContextCompat.getMainExecutor(context), callback)
            } else {
                locationManager.addNmeaListener(
                    callback,
                    android.os.Handler(Looper.getMainLooper())
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception in location flow")
            close(e) // in case of exception, close the Flow
        }

        awaitClose {
            Timber.d("Stopping NMEA updates")
            locationManager.removeNmeaListener(callback) // clean up when Flow collection ends
        }
    }.shareIn(
        externalScope,
        replay = 0,
        started = SharingStarted.WhileSubscribed()
    )

    @ExperimentalCoroutinesApi
    fun nmeaFlow(): Flow<NmeaWithTime> {
        return _nmeaUpdates
    }
}