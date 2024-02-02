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
import com.craxiom.networksurvey.ui.wifi.WifiSpectrumScreen
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrum24ViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrum5Group1ViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrum5Group2ViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrum5Group3ViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrumScreenViewModel
import com.craxiom.networksurvey.util.NsTheme
import com.craxiom.networksurvey.util.PreferenceUtils
import com.craxiom.networksurvey.util.WifiUtils

/**
 * The fragment that displays the details of a single Wifi network from the scan results.
 */
class WifiSpectrumFragment : AServiceDataFragment(), IWifiSurveyRecordListener {
    private lateinit var screenViewModel: WifiSpectrumScreenViewModel
    private lateinit var viewModel24Ghz: WifiSpectrum24ViewModel
    private lateinit var viewModel5GhzGroup1: WifiSpectrum5Group1ViewModel
    private lateinit var viewModel5GhzGroup2: WifiSpectrum5Group2ViewModel
    private lateinit var viewModel5GhzGroup3: WifiSpectrum5Group3ViewModel

    private lateinit var sharedPreferences: SharedPreferences
    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS) {
                val wifiScanRateMs = PreferenceUtils.getScanRatePreferenceMs(
                    NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS,
                    NetworkSurveyConstants.DEFAULT_WIFI_SCAN_INTERVAL_SECONDS,
                    context
                )
                screenViewModel.setScanRateSeconds(wifiScanRateMs / 1_000)
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
                screenViewModel = viewModel(key = "wifiSpectrumScreenViewModel")
                viewModel24Ghz = viewModel(key = "chart24Ghz")
                viewModel5GhzGroup1 = viewModel(key = "chart5GhzGroup1")
                viewModel5GhzGroup2 = viewModel(key = "chart5GhzGroup2")
                viewModel5GhzGroup3 = viewModel(key = "chart5GhzGroup3")

                sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                sharedPreferences.registerOnSharedPreferenceChangeListener(
                    preferenceChangeListener
                )
                val scanRateMs = PreferenceUtils.getScanRatePreferenceMs(
                    NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS,
                    NetworkSurveyConstants.DEFAULT_WIFI_SCAN_INTERVAL_SECONDS,
                    context
                )
                screenViewModel.setScanRateSeconds(scanRateMs / 1_000)
                viewModel24Ghz.initializeCharts()
                viewModel5GhzGroup1.initializeCharts()
                viewModel5GhzGroup2.initializeCharts()
                viewModel5GhzGroup3.initializeCharts()

                NsTheme {
                    WifiSpectrumScreen(
                        screenViewModel = screenViewModel,
                        viewModel24Ghz = viewModel24Ghz,
                        viewModel5GhzGroup1 = viewModel5GhzGroup1,
                        viewModel5GhzGroup2 = viewModel5GhzGroup2,
                        viewModel5GhzGroup3 = viewModel5GhzGroup3,
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
            ?.filter {
                it.wifiBeaconRecord.data.hasSignalStrength()
                        && it.wifiBeaconRecord.data.ssid != null
                        && it.wifiBeaconRecord.data.hasChannel()
                        && it.wifiBeaconRecord.data.hasFrequencyMhz()
            }
            ?.map {
                WifiNetworkInfo.create(
                    it.wifiBeaconRecord.data.ssid!!,
                    it.wifiBeaconRecord.data.signalStrength.value.toInt(),
                    it.wifiBeaconRecord.data.channel.value,
                    it.wifiBeaconRecord.data.bandwidth,
                    it.wifiBeaconRecord.data.frequencyMhz.value
                )
            }
            ?: emptyList()

        viewModel24Ghz.onWifiScanResults(wifiNetworkInfoList)
        viewModel5GhzGroup1.onWifiScanResults(wifiNetworkInfoList)
        viewModel5GhzGroup2.onWifiScanResults(wifiNetworkInfoList)
        viewModel5GhzGroup3.onWifiScanResults(wifiNetworkInfoList)
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
    val centerChannel: Int,
    val bandwidth: WifiBandwidth,
    val frequency: Int
) {
    companion object {
        fun create(
            ssid: String,
            signalStrength: Int,
            channel: Int,
            bandwidth: WifiBandwidth,
            frequency: Int
        ): WifiNetworkInfo {
            val centerChannel = WifiUtils.getCenterChannel(channel, bandwidth, frequency)
            return WifiNetworkInfo(
                ssid,
                signalStrength,
                channel,
                centerChannel,
                bandwidth,
                frequency
            )
        }
    }
}
