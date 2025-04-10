package com.craxiom.networksurvey.ui.cellular.model

import android.content.Context
import android.graphics.DashPathEffect
import android.location.Location
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.Plmn
import com.craxiom.networksurvey.ui.ASignalChartViewModel
import com.craxiom.networksurvey.ui.cellular.Tower
import com.craxiom.networksurvey.ui.cellular.TowerResponse
import com.craxiom.networksurvey.ui.cellular.nsApi
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import retrofit2.Response
import timber.log.Timber
import java.util.Collections
import java.util.Objects

private const val MAX_TOWERS_ON_MAP = 5000

/**
 * The view model for the Tower Map screen.
 */
internal class TowerMapViewModel : ASignalChartViewModel() {

    private var _paddingInsets = MutableStateFlow(PaddingValues(0.dp, 0.dp, 0.dp, 0.dp))
    val paddingInsets = _paddingInsets.asStateFlow()

    private var _servingCells =
        MutableStateFlow<HashMap<Int, ServingCellInfo>>(HashMap()) // <SubscriptionId, ServingCellInfo>
    val servingCells = _servingCells.asStateFlow()

    val subIdToServingCellLocations = HashMap<Int, GeoPointRange>()
    var myLocation: Location? = null

    private var _servingSignals =
        MutableStateFlow<HashMap<Int, ServingSignalInfo>>(HashMap()) // <SubscriptionId, ServingSignalInfo>
    val servingSignals = _servingSignals.asStateFlow()

    var mapView: MapView? = null
    lateinit var gpsMyLocationProvider: GpsMyLocationProvider
    lateinit var followMyLocationChangeListener: FollowMyLocationChangeListener
    private var hasMapLocationBeenSet = false

    var myLocationOverlay: CustomLocationOverlay? = null

    private lateinit var towerOverlayGroup: RadiusMarkerClusterer
    private var servingCellLinesOverlayGroup: FolderOverlay = FolderOverlay()
    private var servingCellCoverageOverlayGroup: FolderOverlay = FolderOverlay()

    private val _towers = MutableStateFlow(LinkedHashSet<TowerMarker>(LinkedHashSet()))
    val towers = _towers.asStateFlow()

    private val _noTowersFound = MutableStateFlow(false)
    val noTowersFound = _noTowersFound.asStateFlow()

    private val _selectedRadioType = MutableStateFlow(CellularProtocol.LTE.name)
    val selectedRadioType = _selectedRadioType.asStateFlow()

    private val _plmnFilter = MutableStateFlow(Plmn(0, 0))
    val plmnFilter = _plmnFilter.asStateFlow()

    private val _selectedSource = MutableStateFlow(TowerSource.OpenCelliD)
    val selectedSource = _selectedSource.asStateFlow()

    private val _isLoadingInProgress = MutableStateFlow(true)
    val isLoadingInProgress = _isLoadingInProgress.asStateFlow()

    private val _isZoomedOutTooFar = MutableStateFlow(false)
    val isZoomedOutTooFar = _isZoomedOutTooFar.asStateFlow()

    private val _lastQueriedBounds = MutableStateFlow<BoundingBox?>(null)
    val lastQueriedBounds = _lastQueriedBounds.asStateFlow()

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

    fun setLastQueriedBounds(bounds: BoundingBox) {
        _lastQueriedBounds.value = bounds
    }

