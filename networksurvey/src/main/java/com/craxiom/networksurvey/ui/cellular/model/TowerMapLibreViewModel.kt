package com.craxiom.networksurvey.ui.cellular.model

import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.data.api.Api
import com.craxiom.networksurvey.data.api.Tower
import com.craxiom.networksurvey.data.api.TowerResponse
import com.craxiom.networksurvey.data.api.retrofit
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.Plmn
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import retrofit2.Response
import timber.log.Timber
import java.util.Objects

const val INITIAL_ZOOM = 15.0
const val MIN_ZOOM_LEVEL = 9.0
const val MAX_AREA_SQ_METERS = 40_000_000_000.0
private const val MAX_TOWERS_ON_MAP = 5000

class TowerMapLibreViewModel : ViewModel() {

    // UI insets ------------------------------------
    private val _paddingInsets = MutableStateFlow(PaddingValues(0.dp, 0.dp, 0.dp, 0.dp))
    val paddingInsets = _paddingInsets.asStateFlow()

    // Serving cell info -----------------------------
    private val _servingCells = MutableStateFlow<HashMap<Int, ServingCellInfo>>(HashMap())
    val servingCells = _servingCells.asStateFlow()

    private val _servingSignals = MutableStateFlow<HashMap<Int, ServingSignalInfo>>(HashMap())
    val servingSignals = _servingSignals.asStateFlow()

    // Tower markers (stub) --------------------------
    private val _towers = MutableStateFlow(LinkedHashSet<TowerWrapper>(LinkedHashSet()))
    val towers = _towers.asStateFlow()

    // UI state flags --------------------------------
    private val _noTowersFound = MutableStateFlow(false)
    val noTowersFound = _noTowersFound.asStateFlow()

    private val _isLoadingInProgress = MutableStateFlow(true)
    val isLoadingInProgress = _isLoadingInProgress.asStateFlow()

    private val _isZoomedOutTooFar = MutableStateFlow(false)
    val isZoomedOutTooFar = _isZoomedOutTooFar.asStateFlow()

    // Filters & selections --------------------------
    private val _selectedRadioType = MutableStateFlow(CellularProtocol.LTE.name)
    val selectedRadioType = _selectedRadioType.asStateFlow()

    private val _plmnFilter = MutableStateFlow(Plmn(0, 0))
    val plmnFilter = _plmnFilter.asStateFlow()

    private val _selectedSource = MutableStateFlow(TowerSource.OpenCelliD)
    val selectedSource = _selectedSource.asStateFlow()

    // Last-queried viewport bounds ------------------
    private val _lastQueriedBounds = MutableStateFlow<LatLngBounds?>(null)
    val lastQueriedBounds = _lastQueriedBounds.asStateFlow()

    private var hasCenteredLocation = false

    // MapLibre handles ------------------------------
    private var mapView: MapView? = null
    private var mapLibreMap: MapLibreMap? = null

    // Mutex to prevent concurrent tower queries
    private val towerQueryMutex = Mutex()

    // Current location for drawing serving cell lines
    private var myLocation: Location? = null

    // Follow user location state
    private val _isFollowingUser = MutableStateFlow(false)
    val isFollowingUser = _isFollowingUser.asStateFlow()

    // Serving cell locations with range info
    private val subIdToServingCellLocations = HashMap<Int, ServingCellLocationInfo>()

    // Serving cell lines and coverage data ---------
    private val _servingCellLines = MutableStateFlow<List<ServingCellLineData>>(emptyList())
    val servingCellLines = _servingCellLines.asStateFlow()

    private val _servingCellCoverage = MutableStateFlow<List<ServingCellCoverageData>>(emptyList())
    val servingCellCoverage = _servingCellCoverage.asStateFlow()

    private val _mapTilerKey = MutableStateFlow<String?>(null)
    val mapTilerKey = _mapTilerKey.asStateFlow()

    private val _mapKeyLoadError = MutableStateFlow(false)
    val mapKeyLoadError = _mapKeyLoadError.asStateFlow()

    private val _isMapInitializing = MutableStateFlow(true)
    val isMapInitializing = _isMapInitializing.asStateFlow()

    val nsApi: Api = retrofit.create(Api::class.java)

