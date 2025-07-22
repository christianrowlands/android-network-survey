package com.craxiom.networksurvey.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.wifi.WifiDetailsScreen
import com.craxiom.networksurvey.ui.wifi.model.WifiDetailsViewModel
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * The fragment that displays the details of a single Wifi network from the scan results.
 */
class WifiDetailsFragment : AServiceDataFragment(), IWifiSurveyRecordListener {
    private var wifiNetwork: WifiNetwork? = null
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

    private val _serviceFlow = MutableStateFlow<NetworkSurveyService?>(null)
    val serviceFlow: StateFlow<NetworkSurveyService?> = _serviceFlow.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (context != null) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
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
                if (wifiNetwork != null) {
                    viewModel.wifiNetwork = wifiNetwork!!
                    if (wifiNetwork!!.signalStrength == null) {
                        viewModel.addInitialRssi(UNKNOWN_RSSI)
                    } else {
                        viewModel.addInitialRssi(wifiNetwork!!.signalStrength!!)
                    }
                }

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
        try {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        } catch (e: UninitializedPropertyAccessException) {
            // no-op
        }

        super.onPause()
    }

    override fun onSurveyServiceConnected(service: NetworkSurveyService?) {
        if (service == null) return
        _serviceFlow.value = service
        service.registerWifiSurveyRecordListener(this)
    }

    override fun onSurveyServiceDisconnecting(service: NetworkSurveyService?) {
        if (service == null) return
        service.unregisterWifiSurveyRecordListener(this)
        _serviceFlow.value = null

        super.onSurveyServiceDisconnecting(service)
    }

    override fun onWifiBeaconSurveyRecords(wifiBeaconRecords: MutableList<WifiRecordWrapper>?) {
        val matchedWifiRecordWrapper =
            wifiBeaconRecords?.find { it.wifiBeaconRecord.data.bssid.equals(wifiNetwork?.bssid) }

        if (matchedWifiRecordWrapper == null) {
            Timber.i("No wifi record found for ${wifiNetwork?.bssid} in the wifi scan results")
            viewModel.addNewRssi(UNKNOWN_RSSI)
            return
        }

        if (matchedWifiRecordWrapper.wifiBeaconRecord.data.hasSignalStrength()) {
            viewModel.addNewRssi(matchedWifiRecordWrapper.wifiBeaconRecord.data.signalStrength.value)
        } else {
            Timber.i("No signal strength present for ${wifiNetwork?.bssid} in the wifi beacon record")
            viewModel.addNewRssi(UNKNOWN_RSSI)
        }
    }

    /**
     * Sets the WifiNetwork that this fragment should display the details for. This needs to be
     * called right after the fragment is created.
     */
    fun setWifiNetwork(wifiNetwork: WifiNetwork) {
        this.wifiNetwork = wifiNetwork
        // TODO We might need to update the ViewModel with the new WifiNetwork if it has already
        // been initialized
    }

    /**
     * Navigates to the Settings UI (primarily for the user to change the scan rate)
     */
    fun navigateToSettings() {
        val nsActivity = activity ?: return

        val viewModel = ViewModelProvider(nsActivity)[SharedViewModel::class.java]
        viewModel.triggerNavigationToSettings()
    }

    fun navigateBack() {
        val nsActivity = activity ?: return
        nsActivity.onBackPressed()
    }

    /**
     * Navigates to the SSID Exclusion List UI
     */
    fun navigateToExclusionList() {
        val nsActivity = activity ?: return

        val viewModel = ViewModelProvider(nsActivity)[SharedViewModel::class.java]
        viewModel.triggerNavigationToSsidExclusionList()
    }
}