package com.craxiom.networksurvey.ui.activesurvey

import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.mqttlibrary.IConnectionStateListener
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.listeners.ILoggingChangeListener
import com.craxiom.networksurvey.model.SurveyTypes
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.activesurvey.model.ActiveSurveyState
import com.craxiom.networksurvey.ui.activesurvey.model.FileLoggingInfo
import com.craxiom.networksurvey.ui.activesurvey.model.MqttConnectionState
import com.craxiom.networksurvey.ui.activesurvey.model.MqttStreamingInfo
import com.craxiom.networksurvey.ui.activesurvey.model.SurveyStatus
import com.craxiom.networksurvey.ui.activesurvey.model.SurveyTrack
import com.craxiom.networksurvey.ui.activesurvey.model.UploadQueueInfo
import com.craxiom.networksurvey.ui.activesurvey.model.WirelessProtocol
import com.craxiom.networksurvey.util.PreferenceUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for the Active Survey screen that manages survey status monitoring
 */
@HiltViewModel
class SurveyMonitorViewModel @Inject constructor() : ViewModel(), IConnectionStateListener,
    ILoggingChangeListener, LocationListener {

    private val _surveyState = MutableStateFlow(ActiveSurveyState())
    val surveyState: StateFlow<ActiveSurveyState> = _surveyState.asStateFlow()

    private var networkSurveyService: NetworkSurveyService? = null

    // Track points for the current session
    private val currentTrackPoints = mutableListOf<LatLng>()
    private val currentTrackTimestamps = mutableListOf<Long>()
    private var currentSessionId: String = ""

    // Refresh interval for statistics
    private val STATS_REFRESH_INTERVAL_MS = 3000L

    init {
        // Start periodic updates
        startPeriodicUpdates()
    }

    /**
     * Sets the NetworkSurveyService reference
     */
    fun setNetworkSurveyService(service: NetworkSurveyService?) {
        // Unregister from old service
        networkSurveyService?.unregisterMqttConnectionStateListener(this)
        networkSurveyService?.unregisterLoggingChangeListener(this)
        networkSurveyService?.primaryLocationListener?.unregisterListener(this)

        networkSurveyService = service

        // Register with new service
        service?.let {
            it.registerMqttConnectionStateListener(this)
            it.registerLoggingChangeListener(this)
            it.primaryLocationListener?.registerListener(this)

            // Get initial states
            updateSurveyStates()
        }
    }


    /**
     * LocationListener implementation - Called when location is updated
     */
    override fun onLocationChanged(location: Location) {
        // Only track if any survey is active
        if (_surveyState.value.isAnyActive) {
            currentTrackPoints.add(LatLng(location.latitude, location.longitude))
            currentTrackTimestamps.add(System.currentTimeMillis())

            // Update the current track
            _surveyState.update { state ->
                state.copy(
                    currentTrack = SurveyTrack(
                        points = currentTrackPoints.toList(),
                        timestamps = currentTrackTimestamps.toList(),
                        sessionId = currentSessionId
                    )
                )
            }

            Timber.d("Added location to track: ${location.latitude}, ${location.longitude}. Total points: ${currentTrackPoints.size}")
        }
    }

    /**
     * LocationListener implementation - Called when provider status changes
     */
    @Deprecated("Deprecated in API level 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // Not needed for our use case
    }

    /**
     * LocationListener implementation - Called when provider is enabled
     */
    override fun onProviderEnabled(provider: String) {
        Timber.d("Location provider enabled: $provider")
    }

    /**
     * LocationListener implementation - Called when provider is disabled
     */
    override fun onProviderDisabled(provider: String) {
        Timber.d("Location provider disabled: $provider")
    }

    /**
     * Starts a new tracking session
     */
    private fun startNewTrackingSession() {
        currentSessionId = System.currentTimeMillis().toString()
        currentTrackPoints.clear()
        currentTrackTimestamps.clear()
    }

    /**
     * IConnectionStateListener implementation
     */
    override fun onConnectionStateChange(connectionState: ConnectionState?) {
        val mqttState = when (connectionState) {
            ConnectionState.CONNECTED -> MqttConnectionState.CONNECTED
            ConnectionState.CONNECTING -> MqttConnectionState.CONNECTING
            ConnectionState.DISCONNECTED -> MqttConnectionState.DISCONNECTED
            else -> MqttConnectionState.ERROR
        }

        updateMqttStatus(mqttState, null)
    }

    /**
     * ILoggingChangeListener implementation
     */
    override fun onLoggingChanged() {
        updateSurveyStates()
    }

    /**
     * Updates all survey states based on current service state
     */
    private fun updateSurveyStates() {
        val service = networkSurveyService ?: return

        viewModelScope.launch {
            // Update file logging status - check if any logging is enabled
            val fileLoggingActive = service.isCellularLoggingEnabled ||
                    service.isWifiLoggingEnabled ||
                    service.isBluetoothLoggingEnabled ||
                    service.isGnssLoggingEnabled
            val fileLoggingStatus = if (fileLoggingActive) {
                createFileLoggingStatus(service)
            } else {
                null
            }

            // Update MQTT streaming status
            // For now, we'll check if MQTT is enabled based on whether streaming is active
            val mqttActive = _surveyState.value.mqttStreamingStatus?.isActive ?: false
            val mqttStatus = if (mqttActive || _surveyState.value.mqttStreamingStatus != null) {
                createMqttStreamingStatus(service)
            } else {
                null
            }

            // Update upload survey status
            val uploadActive = service.isUploadScanningActive
            val uploadStatus = if (uploadActive) {
                createUploadSurveyStatus(service)
            } else {
                null
            }

            val isAnyActive = fileLoggingActive || mqttActive || uploadActive

            // Start new tracking session if surveys just became active
            if (isAnyActive && !_surveyState.value.isAnyActive) {
                startNewTrackingSession()
            }

            _surveyState.update { state ->
                state.copy(
                    fileLoggingStatus = fileLoggingStatus,
                    mqttStreamingStatus = mqttStatus,
                    uploadSurveyStatus = uploadStatus,
                    isAnyActive = isAnyActive,
                    lastUpdateTime = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Creates file logging status from service state
     */
    private fun createFileLoggingStatus(service: NetworkSurveyService): SurveyStatus {
        val activeProtocols = mutableSetOf<String>()

        if (service.isCellularLoggingEnabled) {
            activeProtocols.add("Cellular")
        }

        if (service.isWifiLoggingEnabled) {
            activeProtocols.add(WirelessProtocol.WIFI.displayName)
        }

        if (service.isBluetoothLoggingEnabled) {
            activeProtocols.add(WirelessProtocol.BLUETOOTH.displayName)
        }

        if (service.isGnssLoggingEnabled) {
            activeProtocols.add(WirelessProtocol.GNSS.displayName)
        }

        val logTypeState = PreferenceUtils.getLogTypePreference(service.applicationContext)

        // Calculate actual file sizes and record counts
        val (csvFileSize, csvRecordCount) = if (logTypeState.csv) {
            calculateCsvFileStats()
        } else {
            Pair(0L, 0L)
        }

        val (geoPackageFileSize, geoPackageRecordCount) = if (logTypeState.geoPackage) {
            calculateGeoPackageFileStats()
        } else {
            Pair(0L, 0L)
        }

        val fileInfo = FileLoggingInfo(
            csvEnabled = logTypeState.csv,
            csvFileSize = csvFileSize,
            csvRecordCount = csvRecordCount,
            geoPackageEnabled = logTypeState.geoPackage,
            geoPackageFileSize = geoPackageFileSize,
            geoPackageRecordCount = geoPackageRecordCount,
            activeProtocols = activeProtocols
        )

        return SurveyStatus(
            type = SurveyTypes.CELLULAR,
            isActive = true,
            protocols = activeProtocols,
            fileInfo = fileInfo
        )
    }

    /**
     * Creates MQTT streaming status from service state
     */
    private fun createMqttStreamingStatus(service: NetworkSurveyService): SurveyStatus {
        val activeProtocols = mutableSetOf<String>()

        // Similar protocol detection as file logging
        if (service.isCellularLoggingEnabled) {
            activeProtocols.add("Cellular")
        }

        if (service.isWifiLoggingEnabled) {
            activeProtocols.add(WirelessProtocol.WIFI.displayName)
        }

        if (service.isBluetoothLoggingEnabled) {
            activeProtocols.add(WirelessProtocol.BLUETOOTH.displayName)
        }

        if (service.isGnssLoggingEnabled) {
            activeProtocols.add(WirelessProtocol.GNSS.displayName)
        }

        // Use the current state from the connection state listener
        val currentMqttInfo = _surveyState.value.mqttStreamingStatus?.mqttInfo

        val mqttInfo = MqttStreamingInfo(
            connectionState = currentMqttInfo?.connectionState ?: MqttConnectionState.DISCONNECTED,
            brokerAddress = currentMqttInfo?.brokerAddress,
            activeProtocols = activeProtocols
        )

        return SurveyStatus(
            type = SurveyTypes.CELLULAR,
            isActive = true,
            protocols = activeProtocols,
            mqttInfo = mqttInfo
        )
    }

    /**
     * Creates upload survey status from service state
     */
    private fun createUploadSurveyStatus(service: NetworkSurveyService): SurveyStatus {
        val uploadInfo = UploadQueueInfo(
            // TODO: Query actual database counts
            openCellidPending = 0,
            openCellidUploaded = 0,
            beaconDbPending = 0,
            beaconDbUploaded = 0,
            isUploading = false
        )

        return SurveyStatus(
            type = SurveyTypes.CELLULAR,
            isActive = true,
            uploadInfo = uploadInfo
        )
    }

    /**
     * Updates MQTT status specifically
     */
    private fun updateMqttStatus(state: MqttConnectionState, brokerHost: String?) {
        _surveyState.update { surveyState ->
            val currentMqttStatus = surveyState.mqttStreamingStatus

            if (currentMqttStatus != null) {
                val updatedMqttInfo = currentMqttStatus.mqttInfo?.copy(
                    connectionState = state,
                    brokerAddress = brokerHost
                ) ?: MqttStreamingInfo(
                    connectionState = state,
                    brokerAddress = brokerHost
                )

                surveyState.copy(
                    mqttStreamingStatus = currentMqttStatus.copy(
                        mqttInfo = updatedMqttInfo
                    )
                )
            } else {
                surveyState
            }
        }
    }

    /**
     * Starts periodic updates for statistics
     */
    private fun startPeriodicUpdates() {
        viewModelScope.launch {
            while (true) {
                if (_surveyState.value.isAnyActive) {
                    updateSurveyStates()
                }
                delay(STATS_REFRESH_INTERVAL_MS)
            }
        }
    }

    /**
     * Get the survey session start time from the service
     * @return Start time in milliseconds since epoch, or null if no session
     */
    fun getSurveySessionStartTime(): Long? {
        return networkSurveyService?.getSurveySessionStartTime()
    }

    /**
     * Get the survey session record count from the service
     * @return Number of records processed in the current session
     */
    fun getSurveySessionRecordCount(): Int {
        return networkSurveyService?.getSurveySessionRecordCount() ?: 0
    }

    override fun onCleared() {
        super.onCleared()
        networkSurveyService?.unregisterMqttConnectionStateListener(this)
        networkSurveyService?.unregisterLoggingChangeListener(this)
        networkSurveyService?.primaryLocationListener?.unregisterListener(this)
    }

    /**
     * Calculates the total size and record count for CSV log files
     */
    private fun calculateCsvFileStats(): Pair<Long, Long> {
        try {
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val csvLogDir = File(downloadsDir, NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME)

            if (!csvLogDir.exists() || !csvLogDir.isDirectory) {
                return Pair(0L, 0L)
            }

            var totalSize = 0L
            var totalRecords = 0L

            // Get all CSV files in the directory
            csvLogDir.listFiles { file ->
                file.isFile && file.name.endsWith(".csv")
            }?.forEach { csvFile ->
                totalSize += csvFile.length()

                // Estimate record count by counting lines (excluding header)
                try {
                    val lineCount = csvFile.useLines { lines ->
                        lines.count() - 1 // Subtract 1 for header
                    }
                    if (lineCount > 0) {
                        totalRecords += lineCount
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error counting lines in CSV file: ${csvFile.name}")
                }
            }

            return Pair(totalSize, totalRecords)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating CSV file stats")
            return Pair(0L, 0L)
        }
    }

    /**
     * Calculates the total size and record count for GeoPackage files
     */
    private fun calculateGeoPackageFileStats(): Pair<Long, Long> {
        try {
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val geoPackageLogDir = File(downloadsDir, NetworkSurveyConstants.LOG_DIRECTORY_NAME)

            if (!geoPackageLogDir.exists() || !geoPackageLogDir.isDirectory) {
                return Pair(0L, 0L)
            }

            var totalSize = 0L
            var totalRecords = 0L

            // Get all GeoPackage files in the directory
            geoPackageLogDir.listFiles { file ->
                file.isFile && file.name.endsWith(".gpkg")
            }?.forEach { gpkgFile ->
                totalSize += gpkgFile.length()

                // For GeoPackage files, we can't easily count records without opening the database
                // So for now, we'll just estimate based on file size (rough approximation)
                // Average record size is approximately 500 bytes in GeoPackage
                val estimatedRecords = gpkgFile.length() / 500
                totalRecords += estimatedRecords
            }

            return Pair(totalSize, totalRecords)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating GeoPackage file stats")
            return Pair(0L, 0L)
        }
    }
}