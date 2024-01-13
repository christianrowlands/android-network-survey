package com.craxiom.networksurvey.ui.cellular


import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.ui.ASignalChartViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val INITIAL_CELL_ID = -1

/**
 * The view model for the Cellular Signals Chart.
 */
class CellularChartViewModel : ASignalChartViewModel() {

    private val _chartTitle = MutableStateFlow("RSSI")
    val chartTitle = _chartTitle.asStateFlow()

    private var cellularProtocol = CellularProtocol.NONE
    private var servingCellId = INITIAL_CELL_ID

    override fun getMarkerLabel(): String {
        return "Serving Cell Change"
    }

    fun setChartTitle(title: String) {
        _chartTitle.value = title
    }

    /**
     * Sets the cellular protocol to use for the chart. If the protocol is already set to the given
     * protocol, then this method does nothing. Otherwise, it clears the chart. This is useful when
     * switching between LTE and NR, for example.
     */
    fun setCellularProtocol(protocol: CellularProtocol) {
        if (cellularProtocol == protocol) return
        cellularProtocol = protocol
        clearChart()
    }

    /**
     * Sets the serving cell ID to use for the chart. If the serving cell ID is already set to the
     * given ID, then this method does nothing. Otherwise, it adds a marker to the chart indicating
     * a serving cell change occurred.
     *
     * The reason we are handling adding the marker here is because in the observer of
     * {@link CellularViewModel#getCellId} in {@link NetworkDetailsFragment} is notified when the
     * observer is added, which happens when the fragment is created. This means that a serving cell
     * change marker is added every time the fragment is switched to, which is not what we want.
     */
    fun setServingCellId(cellId: Int) {
        if (servingCellId == cellId) return
        if (servingCellId != INITIAL_CELL_ID) addMarker()
        servingCellId = cellId
    }
}