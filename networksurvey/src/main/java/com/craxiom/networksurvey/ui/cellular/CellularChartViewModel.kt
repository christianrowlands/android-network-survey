package com.craxiom.networksurvey.ui.cellular

import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.ui.ASignalChartViewModel

private const val INITIAL_CELL_ID = -1

/**
 * The view model for the Cellular Signals Chart.
 */
class CellularChartViewModel : ASignalChartViewModel() {

    private var cellularProtocol = CellularProtocol.NONE
    private var servingCellId = INITIAL_CELL_ID

    override fun getMarkerLabel(): Int = R.string.chart_serving_cell_change

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
     * The chart outlives the screen (one per SIM, kept by the Cellular view model), so it remembers
     * the last cell itself; returning to the screen therefore never adds a marker of its own.
     */
    fun setServingCellId(cellId: Int) {
        if (servingCellId == cellId) return
        if (servingCellId != INITIAL_CELL_ID) addMarker()
        servingCellId = cellId
    }
}