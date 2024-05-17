package com.craxiom.networksurvey.ui.cellular.model


import com.craxiom.networksurvey.ui.ASignalChartViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * The view model for the Tower Map screen.
 */
internal class TowerMapViewModel : ASignalChartViewModel() {

    lateinit var servingCellInfo: ServingCellInfo
    lateinit var mapView: MapView

    private val _towers = MutableStateFlow(LinkedHashSet<Marker>(LinkedHashSet()))
    val towers = _towers.asStateFlow()

    private val _selectedRadioType = MutableStateFlow("LTE")
    val selectedRadioType = _selectedRadioType.asStateFlow()

    private val _isLoadingInProgress = MutableStateFlow(true)
    val isLoadingInProgress = _isLoadingInProgress.asStateFlow()

    private val _isZoomedOutTooFar = MutableStateFlow(false)
    val isZoomedOutTooFar = _isZoomedOutTooFar.asStateFlow()

    private val _lastQueriedBounds = MutableStateFlow<BoundingBox?>(null)
    val lastQueriedBounds = _lastQueriedBounds.asStateFlow()

    private val _mapZoomLevel = MutableStateFlow(0.0)
    val mapZoomLevel = _mapZoomLevel.asStateFlow()

    fun setTowers(towers: LinkedHashSet<Marker>) {
        _towers.value = towers
    }

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
}