package com.craxiom.networksurvey.ui.map

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.SimChanges
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.TowerKey
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.activesurvey.NewTowerRegistry
import com.craxiom.networksurvey.ui.cellular.model.INITIAL_ZOOM
import com.craxiom.networksurvey.ui.cellular.model.MapCellFeed
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.signals.ViewedSim
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * The Map tab's state that lives as long as the activity: the serving cell of each SIM, the SIM the
 * map shows, the camera, and whether location is on. It binds the map to the survey service and
 * feeds the map ([MapCellFeed]) while the tab shows.
 *
 * The map's own view model lives with the tab's back stack entry and its MapView is recreated on
 * each visit; this one keeps what must survive a visit, and lets the Cellular view's map action
 * choose the SIM before the tab opens.
 */
class MapTabViewModel(application: Application) : AndroidViewModel(application), ICellularSurveyRecordListener {

    /** The map being fed, while the Map tab shows. */
    @Volatile
    private var feed: MapCellFeed? = null

    /** The last map given its starting filters, so a returning visit keeps the user's filters. */
    private var seededFeed = WeakReference<MapCellFeed>(null)

    /** Counts SIM changes, so a map that missed one while the tab was hidden forgets its SIMs. */
    @Volatile
    private var simChanges = 0
    private var seededFeedSimChanges = 0

    // Held only between a bind and the unbind (onServiceChanged(null) clears it), so the listeners
    // can be removed from the same service they were added to.
    @SuppressLint("StaticFieldLeak")
    private var service: NetworkSurveyService? = null
    private var locationListener: LocationListener? = null
    private var centered = false

    private val mutableServingCells = MutableStateFlow<Map<Int, ServingCellInfo>>(emptyMap())

    /** The latest serving cell of each SIM, by subscription ID. */
    val servingCells: StateFlow<Map<Int, ServingCellInfo>> = mutableServingCells.asStateFlow()

    private val mutableLatestServingCell = MutableStateFlow<ServingCellInfo?>(null)

    /** The most recent serving cell of any SIM. */
    val latestServingCell: StateFlow<ServingCellInfo?> = mutableLatestServingCell.asStateFlow()

    /** The SIM the user (or the Cellular view's map action) asked for, which may have no cell yet. */
    private val wantedSim = MutableStateFlow<Int?>(null)

    /** The SIM the map shows: the wanted one once it has a serving cell, otherwise the first. */
    val selectedSim: StateFlow<Int?> = combine(wantedSim, mutableServingCells) { wanted, cells ->
        wanted?.takeIf { it in cells } ?: cells.keys.minOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Where the camera was when the map last left the screen, or null before it has been shown. */
    var camera: MapCamera? = null
        private set

    private val mutableLocationEnabled = MutableStateFlow(true)

    /** False while the phone's location setting is off; the map shows a banner then. */
    val locationEnabled: StateFlow<Boolean> = mutableLocationEnabled.asStateFlow()

    /** The towers found new in this Community upload; replaceable for tests. */
    internal var newTowers: StateFlow<Set<TowerKey>> = NewTowerRegistry.newTowers

    private fun onSimChanged() {
        Timber.i("SIM state changed; forgetting the map's serving cells")
        simChanges++
        mutableServingCells.value = emptyMap()
        feed?.let { attached ->
            attached.resetSimCount()
            if (attached === seededFeed.get()) seededFeedSimChanges = simChanges
        }
    }

    private val locationModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshLocationEnabled()
    }

