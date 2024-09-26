package com.craxiom.networksurvey.ui.gnss.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.GnssAntennaInfo
import android.location.LocationManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.util.CarrierFreqUtils
import com.craxiom.networksurvey.util.NsUtils
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
 * Wraps the GnssAntennaInfo updates in callbackFlow
 *
 * Derived in part from https://github.com/android/location-samples/blob/main/LocationUpdatesBackgroundKotlin/app/src/main/java/com/google/android/gms/location/sample/locationupdatesbackgroundkotlin/data/MyLocationManager.kt
 * and https://github.com/googlecodelabs/kotlin-coroutines/blob/master/ktx-library-codelab/step-06/myktxlibrary/src/main/java/com/example/android/myktxlibrary/LocationUtils.kt
 */
class SharedAntennaManager(
    private val context: Context,
    externalScope: CoroutineScope,
    prefs: SharedPreferences
) {
    @RequiresApi(Build.VERSION_CODES.R)
    @ExperimentalCoroutinesApi
    @SuppressLint("MissingPermission")
    private val _antennaUpdates = callbackFlow {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val callback = GnssAntennaInfo.Listener { list: List<GnssAntennaInfo> ->
            // Capture capabilities in preferences
            PreferenceUtils.saveInt(
                prefs,
                context.getString(R.string.capability_key_num_antenna),
                list.size
            )
            val cfs: MutableList<String> = ArrayList(2)
            for (info in list) {
                cfs.add(CarrierFreqUtils.getCarrierFrequencyLabel(info))
            }
            if (cfs.isNotEmpty()) {
                cfs.sort()
                PreferenceUtils.saveString(
                    prefs,
                    context.getString(R.string.capability_key_antenna_cf),
                    NsUtils.trimEnds(cfs.toString())
                )
            }

            //Log.d(TAG, "New antennas: $list")
            // Send the new antennas to the Flow observers
            trySend(list)
        }

        if (!context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            !context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) close()

        Timber.d("Starting antenna updates")

        try {
            locationManager.registerAntennaInfoListener(context.mainExecutor, callback)
        } catch (e: Exception) {
            Timber.e(e, "Exception in location flow")
            close(e) // in case of exception, close the Flow
        }

        awaitClose {
            Timber.d("Stopping antenna updates")
            locationManager.unregisterAntennaInfoListener(callback) // clean up when Flow collection ends
        }
    }.shareIn(
        externalScope,
        replay = 0,
        started = SharingStarted.WhileSubscribed()
    )

    @RequiresApi(Build.VERSION_CODES.R)
    @ExperimentalCoroutinesApi
    fun antennaFlow(): Flow<List<GnssAntennaInfo>> {
        return _antennaUpdates
    }
}