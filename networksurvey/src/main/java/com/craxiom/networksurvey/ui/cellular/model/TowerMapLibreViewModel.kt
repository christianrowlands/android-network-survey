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
import com.craxiom.networksurvey.ui.cellular.towermap.TOWER_LAYER_KEY
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.VectorSource
import retrofit2.Response
import timber.log.Timber
import java.util.Objects

const val INITIAL_ZOOM = 14.0
const val SEARCH_RESULT_ZOOM = 17.0
const val MIN_ZOOM_LEVEL = 9.0
const val MINIMUM_LOCATION_ZOOM = 12.0  // Minimum zoom when centering on user location
const val MAX_AREA_SQ_METERS = 40_000_000_000.0
private const val MAX_TOWERS_ON_MAP = 7_500

const val SERVING_CELL_LINE_LAYER_PREFIX = "line-layer-"
const val SERVING_CELL_COVERAGE_FILL_LAYER_PREFIX = "circle-fill-layer-"
const val SERVING_CELL_COVERAGE_OUTLINE_LAYER_PREFIX = "circle-stroke-layer-"

private const val BEACONDB_STYLE_SOURCE_NAME = "beacondb-source"
private const val BEACONDB_COVERAGE_COLOR = "#ff8000"
private const val BEACONDB_COVERAGE_OPACITY = 0.4f