    @Synchronized
    fun initMapView(mapView: MapView) {
        this.mapView = mapView

        // Since the mapView has access to the overlays, whenever onDetach is called on MapView
        // (e.g. the user navigates away from the screen), the overlays will have onDetach called,
        // which sets the mOverlayManager to null. This causes a NPE when trying to add items
        // to the overlays when navigating back to the screen. So we need to reinitialize the
        // overlays here to prevent the NPE.
        gpsMyLocationProvider = GpsMyLocationProvider(mapView.context)
        towerOverlayGroup = RadiusMarkerClusterer(mapView.context)
        servingCellLinesOverlayGroup = FolderOverlay()
        servingCellCoverageOverlayGroup = FolderOverlay()

        towerOverlayGroup.setMaxClusteringZoomLevel(14)

        mapView.overlays.add(towerOverlayGroup)
        mapView.overlays.add(servingCellLinesOverlayGroup)
        mapView.overlays.add(servingCellCoverageOverlayGroup)

        addDefaultOverlays(mapView.context, mapView)

        PreferenceUtils.getBoundingBoxFromPreferences(mapView.context)?.let {
            mapView.zoomToBoundingBox(it, false)
        }
    }

    /**
     * Adds the default overlays to the map view such as the my location overlay.
     */
    private fun addDefaultOverlays(
        context: Context,
        mapView: MapView
    ) {
        val locationConsumer = IMyLocationConsumer { location, _ ->
            myLocation = location
            drawServingCellLine()
        }

        myLocationOverlay =
            CustomLocationOverlay(
                gpsMyLocationProvider,
                mapView,
                locationConsumer,
                followMyLocationChangeListener
            )
        val locationOverlay: MyLocationNewOverlay = myLocationOverlay!!

        val icon = AppCompatResources.getDrawable(context, R.drawable.ic_location_pin)?.toBitmap()
        if (icon != null) {
            locationOverlay.setPersonIcon(icon)
            locationOverlay.setPersonAnchor(0.5f, .8725f)
        }

        val directionIcon =
            AppCompatResources.getDrawable(context, R.drawable.ic_navigation)?.toBitmap()
        if (icon != null) {
            locationOverlay.setDirectionIcon(directionIcon)
            locationOverlay.setDirectionAnchor(0.5f, 0.5f)
        }

        locationOverlay.enableMyLocation()
        mapView.overlays.add(locationOverlay)
    }

