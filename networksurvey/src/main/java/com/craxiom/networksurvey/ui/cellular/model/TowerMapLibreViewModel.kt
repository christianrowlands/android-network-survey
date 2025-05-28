package com.craxiom.networksurvey.ui.cellular.model

import android.location.Location
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.Plmn
import com.craxiom.networksurvey.ui.cellular.Tower
import com.craxiom.networksurvey.ui.cellular.TowerResponse
import com.craxiom.networksurvey.ui.cellular.nsApi
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

private const val INITIAL_ZOOM = 15.0
private const val MIN_ZOOM_LEVEL = 13.0
private const val MAX_AREA_SQ_METERS = 400_000_000.0
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
    private val _towers = MutableStateFlow(LinkedHashSet<TowerMarkerStub>(LinkedHashSet()))
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
    private var style: Style? = null

    fun setPaddingInsets(paddingValues: PaddingValues) {
        _paddingInsets.value = paddingValues
    }

    fun setNoTowersFound(noTowersFound: Boolean) {
        _noTowersFound.value = noTowersFound
    }

    fun setSelectedRadioType(radioType: String) {
        _selectedRadioType.value = radioType
    }

    fun setPlmnFilter(plmn: Plmn) {
        _plmnFilter.value = plmn
    }

    fun setTowerSource(towerSource: TowerSource) {
        _selectedSource.value = towerSource
    }

    fun setIsLoadingInProgress(isLoading: Boolean) {
        _isLoadingInProgress.value = isLoading
    }

    fun setIsZoomedOutTooFar(isZoomedOut: Boolean) {
        _isZoomedOutTooFar.value = isZoomedOut
    }

    fun setLastQueriedBounds(bounds: LatLngBounds) {
        _lastQueriedBounds.value = bounds
    }

    /**
     * Call this from your Composable’s onMapReady.
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
                    .zoom(INITIAL_ZOOM)
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
            val target = LatLng(location.latitude, location.longitude)
            val camPos = CameraPosition.Builder()
                .target(target)
                .zoom(INITIAL_ZOOM)
                .build()
            mapLibreMap!!.animateCamera(CameraUpdateFactory.newCameraPosition(camPos))
            hasCenteredLocation = true
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

        mapView?.let { mapView ->
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

    /**
     * Fetch towers via your existing API, using the current LatLngBounds.
     */
    internal suspend fun runTowerQuery() {
        val map = mapLibreMap ?: return
        _isLoadingInProgress.value = true

        val b = map.projection.visibleRegion.latLngBounds
        // Format: "south,west,north,east"
        val bboxParam = listOf(
            b.latitudeSouth,
            b.longitudeWest,
            b.latitudeNorth,
            b.longitudeEast
        ).joinToString(",")

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
            _isLoadingInProgress.value = false
            return
        }

        if (response.code() == 204 || !response.isSuccessful || response.body() == null) {
            _towers.value.clear()
        } else {
            val newCells = response.body()!!.cells
            // TODO: replace this stub with real MapLibre PointAnnotations
            _towers.value = LinkedHashSet(newCells.map { TowerMarkerStub(it) })
        }

        _noTowersFound.value = _towers.value.isEmpty()
        _isLoadingInProgress.value = false
    }

    /**
     * Approximate area of the LatLngBounds in m² (using haversine).
     */
    private fun calculateArea(bounds: LatLngBounds): Double {
        val sw = bounds.southWest
        val ne = bounds.northEast
        val earthR = 6_371_000.0

        val dLat = Math.toRadians(ne.latitude - sw.latitude)
        val dLon = Math.toRadians(ne.longitude - sw.longitude)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(sw.latitude)) *
                kotlin.math.cos(Math.toRadians(ne.latitude)) *
                kotlin.math.sin(dLon / 2).let { it * it }
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        val width = earthR * c
        return width * width
    }
}

/**
 * Temporary placeholder for your TowerMarker until you re-implement it
 * with the MapLibre Annotation API.
 */
data class TowerMarkerStub(val tower: Tower)
