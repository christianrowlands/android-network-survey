package com.craxiom.networksurvey.ui.cellular.model


import com.craxiom.networksurvey.ui.ASignalChartViewModel

/**
 * The view model for the Tower Map screen.
 */
internal class TowerMapViewModel : ASignalChartViewModel() {

    lateinit var servingCellInfo: ServingCellInfo
}