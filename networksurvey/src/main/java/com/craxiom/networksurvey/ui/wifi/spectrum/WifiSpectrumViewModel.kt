package com.craxiom.networksurvey.ui.wifi.spectrum

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.signals.ScanConditions
import com.craxiom.networksurvey.ui.signals.ScanListenerRegistration
import com.craxiom.networksurvey.ui.wifi.model.AndroidWifiEnvironment
import com.craxiom.networksurvey.ui.wifi.model.WifiEnvironment
import com.craxiom.networksurvey.ui.wifi.model.WifiNetworkInfo
import com.craxiom.networksurvey.ui.wifi.model.WifiScanState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Wi-Fi spectrum page: the access points of the latest scan, placed by channel and width.
 * Scoped to the page's navigation entry.
 *
 * The record listener is registered only while the page is bound to the service and the phone can
 * scan (registering starts the service's Wi-Fi scanning). Opened from the Wi-Fi list, the page
 * starts from the list's last scan ([seed]) instead of waiting for the next one.
 */
class WifiSpectrumViewModel internal constructor(
    application: Application,
    environment: WifiEnvironment,
    private val mainDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application), IWifiSurveyRecordListener {

    constructor(application: Application) : this(application, AndroidWifiEnvironment(application), Dispatchers.Main)

    private val conditions = ScanConditions(
        context = application,
        actions = WifiEnvironment.ACTIONS,
        read = environment::scanState,
        onBroadcast = ::applyScanState,
    )

    /** What stops the phone from scanning, if anything. */
    val scanState: StateFlow<WifiScanState> = conditions.state

    private val registration = ScanListenerRegistration(
        register = { it.registerWifiSurveyRecordListener(this) },
        unregister = { it.unregisterWifiSurveyRecordListener(this) },
    )

    private val mutableNetworks = MutableStateFlow<List<WifiNetworkInfo>?>(null)

    /** The access points of the latest scan, or null before the first one. */
    val networks: StateFlow<List<WifiNetworkInfo>?> = mutableNetworks.asStateFlow()

    /** Draws [records] (the Wi-Fi list's last scan) until a scan arrives; ignored once one has. */
    fun seed(records: List<WifiRecordWrapper>) {
        if (mutableNetworks.value == null && records.isNotEmpty()) {
            mutableNetworks.value = WifiSpectrumMapper.networks(records)
        }
    }

    /** Called with the bound service, and with null before the page unbinds. */
    fun onServiceChanged(service: NetworkSurveyService?) =
        registration.onServiceChanged(service, conditions.state.value == WifiScanState.READY)

    /** Re-reads whether the phone can scan (on resume and after a permission answer). */
    fun refresh() {
        conditions.refresh()
        applyScanState()
    }

    /** Excluded networks still use the air, so the spectrum shows them. */
    override fun wantsExcludedRecords(): Boolean = true

    override fun onWifiBeaconSurveyRecords(wifiBeaconRecords: List<WifiRecordWrapper>) {
        val networks = WifiSpectrumMapper.networks(wifiBeaconRecords)
        viewModelScope.launch(mainDispatcher) {
            // A scan in flight when the page unregistered is not news.
            if (registration.isRegistered) mutableNetworks.value = networks
        }
    }

    override fun onCleared() {
        registration.release()
        conditions.close()
    }

    private fun applyScanState() {
        registration.update(conditions.state.value == WifiScanState.READY)
    }
}
