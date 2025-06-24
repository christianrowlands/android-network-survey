package com.craxiom.networksurvey.ui.activesurvey

import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.mqttlibrary.IConnectionStateListener
import com.craxiom.mqttlibrary.connection.ConnectionState
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
import javax.inject.Inject

/**
 * ViewModel for the Active Survey screen that manages survey status monitoring
 */
@HiltViewModel
class SurveyMonitorViewModel @Inject constructor() : ViewModel(), IConnectionStateListener,
    ILoggingChangeListener, LocationListener {

    private val _surveyState = MutableStateFlow(ActiveSurveyState())
    val surveyState: StateFlow<ActiveSurveyState> = _surveyState.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(true)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

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
     * Updates the keep screen on preference
     */
    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
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

            // Update MQTT streaming status - for now assume it's based on connection state
            val mqttActive = false // TODO: Get actual MQTT streaming state
            val mqttStatus = if (mqttActive) {
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
            activeProtocols.addAll(
                listOf(
                    WirelessProtocol.GSM.displayName,
                    WirelessProtocol.CDMA.displayName,
                    WirelessProtocol.UMTS.displayName,
                    WirelessProtocol.LTE.displayName,
                    WirelessProtocol.NR.displayName
                )
            )
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

        val fileInfo = FileLoggingInfo(
            csvEnabled = logTypeState.csv,
            csvFileSize = 0, // TODO: Calculate actual file sizes
            csvRecordCount = 0, // TODO: Get actual record counts
            geoPackageEnabled = logTypeState.geoPackage,
            geoPackageFileSize = 0,
            geoPackageRecordCount = 0,
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
            activeProtocols.addAll(
                listOf(
                    WirelessProtocol.GSM.displayName,
                    WirelessProtocol.CDMA.displayName,
                    WirelessProtocol.UMTS.displayName,
                    WirelessProtocol.LTE.displayName,
                    WirelessProtocol.NR.displayName
                )
            )
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

        val mqttInfo = MqttStreamingInfo(
            connectionState = MqttConnectionState.DISCONNECTED, // Will be updated by listener
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

    override fun onCleared() {
        super.onCleared()
        networkSurveyService?.unregisterMqttConnectionStateListener(this)
        networkSurveyService?.unregisterLoggingChangeListener(this)
        networkSurveyService?.primaryLocationListener?.unregisterListener(this)
    }
}