    /**
     * Sets the center of the map to the provided location.
     */
    fun setMapCenterLocation(location: Location?): Boolean {
        if (location != null && !hasMapLocationBeenSet) {
            if (location.latitude != 0.0 || location.longitude != 0.0) {
                hasMapLocationBeenSet = true
                mapView!!.controller.setCenter(GeoPoint(location.latitude, location.longitude))
            }
        }

        return hasMapLocationBeenSet
    }

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
            towerOverlayGroup.items?.clear()
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
            mapView.postInvalidate()
        } catch (e: Exception) {
            Timber.e(e, "Something went wrong while recreating the overlays on the map")
        }
    }

    /**
     * Draws a line between the current location and all the serving cell locations.
     */
    @Synchronized
    fun drawServingCellLine() {
        try {
            servingCellLinesOverlayGroup.items?.clear()

            val currentLocation = myLocation ?: return

            if (subIdToServingCellLocations.isEmpty()) return

            val myGeoPoint = GeoPoint(currentLocation.latitude, currentLocation.longitude)

            subIdToServingCellLocations.forEach { (_, geoPointRange) ->
                val polyline = Polyline()
                polyline.outlinePaint.strokeWidth = 4f
                polyline.outlinePaint.setPathEffect(DashPathEffect(floatArrayOf(10f, 20f), 0f))
                servingCellLinesOverlayGroup.add(polyline)

                val pathPoints = ArrayList<GeoPoint>()
                pathPoints.add(myGeoPoint)
                pathPoints.add(geoPointRange.geoPoint)
                polyline.setPoints(pathPoints)
            }
        } catch (e: Exception) {
            // Sometimes the servingCellLinesOverlayGroup will throw a NPE on the #add call because
            // the mOverlayManager is assigned null on cleanup
            Timber.e(e, "Something went wrong while drawing the serving cell lines on the map")
        }
    }

    @Synchronized
    fun drawServingCellCoverage() {
        try {
            servingCellCoverageOverlayGroup.items?.clear()

            if (subIdToServingCellLocations.isEmpty()) return

            PreferenceUtils.displayServingCellCoverageOnMap(mapView?.context)
                .let { displayCoverage ->
                    if (!displayCoverage) return
                }

            subIdToServingCellLocations.forEach { (_, geoPointRange) ->
                if (geoPointRange.range > 0) {
                    val coverageArea =
                        CoverageAreaOverlay(geoPointRange.geoPoint, geoPointRange.range)
                    servingCellCoverageOverlayGroup.add(coverageArea)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Something went wrong while drawing the serving cell coverage on the map")
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
     * Moves the map view to the user's current location.
     */
    fun goToMyLocation() {
        val lastKnownLocation = gpsMyLocationProvider.lastKnownLocation
        if (lastKnownLocation == null) {
            Timber.w("The last known location is null")
            return
        }
        mapView!!.controller.animateTo(
            GeoPoint(
                lastKnownLocation.latitude,
                lastKnownLocation.longitude
            )
        )
    }

    /**
     * Runs the tower query to get the towers from the back end for the current map view.
     */
    suspend fun runTowerQuery() {
        val mapViewLocal = mapView
        if (mapViewLocal == null) {
            Timber.w("The map view is null")
            return
        }

        setIsLoadingInProgress(true)

        Timber.i("Running the towerQuery")

        val towerPoints = getTowersFromServer()
        Timber.d("Loaded ${towerPoints.size} towers")

        val towers = towers.value

        towerPoints.forEach {
            val towerMarker = TowerMarker(mapViewLocal, it)

            if (towers.size >= MAX_TOWERS_ON_MAP) {
                val towerToRemove = towers.first()
                towers.remove(towerToRemove)
                towerToRemove.destroy()
            }

            if (towers.contains(towerMarker)) {
                towers.remove(towerMarker)
            }

            towers.add(towerMarker)
        }

        setNoTowersFound(towers.isEmpty())

        setIsLoadingInProgress(false)
    }

    /**
     * Loads the towers from the NS backend for the given bounding box.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getTowersFromServer(): List<Tower> {
        return suspendCancellableCoroutine { continuation ->
            try {
                viewModelScope.launch {
                    try {
                        val bounds = lastQueriedBounds.value ?: return@launch

                        // Format the bounding box coordinates to the required "bbox" string format
                        val bbox =
                            "${bounds.latSouth},${bounds.lonWest},${bounds.latNorth},${bounds.lonEast}"

                        val response: Response<TowerResponse>
                        if (plmnFilter.value.isSet()) {
                            val plmn = plmnFilter.value
                            response = nsApi.getTowers(
                                bbox,
                                selectedRadioType.value,
                                plmn.mcc,
                                plmn.mnc,
                                selectedSource.value.apiName
                            )
                        } else {
                            response = nsApi.getTowers(
                                bbox,
                                selectedRadioType.value,
                                selectedSource.value.apiName
                            )
                        }

                        // Process the response
                        if (response.code() == 204) {
                            // No towers found, return an empty list
                            Timber.w("No towers found; raw: ${response.raw()}")
                            continuation.resume(Collections.emptyList(), onCancellation = null)
                            Collections.emptyList<GeoPoint>()
                        } else if (response.isSuccessful && response.body() != null) {
                            Timber.i("Successfully loaded towers")
                            val towerData = response.body()!!

                            continuation.resume(towerData.cells, onCancellation = {
                                Timber.e("The tower data fetch was cancelled")
                            })
                        } else {
                            Timber.w("Failed to load towers; raw: ${response.raw()}")
                            continuation.resume(Collections.emptyList(), onCancellation = null)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to fetch towers")
                        // TODO Display a toast
                        continuation.resume(Collections.emptyList(), onCancellation = null)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch towers")
                continuation.resume(Collections.emptyList(), onCancellation = null)
            }
        }
    }
}
