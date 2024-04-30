package com.craxiom.networksurvey.ui.cellular.model


import com.craxiom.networksurvey.ui.ASignalChartViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.BoundingBox

/**
 * The view model for the Tower Map screen.
 */
internal class TowerMapViewModel : ASignalChartViewModel() {

    lateinit var servingCellInfo: ServingCellInfo

    private val _isLoadingInProgress = MutableStateFlow(true)
    val isLoadingInProgress = _isLoadingInProgress.asStateFlow()

    private val _isZoomedOutTooFar = MutableStateFlow(false)
    val isZoomedOutTooFar = _isZoomedOutTooFar.asStateFlow()

    private val _lastQueriedBounds = MutableStateFlow<BoundingBox?>(null)
    val lastQueriedBounds = _lastQueriedBounds.asStateFlow()

    fun setIsLoadingInProgress(isLoading: Boolean) {
        _isLoadingInProgress.value = isLoading
    }

    fun setIsZoomedOutTooFar(isZoomedOut: Boolean) {
        _isZoomedOutTooFar.value = isZoomedOut
    }

    fun setLastQueriedBounds(bounds: BoundingBox) {
        _lastQueriedBounds.value = bounds
    }
}