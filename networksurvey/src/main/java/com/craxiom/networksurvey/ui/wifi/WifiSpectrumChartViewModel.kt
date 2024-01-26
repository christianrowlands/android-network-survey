package com.craxiom.networksurvey.ui.wifi


import androidx.lifecycle.ViewModel
import com.craxiom.networksurvey.fragments.WifiNetworkInfo
import com.patrykandpatrick.vico.core.model.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.model.LineCartesianLayerModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

// Subtracting 10 from the max and min so that the chart shows spectrum usage a bit better
const val WIFI_SPECTRUM_MAX = MAX_WIFI_RSSI - 10
const val WIFI_SPECTRUM_MIN = MIN_WIFI_RSSI - 10

// The offset for the signal strength values for the channels to the left and right
private const val WEAKER_SIGNAL_OFFSET = 5

const val WIFI_CHART_MIN = WIFI_SPECTRUM_MIN - 10

private val CHANNELS_2_4_GHZ = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)
private val CHANNELS_2_4_GHZ_CHART_VIEW =
    listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

private val CHANNELS_5_GHZ_GROUP_1 = listOf(36, 40, 44, 48, 52, 56, 60, 64)
private val CHANNELS_5_GHZ_GROUP_1_CHART_VIEW = listOf(32, 36, 40, 44, 48, 52, 56, 60, 64, 68)

private val CHANNELS_5_GHZ_GROUP_2 =
    listOf(100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144)
private val CHANNELS_5_GHZ_GROUP_2_CHART_VIEW =
    listOf(96, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 148)

val CHANNELS_5_GHZ_GROUP_3 = listOf(149f, 153f, 157f, 161f, 165f, 169f, 173f, 177f)
private val CHANNELS_5_GHZ_GROUP_3_CHART_VIEW =
    listOf(145, 149, 153, 157, 161, 165, 169, 173, 177, 181)

/**
 * Abstract base class for the view model for a signal chart.
 */
class WifiSpectrumChartViewModel : ViewModel() {

    internal val modelProducer2Point4Ghz = CartesianChartModelProducer.build()
    internal val modelProducer5GhzGroup1 = CartesianChartModelProducer.build()
    internal val modelProducer5GhzGroup2 = CartesianChartModelProducer.build()
    internal val modelProducer5GhzGroup3 = CartesianChartModelProducer.build()

    private val _scanRateSeconds = MutableStateFlow(-1)
    val scanRate = _scanRateSeconds.asStateFlow()

    private val _wifiNetworkInfoList = MutableStateFlow<List<WifiNetworkInfo>>(emptyList())
    val wifiNetworkInfoList: StateFlow<List<WifiNetworkInfo>> = _wifiNetworkInfoList

    fun initializeCharts() {
        if (_wifiNetworkInfoList.value.isEmpty()) {
            Timber.i("No valid wifi records found in the wifi scan results")
            clearCharts()
            return
        } else {
            onWifiScanResults(_wifiNetworkInfoList.value)
        }
    }

    /**
     * Sets the scan rate in seconds.
     */
    fun setScanRateSeconds(scanRateSeconds: Int) {
        _scanRateSeconds.value = scanRateSeconds
    }

    /**
     * Called when a new Wifi scan result is received. Updates the chart with the results
     */
    fun onWifiScanResults(wifiNetworkInfoList: List<WifiNetworkInfo>) {
        _wifiNetworkInfoList.value = wifiNetworkInfoList
        if (wifiNetworkInfoList.isEmpty()) {
            Timber.i("No valid wifi records found in the wifi scan results")
            clearCharts()
            return
        }

        modelProducer2Point4Ghz.tryRunTransaction {
            add(
                create2Point4SeriesModel(wifiNetworkInfoList = wifiNetworkInfoList)
            )
        }

        modelProducer5GhzGroup1.tryRunTransaction {
            add(
                create5Group1SeriesModel(wifiNetworkInfoList = wifiNetworkInfoList)
            )
        }

        modelProducer5GhzGroup2.tryRunTransaction {
            add(
                create5Group2SeriesModel(wifiNetworkInfoList = wifiNetworkInfoList)
            )
        }

        modelProducer5GhzGroup3.tryRunTransaction {
            add(
                create5Group3SeriesModel(wifiNetworkInfoList = wifiNetworkInfoList)
            )
        }
    }

    private fun create2Point4SeriesModel(wifiNetworkInfoList: List<WifiNetworkInfo>): LineCartesianLayerModel.Partial {
        return LineCartesianLayerModel.partial {
            val matchedWifiNetworks = wifiNetworkInfoList
                .filter { it.channel in CHANNELS_2_4_GHZ }

            if (matchedWifiNetworks.isEmpty()) {
                series(
                    CHANNELS_2_4_GHZ_CHART_VIEW,
                    List(CHANNELS_2_4_GHZ_CHART_VIEW.size) { WIFI_CHART_MIN })
            } else {

                matchedWifiNetworks
                    .forEach { wifiNetwork ->
                        val signalStrengths = CHANNELS_2_4_GHZ_CHART_VIEW.map { channel ->
                            when (channel) {
                                wifiNetwork.channel -> constrictSignalStrength(wifiNetwork.signalStrength.toFloat())
                                wifiNetwork.channel - 1, wifiNetwork.channel + 1 -> (wifiNetwork.signalStrength - WEAKER_SIGNAL_OFFSET).toFloat()
                                else -> WIFI_CHART_MIN
                            }
                        }
                        series(CHANNELS_2_4_GHZ_CHART_VIEW, signalStrengths)
                    }
            }
        }
    }


