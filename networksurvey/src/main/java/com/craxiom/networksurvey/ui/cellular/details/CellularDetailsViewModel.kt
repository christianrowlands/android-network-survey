package com.craxiom.networksurvey.ui.cellular.details

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.SimChanges
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.NetworkTechnologyInfo
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.cellular.CellularChartViewModel
import com.craxiom.networksurvey.ui.cellular.details.model.CellularDetailsUiState
import com.craxiom.networksurvey.ui.cellular.details.model.CellularPages
import com.craxiom.networksurvey.ui.cellular.details.model.CellularScanState
import com.craxiom.networksurvey.ui.cellular.details.model.CellularSimData
import com.craxiom.networksurvey.ui.cellular.details.model.CellularSimView
import com.craxiom.networksurvey.ui.cellular.details.model.SimInfo
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The Cellular view: per SIM, the technology the phone uses, its serving cell, the 5G NR secondary
 * cell and the neighbors, rebuilt from every cellular scan. Activity scoped, so the SIM tab, the
 * NR card's idle state and the charts survive sub-tab switches and rotation.
 *
 * The record listener is registered only while the screen is bound to the service
 * ([onServiceChanged]) and the phone can report cells (permissions, location on, airplane mode
 * off); registering starts the service's cellular scanning. Scans arrive on the survey executor;
 * the state is updated with pure reducers, and the charts are fed on the main thread.
 */
