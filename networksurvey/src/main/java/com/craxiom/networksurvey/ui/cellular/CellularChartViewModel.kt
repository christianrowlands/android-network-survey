package com.craxiom.networksurvey.ui.cellular


import com.craxiom.networksurvey.ui.ASignalChartViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The view model for the Cellular Signals Chart.
 */
class CellularChartViewModel : ASignalChartViewModel() {

    private val _chartTitle = MutableStateFlow("RSSI")
    val chartTitle = _chartTitle.asStateFlow()

    fun setChartTitle(title: String) {
        _chartTitle.value = title
    }
}