    private fun create5Group1SeriesModel(wifiNetworkInfoList: List<WifiNetworkInfo>): LineCartesianLayerModel.Partial {
        return LineCartesianLayerModel.partial {
            val matchedWifiNetworks = wifiNetworkInfoList
                .filter { it.channel in CHANNELS_5_GHZ_GROUP_1 }

            if (matchedWifiNetworks.isEmpty()) {
                series(CHANNELS_5_GHZ_GROUP_1_CHART_VIEW, List(
                    CHANNELS_5_GHZ_GROUP_1_CHART_VIEW.size
                ) { WIFI_CHART_MIN })
            } else {
                matchedWifiNetworks
                    .forEach { wifiNetwork ->
                        val signalStrengths = CHANNELS_5_GHZ_GROUP_1_CHART_VIEW.map { channel ->
                            when (channel) {
                                wifiNetwork.channel -> constrictSignalStrength(wifiNetwork.signalStrength.toFloat())
                                wifiNetwork.channel - 1, wifiNetwork.channel + 1 -> (wifiNetwork.signalStrength - WEAKER_SIGNAL_OFFSET).toFloat()
                                else -> WIFI_CHART_MIN
                            }
                        }
                        series(CHANNELS_5_GHZ_GROUP_1_CHART_VIEW, signalStrengths)
                    }
            }
        }
    }

    private fun create5Group2SeriesModel(wifiNetworkInfoList: List<WifiNetworkInfo>): LineCartesianLayerModel.Partial {
        return LineCartesianLayerModel.partial {
            val matchedWifiNetworks = wifiNetworkInfoList
                .filter { it.channel in CHANNELS_5_GHZ_GROUP_2 }

            if (matchedWifiNetworks.isEmpty()) {
                series(CHANNELS_5_GHZ_GROUP_2_CHART_VIEW, List(
                    CHANNELS_5_GHZ_GROUP_2_CHART_VIEW.size
                ) { WIFI_CHART_MIN })
            } else {
                matchedWifiNetworks
                    .forEach { wifiNetwork ->
                        val signalStrengths = CHANNELS_5_GHZ_GROUP_2_CHART_VIEW.map { channel ->
                            when (channel) {
                                wifiNetwork.channel -> constrictSignalStrength(wifiNetwork.signalStrength.toFloat())
                                wifiNetwork.channel - 1, wifiNetwork.channel + 1 -> (wifiNetwork.signalStrength - WEAKER_SIGNAL_OFFSET).toFloat()
                                else -> WIFI_CHART_MIN
                            }
                        }
                        series(CHANNELS_5_GHZ_GROUP_2_CHART_VIEW, signalStrengths)
                    }
            }
        }
    }

    private fun create5Group3SeriesModel(wifiNetworkInfoList: List<WifiNetworkInfo>): LineCartesianLayerModel.Partial {
        return LineCartesianLayerModel.partial {
            val matchedWifiNetworks = wifiNetworkInfoList
                .filter { it.channel.toFloat() in CHANNELS_5_GHZ_GROUP_3 }

            if (matchedWifiNetworks.isEmpty()) {
                series(CHANNELS_5_GHZ_GROUP_3_CHART_VIEW, List(
                    CHANNELS_5_GHZ_GROUP_3_CHART_VIEW.size
                ) { WIFI_CHART_MIN })
            } else {
                matchedWifiNetworks
                    .forEach { wifiNetwork ->
                        val signalStrengths = CHANNELS_5_GHZ_GROUP_3_CHART_VIEW.map { channel ->
                            when (channel) {
                                wifiNetwork.channel -> constrictSignalStrength(wifiNetwork.signalStrength.toFloat())
                                wifiNetwork.channel - 1, wifiNetwork.channel + 1 -> (wifiNetwork.signalStrength - WEAKER_SIGNAL_OFFSET).toFloat()
                                else -> WIFI_CHART_MIN
                            }
                        }
                        series(CHANNELS_5_GHZ_GROUP_3_CHART_VIEW, signalStrengths)
                    }
            }
        }
    }

    private fun clearCharts() {
        modelProducer2Point4Ghz.tryRunTransaction {
            add(LineCartesianLayerModel.partial {
                series(
                    CHANNELS_2_4_GHZ_CHART_VIEW,
                    List(CHANNELS_2_4_GHZ_CHART_VIEW.size) { WIFI_CHART_MIN })
            })
        }

        modelProducer5GhzGroup1.tryRunTransaction {
            add(LineCartesianLayerModel.partial {
                series(CHANNELS_5_GHZ_GROUP_1_CHART_VIEW, List(
                    CHANNELS_5_GHZ_GROUP_1_CHART_VIEW.size
                ) { WIFI_CHART_MIN })
            })
        }

        modelProducer5GhzGroup2.tryRunTransaction {
            add(LineCartesianLayerModel.partial {
                series(CHANNELS_5_GHZ_GROUP_2_CHART_VIEW, List(
                    CHANNELS_5_GHZ_GROUP_2_CHART_VIEW.size
                ) { WIFI_CHART_MIN })
            })
        }

        modelProducer5GhzGroup3.tryRunTransaction {
            add(LineCartesianLayerModel.partial {
                series(CHANNELS_5_GHZ_GROUP_3_CHART_VIEW, List(
                    CHANNELS_5_GHZ_GROUP_3_CHART_VIEW.size
                ) { WIFI_CHART_MIN })
            })
        }
    }

    /**
     * Brings the provided signal strength value within the range of the chart.
     */
    private fun constrictSignalStrength(signalStrength: Float): Float {
        return if (signalStrength > WIFI_SPECTRUM_MAX) {
            WIFI_SPECTRUM_MAX
        } else if (signalStrength < WIFI_SPECTRUM_MIN) {
            WIFI_SPECTRUM_MIN
        } else {
            signalStrength
        }
    }
}