    init {
        SimChanges.changes.onEach { onSimChanged() }.launchIn(viewModelScope)
        ContextCompat.registerReceiver(
            application,
            locationModeReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refreshLocationEnabled()
        selectedSim.onEach { sim -> if (sim != null) feed?.setSelectedSimSubscriptionId(sim) }.launchIn(viewModelScope)
    }

    /**
     * Starts feeding [mapFeed], the map now on screen. A map seen for the first time gets the
     * saved tower source and the technology and operator of [seed] (or of the latest serving cell
     * this view model saw); every map gets the serving cells replayed so its overlay shows at once.
     *
     * @param seed The latest serving cell the Cellular view saw, which may be newer than ours.
     */
    fun attach(mapFeed: MapCellFeed, seed: ServingCellInfo?) {
        seed?.let(::offerServingCell)
        if (seededFeed.get() === mapFeed && seededFeedSimChanges != simChanges) {
            // The SIMs changed while this map was off screen
            mapFeed.resetSimCount()
        }
        if (seededFeed.get() !== mapFeed) {
            seededFeed = WeakReference(mapFeed)
            mapFeed.setTowerSource(PreferenceUtils.getLastSelectedTowerSource(getApplication()))
            val startCell = selectedSim.value?.let { servingCells.value[it] } ?: latestServingCell.value
            startCell?.servingCell?.let { cell ->
                if (cell.cellularProtocol != CellularProtocol.NONE) mapFeed.setSelectedRadioType(cell.cellularProtocol.name)
                cell.plmn?.let(mapFeed::setPlmnFilter)
            }
        }
        seededFeedSimChanges = simChanges
        feed = mapFeed
        servingCells.value.values.forEach { info -> mapFeed.onCellularBatchResults(listOf(info.servingCell), info.subscriptionId) }
        selectedSim.value?.let(mapFeed::setSelectedSimSubscriptionId)
    }

    /** Stops feeding [mapFeed]; a no-op when another map has been attached since. */
    fun detach(mapFeed: MapCellFeed) {
        if (feed === mapFeed) feed = null
    }

    /**
     * Called with the bound service while the Map tab shows, and with null before it is unbound,
     * so the listeners never keep the service in use once the tab is gone.
     */
    fun onServiceChanged(service: NetworkSurveyService?) {
        this.service?.let { old ->
            old.unregisterCellularSurveyRecordListener(this)
            locationListener?.let(old::unregisterLocationListener)
            locationListener = null
        }
        this.service = service
        if (service == null) return

        service.registerCellularSurveyRecordListener(this)
        refreshLocationEnabled()
        if (camera == null && !centered) centerOnFirstLocation(service)
    }

    /** Uses the Cellular view's SIM the first time the map shows, unless one was already chosen. */
    fun seedSim(viewedSim: ViewedSim?) {
        if (wantedSim.value == null) requestSim(viewedSim)
    }

    /** Shows [viewedSim] (the Cellular view's map action), even before it has a serving cell. */
    fun requestSim(viewedSim: ViewedSim?) {
        if (viewedSim != null && !viewedSim.isNoSim) wantedSim.value = viewedSim.subscriptionId
    }

    /** Shows the SIM the user picked on the serving-cell card. */
    fun selectSim(subscriptionId: Int) {
        wantedSim.value = subscriptionId
    }

    /** Remembers where the camera is as the map leaves the screen. */
    fun saveCamera(camera: MapCamera) {
        this.camera = camera
    }

    /**
     * @return Where the map should open: where it was left, else the center of the viewport saved
     * by an earlier launch, else null for the world view.
     */
    fun initialCamera(): MapCamera? = camera ?: PreferenceUtils.getLatLngBoundsFromPreferences(getApplication())
        ?.center?.let { MapCamera(it.latitude, it.longitude, INITIAL_ZOOM) }

    /** Re-reads whether location is on (also called on resume). */
    fun refreshLocationEnabled() {
        val locationManager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        mutableLocationEnabled.value = locationManager?.let(LocationManagerCompat::isLocationEnabled) ?: true
    }

    override fun onCellularBatch(cellularGroup: List<CellularRecordWrapper?>?, subscriptionId: Int) {
        // An empty batch is a scan that returned nothing (or failed), not a lost serving cell; the
        // map ignores it too, so a SIM never drops out of the card for one bad scan
        if (cellularGroup.isNullOrEmpty()) return
        feed?.onCellularBatchResults(cellularGroup, subscriptionId)
        val servingCell = cellularGroup?.firstOrNull { it?.cellularRecord != null && CellularUtils.isServingCell(it.cellularRecord) }
        if (servingCell == null) {
            mutableServingCells.update { it - subscriptionId }
        } else {
            offerServingCell(ServingCellInfo(servingCell, subscriptionId))
        }
    }

    override fun onCleared() {
        onServiceChanged(null)
        getApplication<Application>().unregisterReceiver(locationModeReceiver)
    }

    /** Keeps [info] as its SIM's serving cell unless a newer one is already known. */
    private fun offerServingCell(info: ServingCellInfo) {
        if (info.servingCell == null) return
        // Compared inside the update: a seed on the main thread can race a scan on the survey thread
        mutableServingCells.update { cells ->
            val current = cells[info.subscriptionId]
            if (current != null && current.timestamp > info.timestamp) cells else cells + (info.subscriptionId to info)
        }
        mutableLatestServingCell.update { latest ->
            if (latest != null && latest.timestamp > info.timestamp) latest else info
        }
    }

    /** Centers the map on the phone's location once, on the first visit of this launch. */
    private fun centerOnFirstLocation(service: NetworkSurveyService) {
        service.primaryLocationListener?.latestLocation?.let { if (tryCenter(it)) return }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (tryCenter(location)) {
                    service.unregisterLocationListener(this)
                    if (locationListener === this) locationListener = null
                }
            }
        }
        locationListener = listener
        service.registerLocationListener(listener)
    }

    private fun tryCenter(location: Location): Boolean {
        if (feed?.setMapCenterLocation(location) == true) centered = true
        return centered
    }
}
