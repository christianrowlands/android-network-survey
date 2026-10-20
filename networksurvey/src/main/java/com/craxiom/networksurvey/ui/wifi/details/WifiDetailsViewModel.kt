package com.craxiom.networksurvey.ui.wifi.details

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.data.SsidExclusionManager
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.services.watchlist.WatchlistMatcher
import com.craxiom.networksurvey.ui.RssiChartClock
import com.craxiom.networksurvey.ui.RssiChartSink
import com.craxiom.networksurvey.ui.StoredRssiChart
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
import com.craxiom.networksurvey.ui.signals.ScanConditions
import com.craxiom.networksurvey.ui.signals.ScanListenerRegistration
import com.craxiom.networksurvey.ui.wifi.model.AndroidWifiEnvironment
import com.craxiom.networksurvey.ui.wifi.model.WifiEnvironment
import com.craxiom.networksurvey.ui.wifi.model.WifiScanState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/** What toggling a network's exclusion did, for the page to confirm. */
enum class ExclusionChange { ADDED, REMOVED, LIST_FULL, UNCHANGED }

/**
 * The Wi-Fi details page: the one network it shows, its signal over time, whether its SSID is left
 * out of survey data, and whether it is on the watchlist. Scoped to the page's navigation entry.
 *
 * The record listener is registered only while the page is bound to the service and the phone can
 * scan (registering starts the service's Wi-Fi scanning); each scan plots this network's signal, or
 * "not heard" when the scan missed it. Scans arrive on the survey executor and are plotted on the
 * main thread.
 */
class WifiDetailsViewModel internal constructor(
    application: Application,
    environment: WifiEnvironment,
    private val exclusions: SsidExclusionManager,
    watchlistMatches: (WifiNetwork) -> Flow<Boolean>,
    private val chartSink: RssiChartSink,
    private val mainDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application), IWifiSurveyRecordListener {

    constructor(application: Application) : this(
        application = application,
        environment = AndroidWifiEnvironment(application),
        exclusions = SsidExclusionManager(application),
        watchlistMatches = { network -> watchedIn(application, network) },
        chartSink = StoredRssiChart(),
        mainDispatcher = Dispatchers.Main,
    )

    private val mutableNetwork = MutableStateFlow<WifiNetwork?>(null)

    /** The network this page shows, once the page has said which. */
    val network: StateFlow<WifiNetwork?> = mutableNetwork.asStateFlow()

    private val conditions = ScanConditions(
        context = application,
        actions = WifiEnvironment.ACTIONS,
        read = environment::scanState,
        onBroadcast = ::applyScanState,
    )

    /** What stops the phone from scanning, if anything. */
    val scanState: StateFlow<WifiScanState> = conditions.state

    private val registration = ScanListenerRegistration(
        register = { it.registerWifiSurveyRecordListener(this) },
        unregister = { it.unregisterWifiSurveyRecordListener(this) },
    )

    private val mutableExcluded = MutableStateFlow(false)

    /** Whether this network's SSID is on the exclusion list (left out of survey data). */
    val excluded: StateFlow<Boolean> = mutableExcluded.asStateFlow()

    /** Whether a watchlist entry matches this network; disabled entries count too. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val watched: StateFlow<Boolean> = mutableNetwork.filterNotNull()
        .flatMapLatest(watchlistMatches)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** The chart to draw (null in tests). */
    val chart = chartSink.chart

    private val chartClock = RssiChartClock(chartSink, canScan = conditions.state.value == WifiScanState.READY)

    /**
     * The latest reading for the header: [UNKNOWN_RSSI] when the last scans missed it, or while the
     * phone cannot scan (the last reading is no longer live then).
     */
    val rssi: StateFlow<Float> = combine(chartSink.chart?.rssiFlow ?: MutableStateFlow(UNKNOWN_RSSI), conditions.state) { rssi, state ->
        if (state == WifiScanState.READY) rssi else UNKNOWN_RSSI
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UNKNOWN_RSSI)

    /** Shows [network]; only the first call counts, so a recomposition or rotation keeps the history. */
    fun show(network: WifiNetwork) {
        if (mutableNetwork.value != null) return
        mutableNetwork.value = network
        mutableExcluded.value = exclusions.isExcluded(network.ssid)
        chartSink.start(network.signalStrength ?: UNKNOWN_RSSI)
    }

    /** Called with the bound service, and with null before the page unbinds. */
    fun onServiceChanged(service: NetworkSurveyService?) =
        registration.onServiceChanged(service, conditions.state.value == WifiScanState.READY)

    /** Re-reads whether the phone can scan (on resume and after a permission answer). */
    fun refresh() {
        conditions.refresh()
        applyScanState()
    }

    /** Stops the chart's clock while the page is away. */
    fun setChartPaused(paused: Boolean) = chartClock.setPageShowing(!paused)

    /** Adds this network's SSID to the exclusion list, or takes it off. */
    fun toggleExcluded(): ExclusionChange {
        val ssid = mutableNetwork.value?.ssid ?: return ExclusionChange.UNCHANGED
        val change = when {
            mutableExcluded.value -> if (exclusions.removeExcludedSsid(ssid)) ExclusionChange.REMOVED else ExclusionChange.UNCHANGED
            exclusions.addExcludedSsid(ssid) -> ExclusionChange.ADDED
            exclusions.isAtMaxCapacity() -> ExclusionChange.LIST_FULL
            else -> ExclusionChange.UNCHANGED
        }
        // Read back rather than assume, so the page always matches the list.
        mutableExcluded.value = exclusions.isExcluded(ssid)
        return change
    }

    /** Excluded networks are still shown here, so the page keeps plotting a network the user excluded. */
    override fun wantsExcludedRecords(): Boolean = true

    override fun onWifiBeaconSurveyRecords(wifiBeaconRecords: List<WifiRecordWrapper>) {
        val bssid = mutableNetwork.value?.bssid ?: return
        val data = wifiBeaconRecords.firstOrNull { it.wifiBeaconRecord.data.bssid == bssid }?.wifiBeaconRecord?.data
        val rssi = if (data != null && data.hasSignalStrength()) data.signalStrength.value else UNKNOWN_RSSI
        viewModelScope.launch(mainDispatcher) {
            // A scan in flight when the page unregistered is not news.
            if (registration.isRegistered) chartSink.plot(rssi)
        }
    }

    override fun onCleared() {
        registration.release()
        conditions.close()
        chartSink.clear()
    }

    private fun applyScanState() {
        val ready = conditions.state.value == WifiScanState.READY
        registration.update(ready)
        chartClock.setCanScan(ready)
    }

    private companion object {
        /** Whether any watchlist entry, enabled or not, matches [network]. */
        fun watchedIn(application: Application, network: WifiNetwork): Flow<Boolean> =
            SurveyDatabase.getInstance(application).watchlistDao().observeAll().map { entries ->
                entries.any { WatchlistMatcher.matches(it, network.ssid, network.bssid) }
            }
    }
}
