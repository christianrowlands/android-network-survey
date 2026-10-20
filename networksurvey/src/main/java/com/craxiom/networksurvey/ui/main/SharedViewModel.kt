package com.craxiom.networksurvey.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.mqtt.MqttMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Sealed class representing all possible navigation events in the app.
 * Some events carry data needed for the destination screen.
 */
sealed class NavigationEvent {
    data object UploadSettings : NavigationEvent()
    data object TowerMapSettings : NavigationEvent()
    data object MapTab : NavigationEvent()
    data object QrCodeShare : NavigationEvent()
    data class WifiDetails(val wifiNetwork: WifiNetwork) : NavigationEvent()
    data class BluetoothDetails(val bluetoothData: BluetoothRecordData) : NavigationEvent()
    data object Watchlist : NavigationEvent()
    data object MqttConnection : NavigationEvent()

    data object Settings : NavigationEvent()
    data object NsAnalyticsConnection : NavigationEvent()
    data object GrpcConnection : NavigationEvent()
    data object CommunityDatabases : NavigationEvent()
    data object OrganizationSettings : NavigationEvent()
    data object LogFiles : NavigationEvent()
}

@HiltViewModel
class SharedViewModel @Inject constructor(application: Application) :
    AndroidViewModel(application) {

    // Navigation events using StateFlow
    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    // The currently visible startup permission/location dialog (driven by the activity)
    private val _backgroundLocationRationaleVisible = MutableStateFlow(false)

    /**
     * True while the background location explanation shows. The activity raises it on launch while
     * auto start is on and the permission is missing, and Settings raises it when auto start is
     * turned on; it is drawn by the shared Compose dialog components.
     */
    val backgroundLocationRationaleVisible: StateFlow<Boolean> = _backgroundLocationRationaleVisible.asStateFlow()

    // Whether the GNSS raw-measurement failure dialog is visible
    private val _gnssFailureDialogVisible = MutableStateFlow(false)
    val gnssFailureDialogVisible: StateFlow<Boolean> = _gnssFailureDialogVisible.asStateFlow()

    // Shared data that persists across screens
    // Note: @Volatile ensures thread-safe reads, though these should primarily be accessed from the main thread
    @Volatile
    private var _latestServingCellInfo: ServingCellInfo? = null
    val latestServingCellInfo: ServingCellInfo?
        get() = _latestServingCellInfo

    // The broker the QR code share screen (a Java fragment) shows
    @Volatile
    private var _mqttConnectionSettings: MqttConnectionSettings? = null
    val mqttConnectionSettings: MqttConnectionSettings?
        get() = _mqttConnectionSettings

    // A broker the QR code scanner read, waiting for the MQTT page to add it
    private val _scannedMqttSettings = MutableStateFlow<MqttConnectionSettings?>(null)
    val scannedMqttSettings: StateFlow<MqttConnectionSettings?> = _scannedMqttSettings.asStateFlow()

    // A message for the Survey screen from a page that returns to it (for example "Connected to ...")
    private val _surveyMessage = MutableStateFlow<String?>(null)
    val surveyMessage: StateFlow<String?> = _surveyMessage.asStateFlow()

    // What the broker editor tells the MQTT page when it closes
    private val _mqttEditorMessage = MutableStateFlow<MqttMessage?>(null)
    val mqttEditorMessage: StateFlow<MqttMessage?> = _mqttEditorMessage.asStateFlow()

    // Navigation trigger functions
    fun triggerNavigationToUploadSettings() {
        _navigationEvent.value = NavigationEvent.UploadSettings
    }

    /** Selects the Map tab, closing the page that asked (Community's "See on map"). */
    fun triggerNavigationToMapTab() {
        _navigationEvent.value = NavigationEvent.MapTab
    }

    fun triggerNavigationToTowerMapSettings() {
        _navigationEvent.value = NavigationEvent.TowerMapSettings
    }

    fun triggerNavigationToWatchlist() {
        _navigationEvent.value = NavigationEvent.Watchlist
    }

    fun triggerNavigationToQrCodeShare(mqttConnectionSettings: MqttConnectionSettings) {
        _mqttConnectionSettings = mqttConnectionSettings
        _navigationEvent.value = NavigationEvent.QrCodeShare
    }

    /** Called by the QR code scanner with the broker it read; the MQTT page adds it. */
    fun onMqttQrCodeScanned(settings: MqttConnectionSettings) {
        _scannedMqttSettings.value = settings
    }

    fun consumeScannedMqttSettings() {
        _scannedMqttSettings.value = null
    }

    /** Called by the broker editor as it closes, with the message the MQTT page shows. */
    fun postMqttEditorMessage(message: MqttMessage) {
        _mqttEditorMessage.value = message
    }

    fun consumeMqttEditorMessage() {
        _mqttEditorMessage.value = null
    }

    /**
     * Leaves [message] for the Survey screen, which shows it once it is back on screen. A pending
     * message survives the navigation, unlike the Survey screen's own one-shot events.
     */
    fun postSurveyMessage(message: String) {
        _surveyMessage.value = message
    }

    fun consumeSurveyMessage() {
        _surveyMessage.value = null
    }

    fun updateLatestServingCellInfo(servingCellInfo: ServingCellInfo) {
        _latestServingCellInfo = servingCellInfo
    }

    fun triggerNavigationToWifiDetails(wifiNetwork: WifiNetwork) {
        _navigationEvent.value = NavigationEvent.WifiDetails(wifiNetwork)
    }

    fun triggerNavigationToBluetooth(bluetoothRecordData: BluetoothRecordData) {
        _navigationEvent.value = NavigationEvent.BluetoothDetails(bluetoothRecordData)
    }

    fun triggerNavigationToMqttConnection() {
        _navigationEvent.value = NavigationEvent.MqttConnection
    }

    fun triggerNavigationToSettings() {
        _navigationEvent.value = NavigationEvent.Settings
    }

    fun triggerNavigationToGrpcConnection() {
        _navigationEvent.value = NavigationEvent.GrpcConnection
    }

    fun triggerNavigationToCommunityDatabases() {
        _navigationEvent.value = NavigationEvent.CommunityDatabases
    }

    fun triggerNavigationToNsAnalyticsConnection() {
        _navigationEvent.value = NavigationEvent.NsAnalyticsConnection
    }

    fun triggerNavigationToOrganizationSettings() {
        _navigationEvent.value = NavigationEvent.OrganizationSettings
    }

    fun triggerNavigationToLogFiles() {
        _navigationEvent.value = NavigationEvent.LogFiles
    }

    /**
     * Clear the current navigation event after it has been handled.
     * This prevents the same event from being processed multiple times.
     */
    fun clearNavigationEvent() {
        _navigationEvent.value = null
    }

    /** Shows the background location explanation. */
    fun showBackgroundLocationRationale() {
        _backgroundLocationRationaleVisible.value = true
    }

    /** Clears the background location explanation after it has been handled. */
    fun clearBackgroundLocationRationale() {
        _backgroundLocationRationaleVisible.value = false
    }

    /**
     * Show the GNSS raw-measurement failure dialog.
     */
    fun showGnssFailureDialog() {
        _gnssFailureDialogVisible.value = true
    }

    /**
     * Clear the GNSS raw-measurement failure dialog after it has been handled.
     */
    fun clearGnssFailureDialog() {
        _gnssFailureDialogVisible.value = false
    }
}