package com.craxiom.networksurvey.ui.wifi.model

import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.fragments.WifiNetworkInfo
import com.craxiom.networksurvey.util.WifiUtils
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlinx.coroutines.launch

/**
 * The specific Wi-Fi spectrum view model implementation for the 5 GHz Group 3 chart.
 */
class WifiSpectrum5Group3ViewModel : AWifiSpectrumChartViewModel() {
    override fun filterWifiNetworks(wifiNetworkInfoList: List<WifiNetworkInfo>): List<WifiNetworkInfo> {
        return wifiNetworkInfoList.filter {
            it.frequency < WifiUtils.START_OF_6_GHZ_RANGE && it.channel.toFloat() in CHANNELS_5_GHZ_GROUP_3
        }
    }

    override fun createSeriesData(wifiNetworkInfoList: List<WifiNetworkInfo>): List<Pair<List<Number>, List<Number>>> {
        return if (wifiNetworkInfoList.isEmpty()) {
            listOf(
                CHANNELS_5_GHZ_GROUP_3_CHART_VIEW to List(CHANNELS_5_GHZ_GROUP_3_CHART_VIEW.size) { WIFI_CHART_MIN }
            )
        } else {
            createSeriesForNetworks(wifiNetworkInfoList)
        }
    }

    override fun clearChart() {
        viewModelScope.launch {
            modelProducer.runTransaction {
                lineSeries {
                    series(
                        CHANNELS_5_GHZ_GROUP_3_CHART_VIEW,
                        List(CHANNELS_5_GHZ_GROUP_3_CHART_VIEW.size) { WIFI_CHART_MIN }
                    )
                }
            }
        }
    }
}