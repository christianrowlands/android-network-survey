package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.networksurvey.fragments.WifiNetworkInfo
import com.craxiom.networksurvey.util.WifiUtils.START_OF_6_GHZ_RANGE
import com.patrykandpatrick.vico.core.model.LineCartesianLayerModel

/**
 * The specific Wi-Fi spectrum view model implementation for the 2.4 GHz chart.
 */
class WifiSpectrum24ViewModel : AWifiSpectrumChartViewModel() {
    override fun filterWifiNetworks(wifiNetworkInfoList: List<WifiNetworkInfo>): List<WifiNetworkInfo> {
        return wifiNetworkInfoList.filter {
            it.frequency < START_OF_6_GHZ_RANGE && it.channel.toFloat() in CHANNELS_2_4_GHZ
        }
    }

    override fun createSeriesModel(wifiNetworkInfoList: List<WifiNetworkInfo>): LineCartesianLayerModel.Partial {
        return if (wifiNetworkInfoList.isEmpty()) {
            LineCartesianLayerModel.partial {
                series(
                    CHANNELS_2_4_GHZ_CHART_VIEW,
                    List(CHANNELS_2_4_GHZ_CHART_VIEW.size) { WIFI_CHART_MIN })
            }
        } else {
            createSeriesForNetworks(wifiNetworkInfoList)
        }
    }

    override fun clearChart() {
        modelProducer.tryRunTransaction {
            add(LineCartesianLayerModel.partial {
                series(
                    CHANNELS_2_4_GHZ_CHART_VIEW,
                    List(CHANNELS_2_4_GHZ_CHART_VIEW.size) { WIFI_CHART_MIN })
            })
        }
    }
}