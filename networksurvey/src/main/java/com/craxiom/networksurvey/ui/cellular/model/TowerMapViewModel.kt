package com.craxiom.networksurvey.ui.cellular.model

import android.location.Location
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.ui.ASignalChartViewModel
import com.craxiom.networksurvey.util.CellularUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker

/**
 * The view model for the Tower Map screen.
 */
internal class TowerMapViewModel : ASignalChartViewModel() {

    lateinit var servingCellInfo: ServingCellInfo
    lateinit var mapView: MapView
    private var hasMapLocationBeenSet = false

    var towerOverlayGroup: FolderOverlay? = FolderOverlay()

    private val _towers = MutableStateFlow(LinkedHashSet<Marker>(LinkedHashSet()))
    val towers = _towers.asStateFlow()

    private val _selectedRadioType = MutableStateFlow(CellularProtocol.LTE.name)
    val selectedRadioType = _selectedRadioType.asStateFlow()

    private val _isLoadingInProgress = MutableStateFlow(true)
    val isLoadingInProgress = _isLoadingInProgress.asStateFlow()

    private val _isZoomedOutTooFar = MutableStateFlow(false)
    val isZoomedOutTooFar = _isZoomedOutTooFar.asStateFlow()

    private val _lastQueriedBounds = MutableStateFlow<BoundingBox?>(null)
    val lastQueriedBounds = _lastQueriedBounds.asStateFlow()

    private val _mapZoomLevel = MutableStateFlow(0.0)
    val mapZoomLevel = _mapZoomLevel.asStateFlow()

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    fun setSelectedRadioType(radioType: String) {
        _selectedRadioType.value = radioType
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

    fun setMapZoomLevel(zoomLevel: Double) {
        _mapZoomLevel.value = zoomLevel
    }

    fun updateLocation(location: Location?): Boolean {
        if (location != null && !hasMapLocationBeenSet) {
            if (location.latitude != 0.0 || location.longitude != 0.0) {
                hasMapLocationBeenSet = true
                mapView.controller.setCenter(GeoPoint(location.latitude, location.longitude))
            }
        }

        _currentLocation.value = location
        return hasMapLocationBeenSet
    }

    fun onCellularBatchResults(
        cellularBatchResults: MutableList<CellularRecordWrapper>?,
        subscriptionId: Int
    ) {
        if (cellularBatchResults.isNullOrEmpty()) return

        // TODO Update to support multiple SIMs
        val servingCellRecord =
            cellularBatchResults.firstOrNull {
                if (it != null) {
                    CellularUtils.isServingCell(it.cellularRecord)
                } else {
                    false
                }
            }
                ?: return

        servingCellInfo = ServingCellInfo(servingCellRecord, subscriptionId)
    }
}
