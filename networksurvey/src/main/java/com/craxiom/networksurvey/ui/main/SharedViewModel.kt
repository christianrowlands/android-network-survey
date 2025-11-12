package com.craxiom.networksurvey.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.wifi.model.WifiNetworkInfoList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * Sealed class representing all possible navigation events in the app.
 * Some events carry data needed for the destination screen.
 */
sealed class NavigationEvent {
    data object UploadSettings : NavigationEvent()
    data object TowerMapSettings : NavigationEvent()
    data class QrCodeScanner(val mqttConnectionSettings: MqttConnectionSettings) : NavigationEvent()
    data class QrCodeShare(val mqttConnectionSettings: MqttConnectionSettings) : NavigationEvent()
    data class TowerMap(val servingCellInfo: ServingCellInfo) : NavigationEvent()
    data class WifiDetails(val wifiNetwork: WifiNetwork) : NavigationEvent()
    data class BluetoothDetails(val bluetoothData: BluetoothRecordData) : NavigationEvent()
    data object SsidExclusionList : NavigationEvent()
    data object Acknowledgments : NavigationEvent()
    data class MqttConnection(val mqttConnectionSettings: MqttConnectionSettings?) :
        NavigationEvent()

    data object Settings : NavigationEvent()
    data object NsAnalyticsConnection : NavigationEvent()
}

/**
 * Sealed class representing dialog events.
 */
sealed class DialogEvent {
    data object BatteryOptimization : DialogEvent()
}

@HiltViewModel
class SharedViewModel @Inject constructor(application: Application) :
    AndroidViewModel(application) {

    // Navigation events using StateFlow
    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    // Dialog events using StateFlow
    private val _dialogEvent = MutableStateFlow<DialogEvent?>(null)
    val dialogEvent: StateFlow<DialogEvent?> = _dialogEvent.asStateFlow()

    // Shared data that persists across screens
    // Note: @Volatile ensures thread-safe reads, though these should primarily be accessed from the main thread
    @Volatile
    private var _latestServingCellInfo: ServingCellInfo? = null
    val latestServingCellInfo: ServingCellInfo?
        get() = _latestServingCellInfo

    @Volatile
    private var _wifiNetworkList: WifiNetworkInfoList? = null
    val wifiNetworkList: WifiNetworkInfoList?
        get() = _wifiNetworkList

    // MQTT connection settings - kept for backward compatibility with Java fragments
    // TODO: Remove this field once all Java fragments (MqttConnectionFragment) are migrated to Compose
    //  The data is already included in NavigationEvent.QrCodeScanner/QrCodeShare/MqttConnection
    @Volatile
    private var _mqttConnectionSettings: MqttConnectionSettings? = null
    val mqttConnectionSettings: MqttConnectionSettings?
        get() = _mqttConnectionSettings

    // Navigation trigger functions
    fun triggerNavigationToUploadSettings() {
        _navigationEvent.value = NavigationEvent.UploadSettings
    }

    fun triggerNavigationToTowerMapSettings() {
        _navigationEvent.value = NavigationEvent.TowerMapSettings
    }

    fun triggerNavigationToSsidExclusionList() {
        _navigationEvent.value = NavigationEvent.SsidExclusionList
    }

    fun triggerNavigationToAcknowledgments() {
        _navigationEvent.value = NavigationEvent.Acknowledgments
    }

    fun triggerNavigationToQrCodeScanner(mqttConnectionSettings: MqttConnectionSettings) {
        _mqttConnectionSettings = mqttConnectionSettings
        _navigationEvent.value = NavigationEvent.QrCodeScanner(mqttConnectionSettings)
    }

    fun triggerNavigationToQrCodeShare(mqttConnectionSettings: MqttConnectionSettings) {
        _mqttConnectionSettings = mqttConnectionSettings
        _navigationEvent.value = NavigationEvent.QrCodeShare(mqttConnectionSettings)
    }

    fun updateLatestServingCellInfo(servingCellInfo: ServingCellInfo) {
        _latestServingCellInfo = servingCellInfo
    }

    /**
     * Triggers navigation to the Tower Map screen with the latest serving cell info.
     * @return true if navigation was triggered, false if no serving cell info is available
     */
    fun triggerNavigationToTowerMap(): Boolean {
        return _latestServingCellInfo?.let {
            _navigationEvent.value = NavigationEvent.TowerMap(it)
            true
        } ?: run {
            Timber.w("Cannot navigate to Tower Map - no serving cell info available")
            false
        }
    }

    /**
     * Updates the WiFi network list.
     * @param wifiNetworkInfoList The current WiFi network information list
     */
    fun updateWifiNetworkInfoList(wifiNetworkInfoList: WifiNetworkInfoList) {
        _wifiNetworkList = wifiNetworkInfoList
    }

    fun triggerNavigationToWifiDetails(wifiNetwork: WifiNetwork) {
        _navigationEvent.value = NavigationEvent.WifiDetails(wifiNetwork)
    }

    fun triggerNavigationToBluetooth(bluetoothRecordData: BluetoothRecordData) {
        _navigationEvent.value = NavigationEvent.BluetoothDetails(bluetoothRecordData)
    }

    fun triggerNavigationToMqttConnection() {
        _mqttConnectionSettings = null
        _navigationEvent.value = NavigationEvent.MqttConnection(null)
    }

    /**
     * Triggers navigation to the MQTT Connection screen with pre-filled settings.
     * @param mqttConnectionSettings Optional MQTT connection settings to pre-fill
     */
    fun triggerNavigationToMqttConnection(mqttConnectionSettings: MqttConnectionSettings?) {
        _mqttConnectionSettings = mqttConnectionSettings
        _navigationEvent.value = NavigationEvent.MqttConnection(mqttConnectionSettings)
    }

    fun triggerNavigationToSettings() {
        _navigationEvent.value = NavigationEvent.Settings
    }

    fun triggerBatteryOptimizationDialog() {
        _dialogEvent.value = DialogEvent.BatteryOptimization
    }

    fun triggerNavigationToNsAnalyticsConnection() {
        _navigationEvent.value = NavigationEvent.NsAnalyticsConnection
    }

    /**
     * Clear the current navigation event after it has been handled.
     * This prevents the same event from being processed multiple times.
     */
    fun clearNavigationEvent() {
        _navigationEvent.value = null
    }

    /**
     * Clear the current dialog event after it has been handled.
     */
    fun clearDialogEvent() {
        _dialogEvent.value = null
    }
}