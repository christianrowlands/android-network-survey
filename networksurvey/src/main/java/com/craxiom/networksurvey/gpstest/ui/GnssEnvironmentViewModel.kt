package com.craxiom.networksurvey.gpstest.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Activity-scoped state of what stops the GNSS sub-tab of Signals: a missing permission or location
 * off. Re-read on every resume, after a permission answer and whenever location turns on or off.
 * The Signals strip and the GNSS view both read it.
 */
class GnssEnvironmentViewModel internal constructor(
    application: Application,
    private val environment: GnssEnvironment,
) : AndroidViewModel(application) {

    /** For the default view model factory. */
    constructor(application: Application) : this(application, AndroidGnssEnvironment(application))

    private val mutableScanState = MutableStateFlow(environment.scanState())

    /** The first thing that stops the GNSS view, or [GnssScanState.READY]. */
    val scanState: StateFlow<GnssScanState> = mutableScanState.asStateFlow()

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    init {
        ContextCompat.registerReceiver(
            application,
            locationReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Re-reads the permission and location state. */
    fun refresh() {
        mutableScanState.value = environment.scanState()
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(locationReceiver)
    }
}
