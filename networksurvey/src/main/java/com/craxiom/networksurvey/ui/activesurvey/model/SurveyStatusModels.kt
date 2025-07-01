package com.craxiom.networksurvey.ui.activesurvey.model

import androidx.compose.ui.graphics.Color
import com.craxiom.networksurvey.model.SurveyTypes
import org.maplibre.android.geometry.LatLng

/**
 * Represents the current status of a survey type
 */
data class SurveyStatus(
    val type: SurveyTypes,
    val isActive: Boolean,
    val recordCount: Long = 0,
    val errorMessage: String? = null,
    val protocols: Set<String> = emptySet(),
    val fileInfo: FileLoggingInfo? = null,
    val mqttInfo: MqttStreamingInfo? = null,
    val uploadInfo: UploadQueueInfo? = null
)

/**
 * Information about file logging status
 */
data class FileLoggingInfo(
    val csvEnabled: Boolean,
    val csvFileSize: Long = 0,
    val csvRecordCount: Long = 0,
    val geoPackageEnabled: Boolean,
    val geoPackageFileSize: Long = 0,
    val geoPackageRecordCount: Long = 0,
    val activeProtocols: Set<String> = emptySet()
)

/**
 * Information about MQTT streaming status
 */
data class MqttStreamingInfo(
    val connectionState: MqttConnectionState,
    val brokerAddress: String? = null,
    val messagesSent: Long = 0,
    val lastMessageTime: Long? = null,
    val activeProtocols: Set<String> = emptySet(),
    val errorMessage: String? = null
)

/**
 * MQTT connection states
 */
enum class MqttConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Information about upload queue status
 */
data class UploadQueueInfo(
    val openCellidPending: Long = 0,
    val openCellidUploaded: Long = 0,
    val beaconDbPending: Long = 0,
    val beaconDbUploaded: Long = 0,
    val isUploading: Boolean = false,
    val lastUploadTime: Long? = null
)

/**
 * Represents a GPS track for survey visualization
 */
data class SurveyTrack(
    val points: List<LatLng>,
    val timestamps: List<Long>,
    val sessionId: String,
    val color: Color = Color.Blue
)

/**
 * Overall survey monitoring state
 */
data class ActiveSurveyState(
    val fileLoggingStatus: SurveyStatus? = null,
    val mqttStreamingStatus: SurveyStatus? = null,
    val uploadSurveyStatus: SurveyStatus? = null,
    val currentTrack: SurveyTrack? = null,
    val isAnyActive: Boolean = false,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val totalRecordCount: Int = 0,
    val uploadRecordCount: Int = 0,
    val isUploadActive: Boolean = false,
    val isFileLoggingActive: Boolean = false,
    val isMqttActive: Boolean = false,
    val isGrpcActive: Boolean = false
) {
    /**
     * Get list of active survey types for display
     */
    val activeSurveyTypes: List<String> 
        get() = buildList {
            if (isFileLoggingActive) add("File")
            if (isMqttActive) add("MQTT")
            if (isGrpcActive) add("gRPC")
        }
    
    /**
     * Check if any non-upload survey is active
     */
    val hasNonUploadSurvey: Boolean
        get() = isFileLoggingActive || isMqttActive || isGrpcActive
}

/**
 * Supported wireless protocols
 */
enum class WirelessProtocol(val displayName: String) {
    GSM("GSM"),
    CDMA("CDMA"),
    UMTS("UMTS"),
    LTE("LTE"),
    NR("5G NR"),
    WIFI("Wi-Fi"),
    BLUETOOTH("Bluetooth"),
    GNSS("GNSS")
}