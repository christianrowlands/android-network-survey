package com.craxiom.networksurvey.ui.wifi


import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.AChartDetailsViewModel

const val MAX_WIFI_RSSI = -20f
const val MIN_WIFI_RSSI = -100f

/**
 * The view model for the Wifi Details screen.
 */
internal class WifiDetailsViewModel : AChartDetailsViewModel() {

    lateinit var wifiNetwork: WifiNetwork

    override fun getMaxRssi(): Float {
        return MAX_WIFI_RSSI
    }

    override fun getMinRssi(): Float {
        return MIN_WIFI_RSSI
    }
}