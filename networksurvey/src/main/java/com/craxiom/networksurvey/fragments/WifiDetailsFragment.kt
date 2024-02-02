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
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
import com.craxiom.networksurvey.ui.wifi.WifiDetailsScreen
import com.craxiom.networksurvey.ui.wifi.model.WifiDetailsViewModel
import com.craxiom.networksurvey.util.NsTheme
import com.craxiom.networksurvey.util.PreferenceUtils
import timber.log.Timber

/**
 * The fragment that displays the details of a single Wifi network from the scan results.
 */
class WifiDetailsFragment : AServiceDataFragment(), IWifiSurveyRecordListener {
    private lateinit var wifiNetwork: WifiNetwork
    private lateinit var viewModel: WifiDetailsViewModel

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
        val args: WifiDetailsFragmentArgs by navArgs()
        wifiNetwork = args.wifiNetwork

        val composeView = ComposeView(requireContext())

        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                viewModel = viewModel()
                viewModel.wifiNetwork = wifiNetwork
                if (wifiNetwork.signalStrength == null) {
                    viewModel.addInitialRssi(UNKNOWN_RSSI)
                } else {
                    viewModel.addInitialRssi(wifiNetwork.signalStrength!!)
                }

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

                NsTheme {
                    WifiDetailsScreen(
                        viewModel = viewModel,
                        wifiDetailsFragment = this@WifiDetailsFragment
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
        val matchedWifiRecordWrapper =
            wifiBeaconRecords?.find { it.wifiBeaconRecord.data.bssid.equals(wifiNetwork.bssid) }

        if (matchedWifiRecordWrapper == null) {
            Timber.i("No wifi record found for ${wifiNetwork.bssid} in the wifi scan results")
            viewModel.addNewRssi(UNKNOWN_RSSI)
            return
        }

        if (matchedWifiRecordWrapper.wifiBeaconRecord.data.hasSignalStrength()) {
            viewModel.addNewRssi(matchedWifiRecordWrapper.wifiBeaconRecord.data.signalStrength.value)
        } else {
            Timber.i("No signal strength present for ${wifiNetwork.bssid} in the wifi beacon record")
            viewModel.addNewRssi(UNKNOWN_RSSI)

        }
    }

    /**
     * Navigates to the Settings UI (primarily for the user to change the scan rate)
     */
    fun navigateToSettings() {
        findNavController().navigate(WifiDetailsFragmentDirections.actionWifiDetailsToSettings())
    }
}