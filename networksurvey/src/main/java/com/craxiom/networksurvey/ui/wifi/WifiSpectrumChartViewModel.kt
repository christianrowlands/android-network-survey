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

private val BAND_2_4_GHZ_CHANNELS = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)
private val BAND_2_4_GHZ_CHANNELS_CHART_VIEW =
    listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

/**
 * Abstract base class for the view model for a signal chart.
 */
class WifiSpectrumChartViewModel : ViewModel() {

    internal val modelProducer2Point4 = CartesianChartModelProducer.build()

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

        modelProducer2Point4.tryRunTransaction {
            add(
                create2Point4SeriesModel(wifiNetworkInfoList = wifiNetworkInfoList)
            )
        }
    }

    private fun create2Point4SeriesModel(wifiNetworkInfoList: List<WifiNetworkInfo>): LineCartesianLayerModel.Partial {
        return LineCartesianLayerModel.partial {
            wifiNetworkInfoList
                .filter { it.channel in BAND_2_4_GHZ_CHANNELS }
                .forEach { wifiNetwork ->
                    val signalStrengths = BAND_2_4_GHZ_CHANNELS_CHART_VIEW.map { channel ->
                        when (channel) {
                            wifiNetwork.channel -> constrictSignalStrength(wifiNetwork.signalStrength.toFloat())
                            wifiNetwork.channel - 1, wifiNetwork.channel + 1 -> (wifiNetwork.signalStrength - WEAKER_SIGNAL_OFFSET).toFloat()
                            else -> WIFI_CHART_MIN
                        }
                    }
                    series(BAND_2_4_GHZ_CHANNELS_CHART_VIEW, signalStrengths)
                }
        }

        // TODO Handle the 5 GHz range
    }

    private fun clearCharts() {
        modelProducer2Point4.tryRunTransaction {
            add(LineCartesianLayerModel.partial {
                series(BAND_2_4_GHZ_CHANNELS_CHART_VIEW, List(16) { WIFI_CHART_MIN })
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
