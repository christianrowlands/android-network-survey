/*
 * Copyright (C) 2019 Sean J. Barbeau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.craxiom.networksurvey.gpstest.ui.gnss.model

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.location.GnssMeasurementsEvent
import android.location.Location
import android.os.Build
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.model.DilutionOfPrecision
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.gpstest.ui.gnss.data.FirstFixState
import com.craxiom.networksurvey.gpstest.ui.gnss.data.FixState
import com.craxiom.networksurvey.gpstest.ui.gnss.data.LocationRepository
import com.craxiom.networksurvey.gpstest.ui.status.GnssTrendHistory
import com.craxiom.networksurvey.gpstest.ui.status.GnssTrends
import com.craxiom.networksurvey.gpstest.util.CarrierFreqUtils
import com.craxiom.networksurvey.gpstest.util.CarrierFreqUtils.getCarrierFrequencyLabel
import com.craxiom.networksurvey.gpstest.util.GpsTestUtil
import com.craxiom.networksurvey.gpstest.util.SignalKeys
import com.craxiom.networksurvey.gpstest.util.MathUtils
import com.craxiom.networksurvey.util.NmeaUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import com.craxiom.networksurvey.gpstest.util.SatelliteUtil.toSatelliteMetadata
import com.craxiom.networksurvey.gpstest.util.SatelliteUtil.toSatelliteStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.concurrent.TimeUnit

/**
 * The age at which cached AGC values are considered stale and no longer applied to the status rows.
 */
private const val AGC_TIMEOUT_MS = 4_000L

/**
 * View model that holds GNSS signal information
 */
