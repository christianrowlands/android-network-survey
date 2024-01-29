package com.craxiom.networksurvey.ui.wifi


import androidx.lifecycle.ViewModel
import com.craxiom.messaging.wifi.WifiBandwidth
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

val CHANNELS_2_4_GHZ = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f)
val CHANNELS_2_4_GHZ_CHART_VIEW =
    listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

val CHANNELS_5_GHZ_GROUP_1 = listOf(36f, 40f, 44f, 48f, 52f, 56f, 60f, 64f)
val CHANNELS_5_GHZ_GROUP_1_CHART_VIEW = listOf(32, 36, 40, 44, 48, 52, 56, 60, 64, 68)

val CHANNELS_5_GHZ_GROUP_2 =
    listOf(100f, 104f, 108f, 112f, 116f, 120f, 124f, 128f, 132f, 136f, 140f, 144f)
val CHANNELS_5_GHZ_GROUP_2_CHART_VIEW =
    listOf(96, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 148)

val CHANNELS_5_GHZ_GROUP_3 = listOf(149f, 153f, 157f, 161f, 165f, 169f, 173f, 177f)
val CHANNELS_5_GHZ_GROUP_3_CHART_VIEW =
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
        val matchedWifiNetworks =
            wifiNetworkInfoList.filter { it.channel.toFloat() in CHANNELS_2_4_GHZ }
        return if (matchedWifiNetworks.isEmpty()) {
            LineCartesianLayerModel.partial {
                series(
                    CHANNELS_2_4_GHZ_CHART_VIEW,
                    List(CHANNELS_2_4_GHZ_CHART_VIEW.size) { WIFI_CHART_MIN })
            }
        } else {
            createSeriesForNetworks(matchedWifiNetworks)
        }
    }

    private fun create5Group1SeriesModel(wifiNetworkInfoList: List<WifiNetworkInfo>): LineCartesianLayerModel.Partial {
        val matchedWifiNetworks =
            wifiNetworkInfoList.filter { it.channel.toFloat() in CHANNELS_5_GHZ_GROUP_1 }
        return if (matchedWifiNetworks.isEmpty()) {
            LineCartesianLayerModel.partial {
                series(
                    CHANNELS_5_GHZ_GROUP_1_CHART_VIEW,
                    List(CHANNELS_5_GHZ_GROUP_1_CHART_VIEW.size) { WIFI_CHART_MIN })
            }
        } else {
            createSeriesForNetworks(matchedWifiNetworks)
        }
    }

    private fun create5Group2SeriesModel(wifiNetworkInfoList: List<WifiNetworkInfo>): LineCartesianLayerModel.Partial {
        val matchedWifiNetworks =
            wifiNetworkInfoList.filter { it.channel.toFloat() in CHANNELS_5_GHZ_GROUP_2 }
        return if (matchedWifiNetworks.isEmpty()) {
            LineCartesianLayerModel.partial {
                series(
                    CHANNELS_5_GHZ_GROUP_2_CHART_VIEW,
                    List(CHANNELS_5_GHZ_GROUP_2_CHART_VIEW.size) { WIFI_CHART_MIN })
            }
        } else {
            createSeriesForNetworks(matchedWifiNetworks)
        }
    }

    private fun create5Group3SeriesModel(wifiNetworkInfoList: List<WifiNetworkInfo>): LineCartesianLayerModel.Partial {
        val matchedWifiNetworks =
            wifiNetworkInfoList.filter { it.channel.toFloat() in CHANNELS_5_GHZ_GROUP_3 }
        return if (matchedWifiNetworks.isEmpty()) {
            LineCartesianLayerModel.partial {
                series(
                    CHANNELS_5_GHZ_GROUP_3_CHART_VIEW,
                    List(CHANNELS_5_GHZ_GROUP_3_CHART_VIEW.size) { WIFI_CHART_MIN })
            }
        } else {
            createSeriesForNetworks(matchedWifiNetworks)
        }
    }

    /**
     * Creates a chart series for the provided list of Wifi networks that creates a line series
     * that is larger than the channel number (since channels overlap in Wi-Fi).
     */
    private fun createSeriesForNetworks(matchedWifiNetworks: List<WifiNetworkInfo>): LineCartesianLayerModel.Partial {
        return LineCartesianLayerModel.partial {
            matchedWifiNetworks.forEach { wifiNetwork ->
                val offset = getHalfOffset(wifiNetwork.bandwidth)
                val channels = listOf(
                    wifiNetwork.channel - offset,
                    wifiNetwork.channel,
                    wifiNetwork.channel + offset
                )
                val signalStrengths = listOf(
                    WIFI_CHART_MIN,
                    constrictSignalStrength(wifiNetwork.signalStrength.toFloat()),
                    WIFI_CHART_MIN
                )
                series(channels, signalStrengths)
            }
        }
    }

    /**
     * Gets the number of channels to extend the bandwidth arch to the left and right of the center
     * channel.
     */
    private fun getHalfOffset(bandwidth: WifiBandwidth): Int {
        when (bandwidth) {
            WifiBandwidth.MHZ_20 -> return 2
            WifiBandwidth.MHZ_40 -> return 4
            WifiBandwidth.MHZ_80 -> return 8
            WifiBandwidth.MHZ_80_PLUS -> return 8
            WifiBandwidth.MHZ_160 -> return 16
            WifiBandwidth.MHZ_320 -> return 32
            else -> {
                Timber.w("Unknown Wifi bandwidth value: $bandwidth")
                return 2
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
