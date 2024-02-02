package com.craxiom.networksurvey.ui.wifi.model


import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.ASignalChartViewModel

const val MAX_WIFI_RSSI = -20f
const val MIN_WIFI_RSSI = -100f

/**
 * The view model for the Wifi Details screen.
 */
internal class WifiDetailsViewModel : ASignalChartViewModel() {

    lateinit var wifiNetwork: WifiNetwork

    init {
        setMaxRssi(MAX_WIFI_RSSI)
        setMinRssi(MIN_WIFI_RSSI)
    }
}