@ExperimentalCoroutinesApi
@HiltViewModel
class SignalInfoViewModel @Inject constructor(
    context: Context,
    application: Application,
    private val repository: LocationRepository,
    prefs: SharedPreferences
) : AndroidViewModel(application) {
    //
    // Flows from the repository
    //
    private var locationFlow: Job? = null
    private var gnssFlow: Job? = null
    private var nmeaFlow: Job? = null
    private var measurementFlow: Job? = null

    // AGC values from the most recent GnssMeasurementsEvent, joined onto the SatelliteStatus rows
    // in updateStatus(). Only accessed on the main thread via viewModelScope.
    private val signalAgcs = mutableMapOf<String, Float>()
    private val bandAgcs = mutableMapOf<String, Float>()
    private var lastMeasurementTimeMs = 0L

    //
    // LiveData observed by Composables
    //

    // All statuses BEFORE filtering
    private val _allStatuses = MutableLiveData<List<SatelliteStatus>>()
    val allStatuses: LiveData<List<SatelliteStatus>> = _allStatuses

    // Statuses AFTER filtering
    private val _filteredStatuses = MutableLiveData<List<SatelliteStatus>>()
    val filteredStatuses: LiveData<List<SatelliteStatus>> = _filteredStatuses

    // GNSS Statuses AFTER applying filter
    private val _filteredGnssStatuses = MutableLiveData<List<SatelliteStatus>>()
    val filteredGnssStatuses: LiveData<List<SatelliteStatus>> = _filteredGnssStatuses

    // SBAS Statuses AFTER applying filter
    private val _filteredSbasStatuses = MutableLiveData<List<SatelliteStatus>>()
    val filteredSbasStatuses: LiveData<List<SatelliteStatus>> = _filteredSbasStatuses

    // Satellite metadata AFTER applying filter
    private val _filteredSatelliteMetadata = MutableLiveData<SatelliteMetadata>()
    val filteredSatelliteMetadata: LiveData<SatelliteMetadata> = _filteredSatelliteMetadata

    private val _location = MutableLiveData<Location>()
    val location: LiveData<Location> = _location

    private val _ttff = MutableLiveData<Long?>(null)
    val ttff: LiveData<Long?> = _ttff

    private val _altitudeMsl = MutableLiveData<Double>()
    val altitudeMsl: LiveData<Double> = _altitudeMsl

    private val _dop = MutableLiveData<DilutionOfPrecision>()
    val dop: LiveData<DilutionOfPrecision> = _dop

    private val _fixState = MutableLiveData<FixState>(FixState.NotAcquired)
    val fixState: LiveData<FixState> = _fixState

    // The last two minutes of AGC and C/N0, for the Status page's trend lines
    private val trendHistory = GnssTrendHistory()
    private val _trends = MutableLiveData(GnssTrends.EMPTY)
    val trends: LiveData<GnssTrends> = _trends

    // The satellite sort, an index into the sort_sats array
    private val _sortIndex = MutableLiveData(PreferenceUtils.getSatSortIndex(prefs))
    val sortIndex: LiveData<Int> = _sortIndex

    private var started = false

    init {
        viewModelScope.launch {
            observeLocationUpdateStates(context, prefs)
            observeGnssStates(prefs)
        }
    }

    @ExperimentalCoroutinesApi
    private fun observeLocationUpdateStates(context: Context, prefs: SharedPreferences) {
        repository.receivingLocationUpdates
            .onEach {
                setStarted(context, it, prefs)
            }
            .launchIn(viewModelScope)
    }

    @ExperimentalCoroutinesApi
    private fun observeLocationFlow() {
        if (locationFlow?.isActive == true) {
            // If we're already observing updates, don't register again
            return
        }
        // Observe locations via Flow as they are generated by the repository
        locationFlow = repository.getLocations()
            .onEach {
                //Log.d(TAG, "SignalInfoViewModel location: ${it.toNotificationTitle()}")
                _location.value = it
            }
            .launchIn(viewModelScope)
    }

    @ExperimentalCoroutinesApi
    private fun observeGnssFlow(context: Context, prefs: SharedPreferences) {
        if (gnssFlow?.isActive == true) {
            // If we're already observing updates, don't register again
            return
        }
        // Observe locations via Flow as they are generated by the repository
        gnssFlow = repository.getGnssStatus()
            .map { it.toSatelliteStatus() }
            .onEach {
                //Log.d(TAG, "SignalInfoViewModel gnssStatus: ${it}")
                updateStatus(context, it, prefs)
            }
            .launchIn(viewModelScope)
    }

    @ExperimentalCoroutinesApi
    private fun observeMeasurementFlow() {
        if (measurementFlow?.isActive == true) {
            // If we're already observing updates, don't register again
            return
        }
        // Observe GNSS measurements via Flow as they are generated by the repository so the AGC
        // values can be shown alongside the C/N0 values in the status rows
        measurementFlow = repository.getMeasurements()
            .onEach {
                updateAgc(it)
            }
            .launchIn(viewModelScope)
    }

    /**
     * Caches the AGC values from the provided measurement [event] so they can be joined onto the
     * satellite status rows. The per-signal AGC from GnssMeasurement is preferred, with the
     * band-level values from GnssAutomaticGainControl (Android 13+) as a fallback, mirroring the
     * precedence used by SurveyRecordProcessor for the GNSS survey records.
     */
    private fun updateAgc(event: GnssMeasurementsEvent) {
        signalAgcs.clear()
        bandAgcs.clear()
        for (measurement in event.measurements) {
            if (!measurement.hasAutomaticGainControlLevelDb()) continue
            val gnssType = GpsTestUtil.getGnssConstellationType(measurement.constellationType)
            val label = if (measurement.hasCarrierFrequencyHz()) {
                getCarrierFrequencyLabel(
                    gnssType, measurement.svid, MathUtils.toMhz(measurement.carrierFrequencyHz)
                )
            } else {
                CarrierFreqUtils.CF_UNSUPPORTED
            }
            signalAgcs[signalAgcKey(measurement.svid, gnssType, label)] =
                measurement.automaticGainControlLevelDb.toFloat()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            for (agc in event.gnssAutomaticGainControls) {
                val gnssType = GpsTestUtil.getGnssConstellationType(agc.constellationType)
                val label = getCarrierFrequencyLabel(
                    gnssType, -1, MathUtils.toMhz(agc.carrierFrequencyHz.toFloat())
                )
                bandAgcs[bandAgcKey(gnssType, label)] = agc.levelDb.toFloat()
            }
        }
        lastMeasurementTimeMs = SystemClock.elapsedRealtime()
    }

    /**
     * Joins the cached AGC values onto the provided [status] rows. AGC values older than
     * [AGC_TIMEOUT_MS] are considered stale and are not applied.
     */
    private fun applyAgc(status: List<SatelliteStatus>) {
        val stale = SystemClock.elapsedRealtime() - lastMeasurementTimeMs > AGC_TIMEOUT_MS
        if (stale) return
        for (satelliteStatus in status) {
            val label = getCarrierFrequencyLabel(satelliteStatus)
            val agc =
                signalAgcs[signalAgcKey(satelliteStatus.svid, satelliteStatus.gnssType, label)]
                    ?: bandAgcs[bandAgcKey(satelliteStatus.gnssType, label)]
            if (agc != null) {
                satelliteStatus.hasAgc = true
                satelliteStatus.agcDb = agc
            }
        }
    }

    private fun signalAgcKey(svid: Int, gnssType: GnssType, carrierLabel: String) =
        "$svid $gnssType $carrierLabel"

    private fun bandAgcKey(gnssType: GnssType, carrierLabel: String) = "$gnssType $carrierLabel"

    private fun observeGnssStates(prefs: SharedPreferences) {
        repository.firstFixState
            .onEach {
                when (it) {
                    is FirstFixState.Acquired -> {
                        onGnssFirstFix(it.ttffMillis)
                    }

                    is FirstFixState.NotAcquired -> onGnssFixLost()
                }
            }
            .launchIn(viewModelScope)
        repository.fixState
            .onEach {
                when (it) {
                    is FixState.Acquired -> onGnssFixAcquired()
                    is FixState.NotAcquired -> onGnssFixLost()
                }
            }
            .launchIn(viewModelScope)
    }

    @ExperimentalCoroutinesApi
    private fun observeNmeaFlow() {
        if (nmeaFlow?.isActive == true) {
            // If we're already observing updates, don't register again
            return
        }
        // Observe locations via Flow as they are generated by the repository
        nmeaFlow = repository.getNmea()
            .onEach {
                //Log.d(TAG, "SignalInfoViewModel NMEA: ${it}")
                onNmeaMessage(it.message, it.timestamp)
            }
            .launchIn(viewModelScope)
    }

    @ExperimentalCoroutinesApi
    @VisibleForTesting
    fun updateStatus(context: Context, status: List<SatelliteStatus>, prefs: SharedPreferences) {
        SignalKeys.assign(status)
        applyAgc(status)
        val now = SystemClock.elapsedRealtime()
        trendHistory.record(now, status)
        _trends.value = trendHistory.snapshot(now)
        publishStatus(context, status, prefs)
    }

    /**
     * Sorts the satellites by the [index] of the sort_sats array, saves it, and re-sorts the
     * satellites already shown straight away instead of at the next GnssStatus.
     */
    fun setSort(context: Context, index: Int, prefs: SharedPreferences) {
        PreferenceUtils.setSatSortIndex(prefs, index)
        _sortIndex.value = index
        publishStatus(context, _allStatuses.value.orEmpty(), prefs)
    }

    /** Filters and sorts [status] by the user's choices and publishes the lists the pages show. */
    private fun publishStatus(context: Context, status: List<SatelliteStatus>, prefs: SharedPreferences) {
        _allStatuses.value = status

        // Get filter set by user in UI
        val filter = PreferenceUtils.gnssFilter(context, prefs)

        // Split list into GNSS and SBAS statuses, apply "shown" filter, and update view model
        val (gnssStatus, sbasStatus) = status
            .filter {
                filter.isEmpty() || filter.contains(it.gnssType)
            }
            .partition {
                it.gnssType != GnssType.SBAS
            }

        _filteredStatuses.value = gnssStatus + sbasStatus
        setFilteredAndSortedStatuses(
            sort(context, gnssStatus, true, prefs),
            sort(context, sbasStatus, false, prefs)
        )
    }

    /**
     * Returns a sorted version of the provided [status] list according to the sort preference of
     * the user, with [isGnss] set to true if the list contains all GNSS signals and false if
     * it contains all SBAS signals
     */
    private fun sort(
        context: Context,
        status: List<SatelliteStatus>,
        isGnss: Boolean,
        prefs: SharedPreferences
    ): List<SatelliteStatus> {
        return when (PreferenceUtils.getSatSortIndex(prefs)) {
            0 -> {
                // Sort by Constellation
                if (isGnss) {
                    status.sortedWith(compareBy(SatelliteStatus::gnssType, SatelliteStatus::svid))
                } else {
                    status.sortedWith(compareBy(SatelliteStatus::sbasType, SatelliteStatus::svid))
                }
            }

            1 -> {
                // Sort by Carrier Frequency (raw CF, then label to group L5s, E5a, etc.)
                status.sortedWith(
                    compareBy<SatelliteStatus> {
                        it.carrierFrequencyHz
                    }.thenBy {
                        getCarrierFrequencyLabel(it)
                    }.thenBy {
                        it.svid
                    }
                )
            }

            2 -> {
                // Sort by Signal Strength
                status.sortedWith(compareByDescending(SatelliteStatus::cn0DbHz))
            }

            3 -> {
                // Sort by Used in Fix
                status.sortedWith(
                    compareByDescending(SatelliteStatus::usedInFix).thenComparing(
                        SatelliteStatus::svid
                    )
                )
            }

            4 -> {
                // Sort by Constellation, Carrier Frequency
                if (isGnss) {
                    status.sortedWith(
                        compareBy(
                            SatelliteStatus::gnssType,
                            SatelliteStatus::carrierFrequencyHz,
                            SatelliteStatus::svid
                        )
                    )
                } else {
                    status.sortedWith(
                        compareBy(
                            SatelliteStatus::sbasType,
                            SatelliteStatus::carrierFrequencyHz,
                            SatelliteStatus::svid
                        )
                    )
                }
            }

            5 -> {
                // Sort by Constellation, Signal Strength
                if (isGnss) {
                    status.sortedWith(
                        compareBy(SatelliteStatus::gnssType).thenByDescending(
                            SatelliteStatus::cn0DbHz
                        )
                    )
                } else {
                    status.sortedWith(
                        compareBy(SatelliteStatus::sbasType).thenByDescending(
                            SatelliteStatus::cn0DbHz
                        )
                    )
                }
            }

            6 -> {
                // Sort by Constellation, Used in Fix
                if (isGnss) {
                    status.sortedWith(
                        compareBy(SatelliteStatus::gnssType).thenByDescending(
                            SatelliteStatus::usedInFix
                        ).thenComparing(SatelliteStatus::svid)
                    )
                } else {
                    status.sortedWith(
                        compareBy(SatelliteStatus::sbasType).thenByDescending(
                            SatelliteStatus::usedInFix
                        ).thenComparing(SatelliteStatus::svid)
                    )
                }
            }

            7 -> {
                // Sort by AGC, with signals that have no AGC value at the bottom
                status.sortedWith(
                    compareByDescending<SatelliteStatus> {
                        if (it.hasAgc) it.agcDb else Float.NEGATIVE_INFINITY
                    }.thenComparing(SatelliteStatus::svid)
                )
            }

            else -> status
        }
    }


    /**
     * Adds a new set of GNSS and SBAS status objects (signals) so they can be analyzed and grouped
     * into satellites. Filter and sorting should have been applied before calling this method so
     * only signals and satellites that will be shown to the user are included.
     *
     * @param gnssStatuses a new set of GNSS status objects (signals)
     * @param sbasStatuses a new set of SBAS status objects (signals)
     */
    private fun setFilteredAndSortedStatuses(
        gnssStatuses: List<SatelliteStatus>,
        sbasStatuses: List<SatelliteStatus>
    ) {
        this._filteredGnssStatuses.value = gnssStatuses
        this._filteredSbasStatuses.value = sbasStatuses

        val gnss = gnssStatuses.toSatelliteMetadata()
        val sbas = sbasStatuses.toSatelliteMetadata()

        _filteredSatelliteMetadata.value = SatelliteMetadata(
            gnss.numSignalsInView + sbas.numSignalsInView,
            gnss.numSignalsUsed + sbas.numSignalsUsed,
            gnss.numSignalsTotal + sbas.numSignalsTotal,
            gnss.numSatsInView + sbas.numSatsInView,
            gnss.numSatsUsed + sbas.numSatsUsed,
            gnss.numSatsTotal + sbas.numSatsTotal,
            gnss.supportedGnss,
            gnss.supportedGnssCfs,
            sbas.supportedSbas,
            sbas.supportedSbasCfs,
            gnss.unknownCarrierStatuses + sbas.unknownCarrierStatuses,
            gnss.duplicateCarrierStatuses + sbas.duplicateCarrierStatuses,
            gnss.isDualFrequencyPerSatInView or sbas.isDualFrequencyPerSatInView,
            gnss.isDualFrequencyPerSatInUse or sbas.isDualFrequencyPerSatInUse,
            gnss.isNonPrimaryCarrierFreqInView or sbas.isNonPrimaryCarrierFreqInView,
            gnss.isNonPrimaryCarrierFreqInUse or sbas.isNonPrimaryCarrierFreqInUse
        )
    }

    private fun onGnssFirstFix(ttffMillis: Int) {
        _ttff.value = if (ttffMillis == 0) null else TimeUnit.MILLISECONDS.toSeconds(ttffMillis.toLong())
    }

    private fun onGnssFixAcquired() {
        _fixState.value = FixState.Acquired
    }

    private fun onGnssFixLost() {
        _fixState.value = FixState.NotAcquired
    }

    private fun onNmeaMessage(message: String, timestamp: Long) {
        if (message.startsWith("\$GPGGA") || message.startsWith("\$GNGNS") || message.startsWith("\$GNGGA")) {
            val altitudeMsl = NmeaUtils.getAltitudeMeanSeaLevel(message)
            if (altitudeMsl != null && started) {
                _altitudeMsl.value = altitudeMsl
            }
        }
        if (message.startsWith("\$GNGSA") || message.startsWith("\$GPGSA")) {
            val dop = NmeaUtils.getDop(message)
            if (dop != null && started) {
                _dop.value = dop
            }
        }
    }

    @ExperimentalCoroutinesApi
    @SuppressLint("NotifyDataSetChanged")
    fun setStarted(context: Context, started: Boolean, prefs: SharedPreferences) {
        if (started == this.started) {
            // State hasn't changed - no op and return
            return
        }
        if (started) {
            // Activity or service is observing updates, so observe here too
            observeLocationFlow()
            observeGnssFlow(context, prefs)
            observeNmeaFlow()
            observeMeasurementFlow()
        } else {
            // Cancel updates (Note that these are canceled via trackingListener preference listener
            // in the case where updates are stopped from the Activity UI switch)
            cancelFlows()

            // Reset views
            reset()
        }
        this.started = started
    }

    private fun cancelFlows() {
        locationFlow?.cancel()
        gnssFlow?.cancel()
        nmeaFlow?.cancel()
        measurementFlow?.cancel()
        clearAgc()
    }

    private fun clearAgc() {
        signalAgcs.clear()
        bandAgcs.clear()
        lastMeasurementTimeMs = 0L
    }

    /**
     * The phone's heading (the first value, degrees clockwise from north; true north when the GNSS
     * setting asks for it), for the sky plot to turn with the phone. Collect it only while the plot
     * shows: the sensor runs while collected.
     */
    @ExperimentalCoroutinesApi
    fun headingUpdates(): Flow<Orientation> = repository.getSensorUpdates()

    fun reset() {
        // Cleared too, so a view started again waits for satellites instead of showing the last ones
        _allStatuses.value = emptyList()
        _filteredStatuses.value = emptyList()
        _filteredGnssStatuses.value = emptyList()
        _filteredSbasStatuses.value = emptyList()
        _location.value = Location("reset")
        _ttff.value = null
        _altitudeMsl.value = Double.NaN
        _dop.value = DilutionOfPrecision(Double.NaN, Double.NaN, Double.NaN)
        _filteredSatelliteMetadata.value = SatelliteMetadata()
        _fixState.value = FixState.NotAcquired
        clearAgc()
        trendHistory.clear()
        _trends.value = GnssTrends.EMPTY
    }

    /**
     * Called when the lifecycle of the observer is ended
     */
    override fun onCleared() {
        reset()
    }
}