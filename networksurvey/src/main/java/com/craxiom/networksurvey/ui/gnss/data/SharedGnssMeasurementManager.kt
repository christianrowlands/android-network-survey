package com.craxiom.networksurvey.ui.gnss.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.GnssMeasurementRequest
import android.location.GnssMeasurementsEvent
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.util.PreferenceUtils
import com.craxiom.networksurvey.util.SatelliteUtils
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

/**
 * Wraps the GnssMeasurement updates in callbackFlow
 *
 * Derived in part from https://github.com/android/location-samples/blob/main/LocationUpdatesBackgroundKotlin/app/src/main/java/com/google/android/gms/location/sample/locationupdatesbackgroundkotlin/data/MyLocationManager.kt
 * and https://github.com/googlecodelabs/kotlin-coroutines/blob/master/ktx-library-codelab/step-06/myktxlibrary/src/main/java/com/example/android/myktxlibrary/LocationUtils.kt
 */
class SharedGnssMeasurementManager(
    private var prefs: SharedPreferences,
    private val context: Context,
    externalScope: CoroutineScope
) {
    private val _receivingMeasurementUpdates: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    val receivingMeasurementUpdates: StateFlow<Boolean>
        get() = _receivingMeasurementUpdates

    @ExperimentalCoroutinesApi
    @SuppressLint("MissingPermission")
    private val _measurementUpdates = callbackFlow {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // Check explicit support on Android S and higher here - Android R and lower are checked in status callbacks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkMeasurementSupport(context, locationManager, prefs)
        }
        val callback: GnssMeasurementsEvent.Callback =
            object : GnssMeasurementsEvent.Callback() {
                override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                    saveMeasurementCapabilities(context, event, prefs)

                    //Log.d(TAG, "New measurement: $event")
                    // Send the new measurement to the Flow observers
                    trySend(event)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(status: Int) {
                    // These status messages are deprecated on Android S and higher and should not be used
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        return
                    }
                    handleLegacyMeasurementStatus(context, status, prefs)
                }
            }

        if (!context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            !context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) close()

        Timber.d("Starting measurement updates")
        _receivingMeasurementUpdates.value = true

        try {
            if (SatelliteUtils.isForceFullGnssMeasurementsSupported()) {
                val forceFullMeasurements = prefs
                    .getBoolean(
                        context.getString(R.string.pref_key_force_full_gnss_measurements),
                        true
                    )
                Timber.d("Force full GNSS measurements = %s", forceFullMeasurements)
                // Request "force full GNSS measurements" explicitly (on <= Android R this is a manual developer setting)
                val request = GnssMeasurementRequest.Builder()
                    .setFullTracking(forceFullMeasurements)
                    .build()
                locationManager.registerGnssMeasurementsCallback(
                    request,
                    ContextCompat.getMainExecutor(context),
                    callback
                )
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    locationManager.registerGnssMeasurementsCallback(
                        ContextCompat.getMainExecutor(
                            context
                        ), callback
                    )
                } else {
                    locationManager.registerGnssMeasurementsCallback(
                        callback,
                        Handler(Looper.getMainLooper())
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception in location flow")
            close(e) // in case of exception, close the Flow
        }

        awaitClose {
            Timber.d("Stopping measurement updates")
            _receivingMeasurementUpdates.value = false
            locationManager.unregisterGnssMeasurementsCallback(callback) // clean up when Flow collection ends
        }
    }.shareIn(
        externalScope,
        replay = 0,
        started = SharingStarted.WhileSubscribed()
    )

    @ExperimentalCoroutinesApi
    fun measurementFlow(): Flow<GnssMeasurementsEvent> {
        return _measurementUpdates
    }
}

