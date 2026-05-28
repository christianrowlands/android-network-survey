package com.craxiom.networksurvey.ui.dashboard

import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.model.SurveyTypes
import com.craxiom.networksurvey.util.LocationStatusHelper.LocationState

/**
 * UI state for the battery management card.
 */
data class BatteryUiState(
    val visible: Boolean = false,
    val isPaused: Boolean = false,
    val batteryLevel: Int = -1,
    val batteryThreshold: Int = 0,
)

/**
 * UI state for the queue backpressure/MQTT drop card.
 */
data class QueueUiState(
    val visible: Boolean = false,
    val isPaused: Boolean = false,
    val isDropping: Boolean = false,
    val isUnderMdmControl: Boolean = false,
)

/**
 * UI state for the location status display.
 */
data class LocationUiState(
    val state: LocationState = LocationState.SEARCHING,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val hasLocation: Boolean = false,
)

/**
 * UI state for the logging controls card.
 */
data class LoggingUiState(
    val cellularEnabled: Boolean = false,
    val phoneStateEnabled: Boolean = false,
    val phoneStateAutoStarted: Boolean = false,
    val wifiEnabled: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val gnssEnabled: Boolean = false,
    val cdrEnabled: Boolean = false,
)

/**
 * UI state for the MQTT status card.
 */
data class MqttUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val cellularStreamEnabled: Boolean = false,
    val phoneStateStreamEnabled: Boolean = false,
    val wifiStreamEnabled: Boolean = false,
    val bluetoothStreamEnabled: Boolean = false,
    val gnssStreamEnabled: Boolean = false,
    val deviceStatusStreamEnabled: Boolean = false,
    val isMqttToggleHiddenByMdm: Boolean = false,
)

/**
 * UI state for the community upload card.
 */
data class UploadUiState(
    val visible: Boolean = true,
    val scanningActive: Boolean = false,
    val cellularQueueCount: Int = -1,
    val wifiQueueCount: Int = -1,
    val uploadButtonEnabled: Boolean = false,
    val autoUploadEnabled: Boolean = false,
    val activeSurveys: Set<SurveyTypes> = emptySet(),
    val uploadProgress: UploadProgressState = UploadProgressState.Hidden,
)

/**
 * Represents upload progress states.
 */
sealed class UploadProgressState {
    data object Hidden : UploadProgressState()
    data class InProgress(
        val progress: Int = 0,
        val maxProgress: Int = 100,
        val statusMessage: String = "",
    ) : UploadProgressState()

    data class Finished(
        val cancelled: Boolean = false,
        val ocidResult: String = "",
        val beaconDbResult: String = "",
        val ocidResultMessage: String = "",
        val beaconDbResultMessage: String = "",
    ) : UploadProgressState()
}

/**
 * UI state for the NS Analytics card.
 */
data class NsAnalyticsUiState(
    val visible: Boolean = false,
    val isSurveyActive: Boolean = false,
    val surveyStartTime: Long = 0L,
    val cellularCount: Int = 0,
    val wifiCount: Int = 0,
    val bluetoothCount: Int = 0,
    val gnssCount: Int = 0,
    val phoneStateCount: Int = 0,
)

/**
 * UI state for the Mission ID card.
 *
 * @param visible True once a real survey has started during this app session. Stays true after a
 *                survey stops so the user can still copy the most recent Mission ID.
 * @param active True while a relevant survey is currently running.
 * @param missionId The current or most recent Mission ID.
 */
data class MissionIdUiState(
    val visible: Boolean = false,
    val active: Boolean = false,
    val missionId: String = "",
)

/**
 * One-shot events emitted by the DashboardViewModel.
 */
sealed class DashboardEvent {
    data class ShowToast(val message: String) : DashboardEvent()
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val action: (() -> Unit)? = null,
    ) : DashboardEvent()

    data object ShowBatteryOptimizationDialog : DashboardEvent()
    data object ShowBluetoothPermissionRationale : DashboardEvent()
    data class ShowCdrPermissionRationale(val isRequired: Boolean) : DashboardEvent()
    data object ShowUploadDialog : DashboardEvent()
    data object ShowDisableQueueLimitDialog : DashboardEvent()
}