class CellularDetailsViewModel internal constructor(
    application: Application,
    private val environment: CellularEnvironment,
    private val simsOf: (NetworkSurveyService) -> List<SimInfo>,
    private val charts: CellularChartSink,
    private val clock: () -> Long,
    private val mainDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application), ICellularSurveyRecordListener {

    constructor(application: Application) : this(
        application = application,
        environment = AndroidCellularEnvironment(application),
        simsOf = ::activeSims,
        charts = StoredCellularCharts(),
        clock = SystemClock::elapsedRealtime,
        mainDispatcher = Dispatchers.Main,
    )

    // Held only between a bind and the unbind (onServiceChanged(null) clears it).
    @SuppressLint("StaticFieldLeak")
    private var service: NetworkSurveyService? = null

    // Read on the survey executor too, inside the state updates: a scan already in flight when the
    // view unregisters (airplane mode, location off) must not bring back what the unregistering
    // cleared.
    @Volatile
    private var registered = false

    private val sims = MutableStateFlow<Map<Int, CellularSimData>>(emptyMap())
    private val pages = MutableStateFlow(CellularPages.from(emptyList()))
    private val selectedPage = MutableStateFlow(0)
    private val scanState = MutableStateFlow(environment.scanState())
    private val bound = MutableStateFlow(false)
    private val pagesKnown = MutableStateFlow(false)
    private val mutableLatestServingCell = MutableStateFlow<ServingCellInfo?>(null)

    /** The newest serving cell of any SIM, which seeds the Map tab's first visit. */
    val latestServingCell: StateFlow<ServingCellInfo?> = mutableLatestServingCell.asStateFlow()

    val uiState: StateFlow<CellularDetailsUiState> = combine(
        combine(pages, selectedPage, pagesKnown, ::Triple),
        scanState,
        bound,
        sims,
    ) { (pages, selected, known), scan, bound, sims ->
        val index = selected.coerceIn(0, pages.lastIndex)
        val data = sims[pages[index].recordSubscriptionId] ?: CellularSimData()
        CellularDetailsUiState(
            pages = pages,
            pagesKnown = known,
            selectedPage = index,
            scanState = scan,
            bound = bound,
            sim = CellularSimView.from(data, getApplication<Application>().getString(R.string.voice_bearer_none)),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        CellularDetailsUiState(pages = pages.value, scanState = scanState.value),
    )

    private val environmentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    init {
        val filter = IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED).apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(application, environmentReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        SimChanges.changes.onEach { onSimChanged() }.launchIn(viewModelScope)
    }

    /** Called with the bound service, and with null before the screen unbinds. */
    fun onServiceChanged(newService: NetworkSurveyService?) {
        if (newService == null) unregister()
        service = newService
        newService?.let { readPages(it) }
        updateRegistration()
    }

    /**
     * Re-reads whether the phone can report cells (on resume, after a permission answer, and when
     * airplane mode or location changes), and registers or unregisters to match.
     */
    fun refresh() {
        scanState.value = environment.scanState()
        updateRegistration()
    }

    /** Shows the SIM tab at [index]. */
    fun selectPage(index: Int) {
        selectedPage.value = index
    }

    /** @return The signal chart of [subscriptionId]'s tab, or null in tests. */
    fun chartFor(subscriptionId: Int): CellularChartViewModel? = charts.chartFor(subscriptionId)

    /** Stops the charts' clock while the screen is away, and restarts it on return. */
    fun setChartsPaused(paused: Boolean) = charts.setPaused(paused)

    override fun onNetworkType(technologyInfo: NetworkTechnologyInfo, subscriptionId: Int) {
        // Checked inside the update too: an unregister that clears the cells makes this retry and drop.
        sims.update {
            if (!registered) return@update it
            it + (subscriptionId to (it[subscriptionId] ?: CellularSimData()).withTechnology(technologyInfo))
        }
    }

    override fun onCellularBatch(cellularGroup: List<CellularRecordWrapper>, subscriptionId: Int) {
        val now = clock()
        // The reducer is pure; these only remember what the successful update produced.
        var before: CellularSimData? = null
        var after: CellularSimData? = null
        sims.update {
            // Checked inside the update: an unregister that clears the cells makes this retry and drop.
            if (!registered) {
                after = null
                return@update it
            }
            before = it[subscriptionId]
            val next = (before ?: CellularSimData()).withBatch(cellularGroup, now)
            after = next
            it + (subscriptionId to next)
        }

        val updated = after ?: return
        // A scan without a serving cell keeps the last one on screen, but is not plotted again.
        val newServing = updated.serving?.takeIf { it !== before?.serving }
        if (newServing != null || cellularGroup.isEmpty()) {
            viewModelScope.launch(mainDispatcher) { charts.plot(subscriptionId, newServing?.chart) }
        }
        val servingRecord = updated.servingRecord
        if (servingRecord != null && servingRecord !== before?.servingRecord) {
            mutableLatestServingCell.value = ServingCellInfo(servingRecord, subscriptionId)
        }
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(environmentReceiver)
        unregister()
        charts.clear()
    }

    private fun onSimChanged() {
        Timber.i("SIM state changed; re-reading the Cellular view's SIMs")
        sims.value = emptyMap()
        service?.let { readPages(it) }
    }

    private fun readPages(service: NetworkSurveyService) {
        val newPages = CellularPages.from(simsOf(service))
        pagesKnown.value = true
        if (newPages != pages.value) {
            pages.value = newPages
            selectedPage.update { it.coerceIn(0, newPages.lastIndex) }
        }
    }

    private fun updateRegistration() {
        val bound = service
        val ready = scanState.value == CellularScanState.READY
        if (bound != null && ready && !registered) {
            registered = true
            this.bound.value = true
            bound.registerCellularSurveyRecordListener(this)
            // Scans can be minutes apart; one now fills the screen at once.
            bound.runSingleCellularScan()
        } else if (registered && !ready) {
            unregister()
            // What was shown is no longer true (airplane mode, location off).
            sims.value = emptyMap()
        }
    }

    private fun unregister() {
        if (!registered) return
        registered = false
        bound.value = false
        service?.unregisterCellularSurveyRecordListener(this)
    }
}

/** @return The phone's active SIMs, read through the survey service's cellular controller. */
private fun activeSims(service: NetworkSurveyService): List<SimInfo> =
    service.activeSubscriptionInfoList.orEmpty().map {
        SimInfo(slot = it.simSlotIndex + 1, subscriptionId = it.subscriptionId, name = it.displayName?.toString())
    }
