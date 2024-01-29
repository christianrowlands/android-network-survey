package com.craxiom.networksurvey.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.craxiom.messaging.wifi.WifiBandwidth
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.wifi.WifiSpectrumChartViewModel
import com.craxiom.networksurvey.ui.wifi.WifiSpectrumScreen
import com.craxiom.networksurvey.util.NsTheme
import com.craxiom.networksurvey.util.PreferenceUtils

/**
 * The fragment that displays the details of a single Wifi network from the scan results.
 */
class WifiSpectrumFragment : AServiceDataFragment(), IWifiSurveyRecordListener {
    private lateinit var viewModel: WifiSpectrumChartViewModel

    private lateinit var sharedPreferences: SharedPreferences
    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS) {
                val wifiScanRateMs = PreferenceUtils.getScanRatePreferenceMs(
                    NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS,
                    NetworkSurveyConstants.DEFAULT_WIFI_SCAN_INTERVAL_SECONDS,
                    context
                )
                viewModel.setScanRateSeconds(wifiScanRateMs / 1_000)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val composeView = ComposeView(requireContext())

        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                viewModel = viewModel()

                sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                sharedPreferences.registerOnSharedPreferenceChangeListener(
                    preferenceChangeListener
                )
                val scanRateMs = PreferenceUtils.getScanRatePreferenceMs(
                    NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS,
                    NetworkSurveyConstants.DEFAULT_WIFI_SCAN_INTERVAL_SECONDS,
                    context
                )
                viewModel.setScanRateSeconds(scanRateMs / 1_000)
                viewModel.initializeCharts()

                NsTheme {
                    WifiSpectrumScreen(
                        viewModel = viewModel,
                        wifiSpectrumFragment = this@WifiSpectrumFragment
                    )
                }
            }
        }

        return composeView
    }

    override fun onResume() {
        super.onResume()

        startAndBindToService()
    }

    override fun onPause() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)

        super.onPause()
    }

    override fun onSurveyServiceConnected(service: NetworkSurveyService?) {
        if (service == null) return
        service.registerWifiSurveyRecordListener(this)
    }

    override fun onSurveyServiceDisconnecting(service: NetworkSurveyService?) {
        if (service == null) return
        service.unregisterWifiSurveyRecordListener(this)
    }

    override fun onWifiBeaconSurveyRecords(wifiBeaconRecords: MutableList<WifiRecordWrapper>?) {
        val wifiNetworkInfoList: List<WifiNetworkInfo> = wifiBeaconRecords
            ?.filter { it.wifiBeaconRecord.data.hasSignalStrength() && it.wifiBeaconRecord.data.ssid != null && it.wifiBeaconRecord.data.hasChannel() }
            ?.map {
                WifiNetworkInfo(
                    it.wifiBeaconRecord.data.ssid!!,
                    it.wifiBeaconRecord.data.signalStrength.value.toInt(),
                    it.wifiBeaconRecord.data.channel.value,
                    it.wifiBeaconRecord.data.bandwidth
                )
            }
            ?: emptyList()

        viewModel.onWifiScanResults(wifiNetworkInfoList)
    }


    /**
     * Navigates to the Settings UI (primarily for the user to change the scan rate)
     */
    fun navigateToSettings() {
        findNavController().navigate(WifiDetailsFragmentDirections.actionWifiDetailsToSettings())
    }
}

data class WifiNetworkInfo(
    val ssid: String,
    val signalStrength: Int,
    val channel: Int,
    val bandwidth: WifiBandwidth
)
