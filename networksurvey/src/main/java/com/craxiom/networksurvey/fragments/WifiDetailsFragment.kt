package com.craxiom.networksurvey.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.navArgs
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.wifi.WifiDetailsScreen
import com.craxiom.networksurvey.ui.wifi.WifiDetailsViewModel
import com.craxiom.networksurvey.util.NsTheme
import timber.log.Timber

class WifiDetailsFragment : AServiceDataFragment(), IWifiSurveyRecordListener {
    private lateinit var wifiNetwork: WifiNetwork
    private lateinit var viewModel: WifiDetailsViewModel

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
                if (wifiNetwork.signalStrength != null) {
                    viewModel.addInitialRssi(wifiNetwork.signalStrength!!)
                }
                NsTheme {
                    WifiDetailsScreen(viewModel = viewModel)
                }
            }
        }

        return composeView
    }

    override fun onResume() {
        super.onResume()

        startAndBindToService()
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
        val targetWifiRecordWrapper =
            wifiBeaconRecords?.find { it.wifiBeaconRecord.data.bssid.equals(wifiNetwork.bssid) }

        if (targetWifiRecordWrapper == null) {
            Timber.i("No wifi record found for ${wifiNetwork.bssid} in the wifi scan results")
            return
        }

        if (targetWifiRecordWrapper.wifiBeaconRecord.data.hasSignalStrength()) {
            viewModel.addNewRssi(targetWifiRecordWrapper.wifiBeaconRecord.data.signalStrength.value)
        } else {
            Timber.i("No signal strength present for ${wifiNetwork.bssid} in the wifi beacon record")
        }
    }
}