    init {
        viewModelScope.launch {
            try {
                val resp = nsApi.getApiKey()
                if (resp.isSuccessful && resp.body() != null) {
                    _mapTilerKey.value = resp.body()!!.apiKey
                    Timber.i("MapTiler API key loaded successfully (${_mapTilerKey.value})")
                } else {
                    Timber.w("Failed to load MapTiler API key, falling back to OSM")
                    _mapKeyLoadError.value = true
                }
            } catch (t: Throwable) {
                Timber.e(t, "Error loading MapTiler API key, falling back to OSM")
                _mapKeyLoadError.value = true
            } finally {
                _isMapInitializing.value = false
            }
        }
    }

    fun setPaddingInsets(paddingValues: PaddingValues) {
        _paddingInsets.value = paddingValues
    }

    fun setSelectedRadioType(radioType: String) {
        _selectedRadioType.value = radioType
        // Clear towers when radio type changes
        _towers.value = LinkedHashSet()
        _noTowersFound.value = false
    }

    fun setPlmnFilter(plmn: Plmn) {
        _plmnFilter.value = plmn
        // Clear towers when PLMN filter changes
        _towers.value = LinkedHashSet()
        _noTowersFound.value = false
    }

    fun setTowerSource(towerSource: TowerSource) {
        _selectedSource.value = towerSource
        // Clear towers when source changes
        _towers.value = LinkedHashSet()
        _noTowersFound.value = false
    }

    /**
     * Call this from the Composable’s onMapReady.
     */
    fun initMapLibre(view: MapView, map: MapLibreMap, style: Style) {
        mapView = view
        mapLibreMap = map

        // 1) Restore saved viewport if available
        PreferenceUtils.getLatLngBoundsFromPreferences(view.context)
            ?.let { bounds ->
                // center & zoom
                val center = bounds.center
                val camPos = CameraPosition.Builder()
                    .target(center)
                    .zoom(INITIAL_ZOOM)
                    .build()
                map.moveCamera(CameraUpdateFactory.newCameraPosition(camPos))

                // avoid immediately refetching on startup
                _lastQueriedBounds.value = bounds
            }
            ?: run {
                // fallback, world view
                val camPos = CameraPosition.Builder()
                    .target(LatLng(0.0, 0.0))
                    .zoom(1.0)
                    .build()
                map.moveCamera(CameraUpdateFactory.newCameraPosition(camPos))
            }

        // 2) When the camera stops moving, trigger a tower query
        map.addOnCameraIdleListener {
            val bounds = map.projection.visibleRegion.latLngBounds
            if (bounds != _lastQueriedBounds.value) {
                _lastQueriedBounds.value = bounds
                val area = calculateArea(bounds)
                if (map.cameraPosition.zoom >= MIN_ZOOM_LEVEL && area <= MAX_AREA_SQ_METERS) {
                    _isZoomedOutTooFar.value = false
                    viewModelScope.launch { runTowerQuery() }
                } else {
                    _isZoomedOutTooFar.value = true
                    _isLoadingInProgress.value = false
                }
            }
        }

        // 3) tweak gesture settings
        map.uiSettings.apply {
            isZoomGesturesEnabled = true
            isScrollGesturesEnabled = true
        }
    }

    /**
     * Centers the map on the given location once and returns true if set.
     */
    fun setMapCenterLocation(location: Location): Boolean {
        if (!hasCenteredLocation && mapLibreMap != null) {
            hasCenteredLocation = true

            val target = LatLng(location.latitude, location.longitude)
            val camPos = CameraPosition.Builder()
                .target(target)
                .zoom(INITIAL_ZOOM)
                .build()

            // Ensure we call animateCamera on the main thread
            Handler(Looper.getMainLooper()).post {
                mapLibreMap!!.animateCamera(CameraUpdateFactory.newCameraPosition(camPos))
            }
        }
        return hasCenteredLocation
    }

