package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.networksurvey.fragments.WifiNetworkInfo
import com.patrykandpatrick.vico.core.model.LineCartesianLayerModel

/**
 * The specific Wi-Fi spectrum view model implementation for the 5 GHz Group 1 chart.
 */
class WifiSpectrum5Group1ViewModel : AWifiSpectrumChartViewModel() {
    override fun filterWifiNetworks(wifiNetworkInfoList: List<WifiNetworkInfo>): List<WifiNetworkInfo> {
        return wifiNetworkInfoList.filter { it.channel.toFloat() in CHANNELS_5_GHZ_GROUP_1 }
    }

    override fun createSeriesModel(wifiNetworkInfoList: List<WifiNetworkInfo>): LineCartesianLayerModel.Partial {
        return if (wifiNetworkInfoList.isEmpty()) {
            LineCartesianLayerModel.partial {
                series(
                    CHANNELS_5_GHZ_GROUP_1_CHART_VIEW,
                    List(CHANNELS_5_GHZ_GROUP_1_CHART_VIEW.size) { WIFI_CHART_MIN })
            }
        } else {
            createSeriesForNetworks(wifiNetworkInfoList)
        }
    }

    override fun clearChart() {
        modelProducer.tryRunTransaction {
            add(LineCartesianLayerModel.partial {
                series(
                    CHANNELS_5_GHZ_GROUP_1_CHART_VIEW,
                    List(CHANNELS_5_GHZ_GROUP_1_CHART_VIEW.size) { WIFI_CHART_MIN })
            })
        }
    }
}