private fun handleLegacyMeasurementStatus(context: Context, status: Int, prefs: SharedPreferences) {
    // TODO - surface this state message in UI somewhere, like when user returned from Settings like before? For now just disable logging option in Settings, will surface in Dashboard later
    val uiStatusMessage: String
    when (status) {
        GnssMeasurementsEvent.Callback.STATUS_LOCATION_DISABLED -> {
            uiStatusMessage =
                context.getString(R.string.gnss_measurement_status_loc_disabled)
            PreferenceUtils.saveInt(
                prefs,
                context.getString(R.string.capability_key_raw_measurements),
                PreferenceUtils.CAPABILITY_LOCATION_DISABLED
            )
        }

        GnssMeasurementsEvent.Callback.STATUS_NOT_SUPPORTED -> {
            uiStatusMessage =
                context.getString(R.string.gnss_measurement_status_not_supported)
            PreferenceUtils.saveInt(
                prefs,
                context.getString(R.string.capability_key_raw_measurements),
                PreferenceUtils.CAPABILITY_NOT_SUPPORTED
            )
            PreferenceUtils.saveInt(
                prefs,
                context.getString(R.string.capability_key_measurement_automatic_gain_control),
                PreferenceUtils.CAPABILITY_NOT_SUPPORTED
            )
            PreferenceUtils.saveInt(
                prefs,
                context.getString(R.string.capability_key_measurement_delta_range),
                PreferenceUtils.CAPABILITY_NOT_SUPPORTED
            )
        }

        GnssMeasurementsEvent.Callback.STATUS_READY -> {
            uiStatusMessage = context.getString(R.string.gnss_measurement_status_ready)
            PreferenceUtils.saveInt(
                prefs,
                context.getString(R.string.capability_key_raw_measurements),
                PreferenceUtils.CAPABILITY_SUPPORTED
            )
        }

        else -> {
            uiStatusMessage = context.getString(R.string.gnss_status_unknown)
            PreferenceUtils.saveInt(
                prefs,
                context.getString(R.string.capability_key_raw_measurements),
                PreferenceUtils.CAPABILITY_UNKNOWN
            )
        }
    }
}

@RequiresApi(api = Build.VERSION_CODES.S)
private fun checkMeasurementSupport(
    context: Context,
    lm: LocationManager,
    prefs: SharedPreferences
) {
    // TODO - surface this state message in UI somewhere, like when user returned from Settings like before?  For now just disable logging option in Settings, will surface in Dashboard later
    val uiStatusMessage: String = if (SatelliteUtils.isMeasurementsSupported(lm)) {
        PreferenceUtils.saveInt(
            prefs,
            context.getString(R.string.capability_key_raw_measurements),
            PreferenceUtils.CAPABILITY_SUPPORTED
        )
        context.getString(R.string.gnss_measurement_status_ready)
    } else {
        PreferenceUtils.saveInt(
            prefs,
            context.getString(R.string.capability_key_raw_measurements),
            PreferenceUtils.CAPABILITY_NOT_SUPPORTED,
        )
        context.getString(R.string.gnss_measurement_status_not_supported)
    }
}

/**
 * Saves device capabilities for GNSS measurements and related information from the given [event]
 */
fun saveMeasurementCapabilities(
    context: Context,
    event: GnssMeasurementsEvent,
    prefs: SharedPreferences
) {
    var agcSupport = PreferenceUtils.CAPABILITY_UNKNOWN
    var carrierPhaseSupport = PreferenceUtils.CAPABILITY_UNKNOWN
    // Loop through all measurements - if at least one supports, then mark as supported
    for (measurement in event.measurements) {
        if (SatelliteUtils.isAutomaticGainControlSupported(measurement)) {
            agcSupport = PreferenceUtils.CAPABILITY_SUPPORTED
        } else if (agcSupport == PreferenceUtils.CAPABILITY_UNKNOWN) {
            agcSupport = PreferenceUtils.CAPABILITY_NOT_SUPPORTED
        }
        if (SatelliteUtils.isCarrierPhaseSupported(measurement)) {
            carrierPhaseSupport = PreferenceUtils.CAPABILITY_SUPPORTED
        } else if (carrierPhaseSupport == PreferenceUtils.CAPABILITY_UNKNOWN) {
            carrierPhaseSupport = PreferenceUtils.CAPABILITY_NOT_SUPPORTED
        }
    }
    PreferenceUtils.saveInt(
        prefs,
        context.getString(R.string.capability_key_measurement_automatic_gain_control),
        agcSupport
    )
    PreferenceUtils.saveInt(
        prefs,
        context.getString(R.string.capability_key_measurement_delta_range),
        carrierPhaseSupport
    )
}