    /**
     * Centers the map on the user's current location.
     */
    fun goToMyLocation() {
        mapLibreMap?.let { map ->
            map.locationComponent.lastKnownLocation?.let { location ->
                val target = LatLng(location.latitude, location.longitude)
                val camPos = CameraPosition.Builder()
                    .target(target)
                    .zoom(INITIAL_ZOOM)
                    .build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(camPos))
            }
        }
    }

    /**
     * Updates the current user location for drawing serving cell lines.
     */
    fun updateMyLocation(location: Location) {
        myLocation = location
        updateServingCellLines()
    }

    /**
     * Toggles the follow user location mode state.
     */
    fun toggleFollowUser() {
        _isFollowingUser.value = !_isFollowingUser.value
    }

    /**
     * Handle incoming cellular batches (serving cell updates).
     */
    fun onCellularBatchResults(
        cellularBatchResults: MutableList<CellularRecordWrapper?>?,
        subscriptionId: Int
    ) {
        if (cellularBatchResults.isNullOrEmpty()) return

        // Get the servingCellRecord from the cellularBatchResults and add it to the servingCells map
        // If none are found then clear the serving cell map for that particular subscriptionId
        val servingCellRecord =
            cellularBatchResults.firstOrNull {
                it?.cellularRecord != null && CellularUtils.isServingCell(it.cellularRecord)
            }

        updateServingCellSignals(servingCellRecord, subscriptionId)

        // No need to update the serving cell if it is the same as the current serving cell. This
        // prevents a map refresh which is expensive.
        val currentServingCell = _servingCells.value[subscriptionId]
        if (Objects.equals(currentServingCell?.servingCell, servingCellRecord)) return

        if (servingCellRecord == null) {
            _servingCells.update { map ->
                map.remove(subscriptionId)
                map
            }
        } else {
            _servingCells.update { oldMap ->
                val newMap = HashMap(oldMap)
                newMap[subscriptionId] = ServingCellInfo(servingCellRecord, subscriptionId)
                newMap
            }
        }

        // Update serving cell locations and coverage circles when serving cell changes
        updateServingCellLocations()

        mapView?.let { mapView ->
            // FIXME This needs to be updated
            recreateOverlaysFromTowerData(mapView, false)
        }
    }

    /**
     * Save the viewport when the Fragment pauses.
     */
    fun saveViewport(context: android.content.Context) {
        lastQueriedBounds.value?.let { bounds ->
            PreferenceUtils.saveTowerMapViewLatLngBounds(context, bounds)
        }
    }

    /**
     * Triggers any necessary updates to SIM count aware variables.
     */
    fun resetSimCount() {
        _servingCells.update {
            it.clear()
            it
        }
        _servingSignals.update {
            it.clear()
            it
        }
    }

    /**
     * Recreates the overlays on the map based on the current tower data.
     * @param mapView The map view to add the overlays to.
     * @param invalidate True to invalidate the map view after adding the overlays. Invalidating the
     * map will trigger a redraw of the map, which is important if the towers have changed, but a
     * side effect is that it closes any open info windows (shown when a user clicks on a tower).
     */
    @Synchronized
    fun recreateOverlaysFromTowerData(mapView: MapView, invalidate: Boolean = true) {
        try {
            /* FIXME towerOverlayGroup.items?.clear()
            subIdToServingCellLocations.clear()

            val towers = towers.value
            val servingCellGciIds: List<String>
            val servingCellToSubscriptionMap =
                servingCells.value.entries.associate { entry ->
                    CellularUtils.getTowerId(entry.value) to entry.value.subscriptionId
                }
            servingCellToSubscriptionMap.let { servingCellGciIds = it.keys.toList() }

            Timber.i("Adding %s points to the map", towers.size)
            towers.forEach { marker ->
                val isServingCell = servingCellGciIds.contains(marker.cgiId)
                if (isServingCell) {
                    // Get the value form servingCellToSubscriptionMap to be the key for the
                    // subIdToServingCellLocations so that we can set the value as marker.position
                    subIdToServingCellLocations[servingCellToSubscriptionMap[marker.cgiId]!!] =
                        GeoPointRange(marker.position, marker.tower.range)
                }
                marker.setServingCell(isServingCell)
                towerOverlayGroup.add(marker)
            }

            drawServingCellLine()
            drawServingCellCoverage()

            // .clusterer can cause a NPE if the markers are changed while the map is being drawn
            towerOverlayGroup.clusterer(mapView)

            if (invalidate) {
                towerOverlayGroup.invalidate()
            }
            mapView.postInvalidate()*/
        } catch (e: Exception) {
            Timber.e(e, "Something went wrong while recreating the overlays on the map")
        }
    }

    private fun updateServingCellSignals(
        servingCellRecord: CellularRecordWrapper?,
        subscriptionId: Int
    ) {
        if (servingCellRecord == null) {
            _servingSignals.update { map ->
                map.remove(subscriptionId)
                map
            }
        } else {
            _servingSignals.update { oldMap ->
                val newMap = HashMap(oldMap)
                newMap[subscriptionId] = CellularUtils.getSignalInfo(servingCellRecord)
                newMap
            }
        }
    }

    internal suspend fun runTowerQuery() = towerQueryMutex.withLock {
        val map = mapLibreMap ?: return@withLock
        _isLoadingInProgress.value = true

        // 1) Build bbox string for request
        val b = map.projection.visibleRegion.latLngBounds
        val bboxParam = listOf(
            b.latitudeSouth,
            b.longitudeWest,
            b.latitudeNorth,
            b.longitudeEast
        ).joinToString(",")

        // 2) Fetch from API
        val response: Response<TowerResponse> = try {
            if (plmnFilter.value.isSet()) {
                val p = plmnFilter.value
                nsApi.getTowers(
                    bboxParam,
                    selectedRadioType.value,
                    p.mcc,
                    p.mnc,
                    selectedSource.value.apiName
                )
            } else {
                nsApi.getTowers(bboxParam, selectedRadioType.value, selectedSource.value.apiName)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching towers from the NS API")
            _isLoadingInProgress.value = false
            return@withLock
        }

        // 3) Extract body or empty
        val fetched =
            if (response.code() == 204 || !response.isSuccessful || response.body() == null) {
                emptyList<TowerWrapper>()
            } else {
                response.body()!!.cells.map { TowerWrapper(it) }
            }
        Timber.i("Fetched ${fetched.size} towers")

        // 4) Merge into existing set, evict oldest if > MAX
        _towers.update { existing ->
            // Copy to preserve immutability
            val merged = LinkedHashSet(existing)

            fetched.forEach { wrapper ->
                // If already present, remove it so we can re-add and move to newest
                if (merged.remove(wrapper)) {
                    // no-op; removal done
                }
                merged.add(wrapper)
            }

            // Evict the oldest entries if we exceed the limit
            val overflow = merged.size - MAX_TOWERS_ON_MAP
            if (overflow > 0) {
                val iterator = merged.iterator()
                repeat(overflow.coerceAtLeast(0)) {
                    if (iterator.hasNext()) iterator.remove()
                }
            }
            merged
        }

        _noTowersFound.value = _towers.value.isEmpty()
        _isLoadingInProgress.value = false

        // 5) Recompute serving-cell overlays
        updateServingCellLocations()
    }


    /**
     * Updates serving cell lines based on current location and serving cells.
     */
    private fun updateServingCellLines() {
        val currentLocation = myLocation ?: return
        val myLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)

        val lines = subIdToServingCellLocations.map { (subscriptionId, locationInfo) ->
            ServingCellLineData(
                subscriptionId = subscriptionId,
                startPoint = myLatLng,
                endPoint = locationInfo.location
            )
        }

        _servingCellLines.value = lines
    }

    /**
     * Updates serving cell coverage circles.
     */
    private fun updateServingCellCoverage() {
        val coverage = subIdToServingCellLocations.mapNotNull { (subscriptionId, locationInfo) ->
            if (locationInfo.range > 0) {
                ServingCellCoverageData(
                    subscriptionId = subscriptionId,
                    center = locationInfo.location,
                    radiusMeters = locationInfo.range
                )
            } else null
        }

        _servingCellCoverage.value = coverage
    }

    /**
     * Updates serving cell locations when towers or serving cells change.
     */
    private fun updateServingCellLocations() {
        subIdToServingCellLocations.clear()

        val servingCellToSubscriptionMap: Map<String, Int> =
            servingCells.value.entries.associate { entry ->
                CellularUtils.getTowerId(entry.value) to entry.value.subscriptionId
            }

        // Find towers that match serving cells
        towers.value.forEach { towerItem ->
            val subscriptionId: Int? = servingCellToSubscriptionMap[towerItem.towerId]

            if (subscriptionId != null) {
                subIdToServingCellLocations[subscriptionId] = ServingCellLocationInfo(
                    location = LatLng(towerItem.tower.lat, towerItem.tower.lon),
                    range = towerItem.tower.range
                )
            }
        }

        updateServingCellLines()
        updateServingCellCoverage()
    }

    /**
     * Approximate area of the LatLngBounds in m² (using haversine).
     */
    private fun calculateArea(bounds: LatLngBounds): Double {
        val sw = bounds.southWest
        val ne = bounds.northEast
        val earthRadius = 6_371_000.0 // meters

        val dLat = Math.toRadians(ne.latitude - sw.latitude)
        val dLon = Math.toRadians(ne.longitude - sw.longitude)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(sw.latitude)) *
                kotlin.math.cos(Math.toRadians(ne.latitude)) *
                kotlin.math.sin(dLon / 2).let { it * it }
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        val width = earthRadius * c
        return width * width
    }
}

data class TowerWrapper(val tower: Tower) {
    internal val towerId: String = CellularUtils.getTowerId(tower)
}