// Hysteresis constants for reducing tower queries
private const val BOUNDS_CHANGE_THRESHOLD_PERCENT = 0.20 // 20% change required
private const val ZOOM_CHANGE_THRESHOLD = 0.5 // Half zoom level change required


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

    private val _isLoadingInProgress = MutableStateFlow(false)
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

    // Track last zoom level for hysteresis
    private var lastQueriedZoom = 0.0

    private var hasCenteredLocation = false

    // MapLibre handles ------------------------------
    private var mapView: MapView? = null
    private var mapLibreMap: MapLibreMap? = null

    // Mutex to prevent concurrent tower queries
    private val towerQueryMutex = Mutex()

    // Debounce timer for tower queries
    private var lastQueryTime = 0L
    private val QUERY_DEBOUNCE_MS = 1000L // Minimum 1 second between queries

    // Current location for drawing serving cell lines
    private var myLocation: Location? = null

    // Track previous serving cell technology per subscription to detect changes
    private val previousServingCellTechnology = HashMap<Int, String>()

    // Track if the current radio type selection is manual (user-selected) vs automatic
    private var isManualRadioTypeSelection = false

    // Selected SIM subscription ID for display
    private val _selectedSimSubscriptionId = MutableStateFlow<Int?>(null)
    val selectedSimSubscriptionId = _selectedSimSubscriptionId.asStateFlow()

    // Map layer settings
    private val _selectedMapTileSource = MutableStateFlow(MapTileSource.MAPTILER)
    val selectedMapTileSource = _selectedMapTileSource.asStateFlow()

    private val _showBeaconDbCoverage = MutableStateFlow(false)
    val showBeaconDbCoverage = _showBeaconDbCoverage.asStateFlow()

    private val _showTowersLayer = MutableStateFlow(true)
    val showTowersLayer = _showTowersLayer.asStateFlow()

    // BeaconDB layer management
    private var beaconDbLayerIds: List<String> = emptyList()

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

    // Search functionality
    private val _searchedTower = MutableStateFlow<Tower?>(null)
    val searchedTower = _searchedTower.asStateFlow()

    private val _searchedTowerCoverage = MutableStateFlow<ServingCellCoverageData?>(null)
    val searchedTowerCoverage = _searchedTowerCoverage.asStateFlow()

    private val _isSearchInProgress = MutableStateFlow(false)
    val isSearchInProgress = _isSearchInProgress.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError = _searchError.asStateFlow()

    // Search input state
    private val _searchMccInput = MutableStateFlow("")
    val searchMccInput = _searchMccInput.asStateFlow()

    private val _searchMncInput = MutableStateFlow("")
    val searchMncInput = _searchMncInput.asStateFlow()

    private val _searchAreaInput = MutableStateFlow("")
    val searchAreaInput = _searchAreaInput.asStateFlow()

    private val _searchCidInput = MutableStateFlow("")
    val searchCidInput = _searchCidInput.asStateFlow()

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
                    setSelectedMapTileSource(MapTileSource.OPENSTREETMAP)
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

    fun setSelectedRadioType(radioType: String, isManualSelection: Boolean = false) {
        if (_selectedRadioType.value != radioType) {
            _selectedRadioType.value = radioType
            isManualRadioTypeSelection = isManualSelection
            // Clear towers when radio type changes
            _towers.value = LinkedHashSet()
            _noTowersFound.value = false
            // Automatically trigger a new query for the selected radio type if layer is visible
            if (_showTowersLayer.value) {
                viewModelScope.launch {
                    runTowerQuery()
                }
            }
        } else if (isManualSelection) {
            // Even if the radio type is the same, update the manual selection flag
            isManualRadioTypeSelection = true
        }
    }

    fun setPlmnFilter(plmn: Plmn) {
        if (_plmnFilter.value != plmn) {
            _plmnFilter.value = plmn
            // Clear towers when PLMN filter changes
            _towers.value = LinkedHashSet()
            _noTowersFound.value = false
            // Automatically trigger a new query for the new filter if layer is visible
            if (_showTowersLayer.value) {
                viewModelScope.launch {
                    runTowerQuery()
                }
            }
        }
    }

    fun setTowerSource(towerSource: TowerSource) {
        if (_selectedSource.value != towerSource) {
            _selectedSource.value = towerSource
            // Clear towers when source changes
            _towers.value = LinkedHashSet()
            _noTowersFound.value = false
            // Automatically trigger a new query for the new source if layer is visible
            if (_showTowersLayer.value) {
                viewModelScope.launch {
                    runTowerQuery()
                }
            }
        }
    }

    fun setSelectedSimSubscriptionId(subscriptionId: Int) {
        if (_selectedSimSubscriptionId.value != subscriptionId) {
            _selectedSimSubscriptionId.value = subscriptionId

            // Clear manual selection when switching SIMs to allow automatic updates
            isManualRadioTypeSelection = false

            // Update the radio type based on the selected SIM's serving cell
            val servingCellInfo = _servingCells.value[subscriptionId]
            val currentTechnology = servingCellInfo?.servingCell?.cellularProtocol?.name
            if (currentTechnology != null) {
                Timber.d("Selected SIM changed to subscription $subscriptionId with technology $currentTechnology")
                setSelectedRadioType(currentTechnology)
            }

            // Recompute serving cell overlays for the selected SIM
            updateServingCellLines()
            updateServingCellCoverage()
        }
    }

    fun setSelectedMapTileSource(source: MapTileSource) {
        _selectedMapTileSource.value = source
    }

    fun setShowBeaconDbCoverage(show: Boolean) {
        _showBeaconDbCoverage.value = show
    }

    fun setShowTowersLayer(show: Boolean) {
        val wasHidden = !_showTowersLayer.value
        _showTowersLayer.value = show

        // Clear search result when towers layer is hidden
        if (!show) {
            clearSearchResult()
        }

        // If the layer was hidden and is now being shown, trigger a tower query
        if (wasHidden && show) {
            Timber.d("Tower layer re-enabled, triggering query")
            val map = mapLibreMap
            if (map != null) {
                val bounds = map.projection.visibleRegion.latLngBounds
                val area = calculateArea(bounds)
                if (map.cameraPosition.zoom >= MIN_ZOOM_LEVEL && area <= MAX_AREA_SQ_METERS) {
                    viewModelScope.launch {
                        runTowerQuery()
                    }
                }
            }
        }
    }

    fun addBeaconDbCoverageLayer() {
        mapLibreMap?.let { map ->
            map.style?.let { style ->
                try {
                    val sourceId = BEACONDB_STYLE_SOURCE_NAME
                    val layerPrefix = "beacondb-layer-"
                    val tileJsonUrl = "https://cdn.beacondb.net/tiles/beacondb.json"

                    // Add BeaconDB vector source if not already added
                    val existingSource =
                        style.getSource(sourceId) as? VectorSource
                    if (existingSource == null || existingSource.uri != tileJsonUrl) {
                        style.removeSource(sourceId)
                        val vectorSource =
                            VectorSource(sourceId, tileJsonUrl)
                        style.addSource(vectorSource)
                    }

                    // Fetch layer IDs from TileJSON and add layers
                    viewModelScope.launch {
                        try {
                            val layerIds = fetchBeaconDbLayerIds(tileJsonUrl)
                            layerIds.forEach { layerId ->
                                val fullLayerId = layerPrefix + layerId
                                if (style.getLayer(fullLayerId) == null) {
                                    val layer = FillLayer(
                                        fullLayerId,
                                        sourceId
                                    ).apply {
                                        sourceLayer = layerId
                                        setProperties(
                                            PropertyFactory.fillColor(BEACONDB_COVERAGE_COLOR),
                                            PropertyFactory.fillOpacity(BEACONDB_COVERAGE_OPACITY)
                                        )
                                    }

                                    // Add the layer below our custom layers but above base map tiles
                                    // Look for our specific custom layers to insert before them
                                    val existingLayers = style.layers
                                    var insertBeforeLayerId: String? = null

                                    // Look for our custom layer IDs first (tower symbols, serving cell lines/circles)
                                    for (existingLayer in existingLayers) {
                                        val layerId = existingLayer.id
                                        // Check if this is one of our custom layers
                                        if (layerId == TOWER_LAYER_KEY ||  // Tower symbols (exact match)
                                            layerId.startsWith(SERVING_CELL_LINE_LAYER_PREFIX) ||  // Serving cell lines
                                            layerId.startsWith(
                                                SERVING_CELL_COVERAGE_FILL_LAYER_PREFIX
                                            ) ||  // Coverage circle fills
                                            layerId.startsWith(
                                                SERVING_CELL_COVERAGE_OUTLINE_LAYER_PREFIX
                                            )
                                        ) {  // Coverage circle strokes
                                            insertBeforeLayerId = layerId
                                            break
                                        }
                                    }

                                    if (insertBeforeLayerId != null) {
                                        style.addLayerBelow(layer, insertBeforeLayerId)
                                    } else {
                                        // If no custom layers found, add at the end (above base tiles)
                                        style.addLayer(layer)
                                    }
                                }
                            }
                            // Store layer IDs for removal
                            beaconDbLayerIds = layerIds
                        } catch (e: Exception) {
                            Timber.e(e, "Error fetching BeaconDB layer IDs")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error adding BeaconDB coverage layer")
                }
            }
        }
    }

    fun removeBeaconDbCoverageLayer() {
        mapLibreMap?.let { map ->
            map.style?.let { style ->
                try {
                    val layerPrefix = "beacondb-layer-"

                    // Remove all BeaconDB layers
                    beaconDbLayerIds.forEach { layerId ->
                        val fullLayerId = layerPrefix + layerId
                        style.getLayer(fullLayerId)?.let {
                            style.removeLayer(fullLayerId)
                        }
                    }

                    // Remove source
                    style.getSource(BEACONDB_STYLE_SOURCE_NAME)?.let {
                        style.removeSource(BEACONDB_STYLE_SOURCE_NAME)
                    }

                    // Clear stored layer IDs
                    beaconDbLayerIds = emptyList()
                } catch (e: Exception) {
                    Timber.e(e, "Error removing BeaconDB coverage layer")
                }
            }
        }
    }

    private suspend fun fetchBeaconDbLayerIds(tileJsonUrl: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL(tileJsonUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = org.json.JSONObject(response)

                // Extract layer IDs from vector_layers array
                val vectorLayers = jsonObject.optJSONArray("vector_layers")
                val layerIds = mutableListOf<String>()

                if (vectorLayers != null) {
                    for (i in 0 until vectorLayers.length()) {
                        val layer = vectorLayers.getJSONObject(i)
                        val id = layer.optString("id")
                        if (id.isNotEmpty()) {
                            layerIds.add(id)
                        }
                    }
                }

                layerIds
            } catch (e: Exception) {
                Timber.e(e, "Error fetching BeaconDB TileJSON")
                emptyList()
            }
        }
    }

    /**
     * Call this from the Composable’s onMapReady.
     */
    fun initMapLibre(view: MapView, map: MapLibreMap, style: Style) {
        mapView = view
        mapLibreMap = map

        // 1) Restore saved viewport if available and check if we need to refresh towers
        val shouldRefreshTowers = PreferenceUtils.getLatLngBoundsFromPreferences(view.context)
            ?.let { bounds ->
                // center & zoom
                val center = bounds.center
                val camPos = CameraPosition.Builder()
                    .target(center)
                    .zoom(INITIAL_ZOOM)
                    .build()
                map.moveCamera(CameraUpdateFactory.newCameraPosition(camPos))

                // If we have tower data but map was recreated (screen off/on), we need to refresh
                val hasTowerData = _towers.value.isNotEmpty()
                if (hasTowerData) {
                    Timber.d("Map recreated with existing tower data, will refresh towers")
                    // Don't set lastQueriedBounds yet - let the camera idle listener trigger a refresh
                    true
                } else {
                    // No tower data yet, avoid immediate refetching on startup
                    _lastQueriedBounds.value = bounds
                    false
                }
            }
            ?: run {
                // fallback, world view
                val camPos = CameraPosition.Builder()
                    .target(LatLng(0.0, 0.0))
                    .zoom(1.0)
                    .build()
                map.moveCamera(CameraUpdateFactory.newCameraPosition(camPos))
                false
            }

        // 2) When the camera stops moving, trigger a tower query
        map.addOnCameraIdleListener {
            val bounds = map.projection.visibleRegion.latLngBounds
            val lastBounds = _lastQueriedBounds.value
            val currentZoom = map.cameraPosition.zoom

            // Check if bounds changed (basic check first)
            val boundsChanged = lastBounds == null || !areBoundsEqual(bounds, lastBounds)

            // Apply hysteresis logic if bounds have changed
            val shouldQuery = if (boundsChanged && lastBounds != null) {
                // Calculate the percentage change in bounds
                val boundsChangePercent = calculateBoundsChangePercent(lastBounds, bounds)
                val zoomChange = kotlin.math.abs(currentZoom - lastQueriedZoom)

                // Query if either:
                // 1. Bounds changed by more than threshold percentage
                // 2. Zoom changed by more than threshold
                val exceedsThreshold = boundsChangePercent >= BOUNDS_CHANGE_THRESHOLD_PERCENT ||
                        zoomChange >= ZOOM_CHANGE_THRESHOLD

                if (!exceedsThreshold) {
                    Timber.d(
                        "Bounds changed but below threshold: ${(boundsChangePercent * 100).toInt()}% (threshold: ${(BOUNDS_CHANGE_THRESHOLD_PERCENT * 100).toInt()}%), zoom change: ${
                            "%.1f".format(
                                zoomChange
                            )
                        }"
                    )
                }

                exceedsThreshold
            } else {
                // Always query if this is the first time or bounds haven't changed
                boundsChanged
            }

            val currentTime = System.currentTimeMillis()
            val timeSinceLastQuery = currentTime - lastQueryTime

            if (shouldQuery && timeSinceLastQuery >= QUERY_DEBOUNCE_MS) {
                Timber.d("Camera bounds changed significantly, triggering tower query (time since last: ${timeSinceLastQuery}ms)")
                lastQueryTime = currentTime
                _lastQueriedBounds.value = bounds
                lastQueriedZoom = currentZoom

                val area = calculateArea(bounds)
                if (currentZoom >= MIN_ZOOM_LEVEL && area <= MAX_AREA_SQ_METERS) {
                    _isZoomedOutTooFar.value = false
                    // Only query towers if the layer is visible
                    if (_showTowersLayer.value) {
                        viewModelScope.launch { runTowerQuery() }
                    } else {
                        Timber.d("Tower layer is hidden, skipping query")
                        _isLoadingInProgress.value = false
                    }
                } else {
                    _isZoomedOutTooFar.value = true
                    _isLoadingInProgress.value = false
                }
            } else {
                if (!shouldQuery) {
                    Timber.d("Camera movement below hysteresis threshold, skipping tower query")
                } else if (timeSinceLastQuery < QUERY_DEBOUNCE_MS) {
                    Timber.d("Tower query debounced (${timeSinceLastQuery}ms < ${QUERY_DEBOUNCE_MS}ms)")
                }
            }
        }

        // 3) tweak gesture settings
        map.uiSettings.apply {
            isZoomGesturesEnabled = true
            isScrollGesturesEnabled = true
        }

        // 4) If we determined we should refresh towers (map recreated), do it now
        if (shouldRefreshTowers) {
            viewModelScope.launch {
                // Small delay to ensure map is fully initialized
                kotlinx.coroutines.delay(100)
                val currentBounds = map.projection.visibleRegion.latLngBounds
                val area = calculateArea(currentBounds)
                if (map.cameraPosition.zoom >= MIN_ZOOM_LEVEL && area <= MAX_AREA_SQ_METERS) {
                    Timber.d("Forcing tower refresh after map recreation")
                    _isZoomedOutTooFar.value = false
                    runTowerQuery()
                    // Now set the bounds so future moves work normally
                    _lastQueriedBounds.value = currentBounds
                } else {
                    _isZoomedOutTooFar.value = true
                }
            }
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
     * Gets the current zoom level of the map.
     */
    fun getCurrentZoom(): Double {
        return mapLibreMap?.cameraPosition?.zoom ?: INITIAL_ZOOM
    }

    /**
     * Gets the user's current location if available.
     */
    fun getMyLocation(): LatLng? {
        return mapLibreMap?.locationComponent?.lastKnownLocation?.let { location ->
            LatLng(location.latitude, location.longitude)
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

        // Check if the serving cell technology has changed and update selectedRadioType if needed
        checkAndUpdateSelectedRadioType(servingCellRecord, subscriptionId)

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
                newMap[subscriptionId] =
                    ServingCellInfo(servingCellRecord, subscriptionId, System.currentTimeMillis())
                newMap
            }
        }

        // Update serving cell locations and coverage circles when serving cell changes
        updateServingCellLocations()
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
        previousServingCellTechnology.clear()
        // Clear manual selection when SIM changes
        isManualRadioTypeSelection = false
    }

    /**
     * Checks if the serving cell technology has changed and updates selectedRadioType if appropriate.
     * Only updates when the technology changes for the selected SIM subscription.
     */
    private fun checkAndUpdateSelectedRadioType(
        servingCellRecord: CellularRecordWrapper?,
        subscriptionId: Int
    ) {
        val currentTechnology = servingCellRecord?.cellularProtocol?.name
        val previousTechnology = previousServingCellTechnology[subscriptionId]

        // Update the stored technology for this subscription
        if (currentTechnology != null) {
            previousServingCellTechnology[subscriptionId] = currentTechnology
        } else {
            previousServingCellTechnology.remove(subscriptionId)
        }

        // Only update selectedRadioType if:
        // 1. The technology has actually changed for this subscription
        // 2. This is the selected subscription (or no selection made yet)
        // 3. The user hasn't manually selected a radio type
        if (currentTechnology != null && currentTechnology != previousTechnology && !isManualRadioTypeSelection) {
            val selectedSim = _selectedSimSubscriptionId.value

            if (selectedSim == null || subscriptionId == selectedSim) {
                Timber.d("Serving cell technology changed from $previousTechnology to $currentTechnology for subscription $subscriptionId, updating selectedRadioType")
                setSelectedRadioType(currentTechnology)
            } else {
                Timber.d("Serving cell technology changed from $previousTechnology to $currentTechnology for subscription $subscriptionId, but not updating selectedRadioType (selected is $selectedSim)")
            }
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
        Timber.d("Starting tower query")

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
                    if (iterator.hasNext()) {
                        iterator.next()  // Must call next() before remove()
                        iterator.remove()
                    }
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

        // Use selected SIM if set, otherwise show all
        val selectedSim = _selectedSimSubscriptionId.value
        val lines = if (selectedSim != null) {
            // Show only the selected SIM's line
            subIdToServingCellLocations
                .filter { (subscriptionId, _) -> subscriptionId == selectedSim }
                .map { (subscriptionId, locationInfo) ->
                    ServingCellLineData(
                        subscriptionId = subscriptionId,
                        startPoint = myLatLng,
                        endPoint = locationInfo.location,
                        distanceMeters = haversineDistance(myLatLng, locationInfo.location)
                    )
                }
        } else {
            // Show all SIM lines
            subIdToServingCellLocations.map { (subscriptionId, locationInfo) ->
                ServingCellLineData(
                    subscriptionId = subscriptionId,
                    startPoint = myLatLng,
                    endPoint = locationInfo.location,
                    distanceMeters = haversineDistance(myLatLng, locationInfo.location)
                )
            }
        }

        _servingCellLines.value = lines
    }

    /**
     * Updates serving cell coverage circles.
     */
    private fun updateServingCellCoverage() {
        // Use selected SIM if set, otherwise show all
        val selectedSim = _selectedSimSubscriptionId.value
        val coverage = if (selectedSim != null) {
            // Show only the selected SIM's coverage
            subIdToServingCellLocations
                .filter { (subscriptionId, _) -> subscriptionId == selectedSim }
                .mapNotNull { (subscriptionId, locationInfo) ->
                    if (locationInfo.range > 0) {
                        ServingCellCoverageData(
                            subscriptionId = subscriptionId,
                            center = locationInfo.location,
                            radiusMeters = locationInfo.range
                        )
                    } else null
                }
        } else {
            // Show all SIM coverage
            subIdToServingCellLocations.mapNotNull { (subscriptionId, locationInfo) ->
                if (locationInfo.range > 0) {
                    ServingCellCoverageData(
                        subscriptionId = subscriptionId,
                        center = locationInfo.location,
                        radiusMeters = locationInfo.range
                    )
                } else null
            }
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
     * Compare two LatLngBounds with tolerance for floating point precision.
     */
    private fun areBoundsEqual(
        bounds1: LatLngBounds,
        bounds2: LatLngBounds,
        tolerance: Double = 0.0001
    ): Boolean {
        return kotlin.math.abs(bounds1.latitudeNorth - bounds2.latitudeNorth) < tolerance &&
                kotlin.math.abs(bounds1.latitudeSouth - bounds2.latitudeSouth) < tolerance &&
                kotlin.math.abs(bounds1.longitudeEast - bounds2.longitudeEast) < tolerance &&
                kotlin.math.abs(bounds1.longitudeWest - bounds2.longitudeWest) < tolerance
    }

    /**
     * Calculate the percentage change between two bounds.
     * Returns the maximum percentage change in any dimension (lat/lng).
     */
    private fun calculateBoundsChangePercent(
        oldBounds: LatLngBounds,
        newBounds: LatLngBounds
    ): Double {
        val oldLatSpan = oldBounds.latitudeNorth - oldBounds.latitudeSouth
        val oldLngSpan = oldBounds.longitudeEast - oldBounds.longitudeWest
        val newLatSpan = newBounds.latitudeNorth - newBounds.latitudeSouth
        val newLngSpan = newBounds.longitudeEast - newBounds.longitudeWest

        // Calculate center points
        val oldCenterLat = (oldBounds.latitudeNorth + oldBounds.latitudeSouth) / 2
        val oldCenterLng = (oldBounds.longitudeEast + oldBounds.longitudeWest) / 2
        val newCenterLat = (newBounds.latitudeNorth + newBounds.latitudeSouth) / 2
        val newCenterLng = (newBounds.longitudeEast + newBounds.longitudeWest) / 2

        // Calculate center movement as percentage of old bounds size
        val latCenterChange = kotlin.math.abs(newCenterLat - oldCenterLat) / oldLatSpan
        val lngCenterChange = kotlin.math.abs(newCenterLng - oldCenterLng) / oldLngSpan

        // Calculate size change
        val latSizeChange = kotlin.math.abs(newLatSpan - oldLatSpan) / oldLatSpan
        val lngSizeChange = kotlin.math.abs(newLngSpan - oldLngSpan) / oldLngSpan

        // Return the maximum change percentage
        return maxOf(latCenterChange, lngCenterChange, latSizeChange, lngSizeChange)
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

    /**
     * Calculates the distance between two LatLng points using the haversine formula.
     *
     * @param point1 The first point
     * @param point2 The second point
     * @return The distance in meters
     */
    private fun haversineDistance(point1: LatLng, point2: LatLng): Double {
        val earthRadius = 6_371_000.0 // meters

        val lat1Rad = Math.toRadians(point1.latitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val deltaLat = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLon = Math.toRadians(point2.longitude - point1.longitude)

        val a = kotlin.math.sin(deltaLat / 2) * kotlin.math.sin(deltaLat / 2) +
                kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) *
                kotlin.math.sin(deltaLon / 2) * kotlin.math.sin(deltaLon / 2)

        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Searches for a tower with the given parameters and centers the map on the result.
     */
    fun searchForTower(mcc: Int, mnc: Int, area: Int, cid: Long) {
        viewModelScope.launch {
            // Clear any previous search result
            _searchedTower.value = null
            _searchedTowerCoverage.value = null

            _isSearchInProgress.value = true
            _searchError.value = null

            try {
                val response = nsApi.searchTowers(mcc, mnc, area, cid)

                // Check for 204 No Content specifically
                if (response.code() == 204) {
                    _searchError.value = "No towers found with the specified parameters"
                    _searchedTower.value = null
                    _searchedTowerCoverage.value = null
                } else if (response.isSuccessful && response.body() != null) {
                    val towerResponse = response.body()!!

                    if (towerResponse.cells.isNotEmpty()) {
                        val tower = towerResponse.cells.first()
                        _searchedTower.value = tower

                        // Set coverage data if tower has range
                        if (tower.range > 0) {
                            _searchedTowerCoverage.value = ServingCellCoverageData(
                                subscriptionId = -999, // Use a special ID for search results
                                center = LatLng(tower.lat, tower.lon),
                                radiusMeters = tower.range
                            )
                        } else {
                            _searchedTowerCoverage.value = null
                        }

                        // Center map on the search result and ensure coverage area is visible
                        mapLibreMap?.let { map ->
                            val target = LatLng(tower.lat, tower.lon)

                            if (tower.range > 0) {
                                // Calculate bounds that encompass the coverage circle
                                val radiusInDegrees = tower.range / 111320.0
                                val latRadians = Math.toRadians(tower.lat)
                                val lngOffset = radiusInDegrees / kotlin.math.cos(latRadians)

                                val bounds = LatLngBounds.Builder()
                                    .include(
                                        LatLng(
                                            tower.lat + radiusInDegrees,
                                            tower.lon + lngOffset
                                        )
                                    ) // NE
                                    .include(
                                        LatLng(
                                            tower.lat - radiusInDegrees,
                                            tower.lon - lngOffset
                                        )
                                    ) // SW
                                    .build()

                                // Ensure we animate on the main thread with padding to show full circle
                                Handler(Looper.getMainLooper()).post {
                                    // Use padding to ensure the circle edge is visible
                                    val padding = 100 // pixels
                                    map.animateCamera(
                                        CameraUpdateFactory.newLatLngBounds(bounds, padding)
                                    )
                                }
                            } else {
                                // No range info, just center with default zoom
                                val camPos = CameraPosition.Builder()
                                    .target(target)
                                    .zoom(SEARCH_RESULT_ZOOM)
                                    .build()

                                // Ensure we animate on the main thread
                                Handler(Looper.getMainLooper()).post {
                                    map.animateCamera(CameraUpdateFactory.newCameraPosition(camPos))
                                }
                            }
                        }

                        if (towerResponse.cells.size > 1) {
                            Timber.d("Search returned ${towerResponse.cells.size} towers, using first result")
                        }
                    } else {
                        _searchError.value = "No towers found with the specified values"
                        _searchedTower.value = null
                        _searchedTowerCoverage.value = null
                    }
                } else {
                    _searchError.value = "Error searching for towers. Please try again."
                    _searchedTower.value = null
                    _searchedTowerCoverage.value = null
                    Timber.e("Tower search failed with response code: ${response.code()}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error searching for tower")
                _searchError.value = "Error searching for tower: ${e.message}"
                _searchedTower.value = null
                _searchedTowerCoverage.value = null
            } finally {
                _isSearchInProgress.value = false
            }
        }
    }

    /**
     * Clears the current search result.
     */
    fun clearSearchResult() {
        _searchedTower.value = null
        _searchedTowerCoverage.value = null
        _searchError.value = null
    }

    /**
     * Updates the search MCC input value.
     */
    fun updateSearchMcc(value: String) {
        _searchMccInput.value = value
        // Clear error when user starts typing
        if (_searchError.value != null) {
            _searchError.value = null
        }
    }

    /**
     * Updates the search MNC input value.
     */
    fun updateSearchMnc(value: String) {
        _searchMncInput.value = value
        // Clear error when user starts typing
        if (_searchError.value != null) {
            _searchError.value = null
        }
    }

    /**
     * Updates the search Area input value.
     */
    fun updateSearchArea(value: String) {
        _searchAreaInput.value = value
        // Clear error when user starts typing
        if (_searchError.value != null) {
            _searchError.value = null
        }
    }

    /**
     * Updates the search CID input value.
     */
    fun updateSearchCid(value: String) {
        _searchCidInput.value = value
        // Clear error when user starts typing
        if (_searchError.value != null) {
            _searchError.value = null
        }
    }

    /**
     * Clears all search input fields.
     */
    fun clearSearchInputs() {
        _searchMccInput.value = ""
        _searchMncInput.value = ""
        _searchAreaInput.value = ""
        _searchCidInput.value = ""
        _searchError.value = null
    }

    override fun onCleared() {
        super.onCleared()

        // Clear map references to prevent memory leaks
        mapLibreMap = null
        mapView = null

        // Clear location reference
        myLocation = null

        // Clear any stored layer IDs
        beaconDbLayerIds = emptyList()
    }
}

data class TowerWrapper(val tower: Tower) {
    internal val towerId: String = CellularUtils.getTowerId(tower)
}

