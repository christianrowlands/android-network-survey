package com.craxiom.networksurvey.ui.activesurvey

import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.mqttlibrary.IConnectionStateListener
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.listeners.ILoggingChangeListener
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.activesurvey.model.ActiveSurveyState
import com.craxiom.networksurvey.ui.activesurvey.model.SurveyTrack
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

    private var networkSurveyService: NetworkSurveyService? = null

    // Track points for the current session
    private val currentTrackPoints = mutableListOf<LatLng>()
    private val currentTrackTimestamps = mutableListOf<Long>()
    private var currentSessionId: String = ""

    // Refresh interval for statistics - increased to reduce main thread load
    private val STATS_REFRESH_INTERVAL_MS = 10000L  // 10 seconds

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
        // Just trigger a state update when MQTT connection changes
        updateSurveyStates()
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
            // Check if any logging is enabled
            val fileLoggingActive = service.isCellularLoggingEnabled ||
                    service.isWifiLoggingEnabled ||
                    service.isBluetoothLoggingEnabled ||
                    service.isGnssLoggingEnabled

            // Check MQTT and upload status
            val mqttActive = service.isMqttStreamingActive
            val uploadActive = service.isUploadScanningActive

            val isAnyActive = fileLoggingActive || mqttActive || uploadActive

            // Start new tracking session if surveys just became active
            if (isAnyActive && !_surveyState.value.isAnyActive) {
                startNewTrackingSession()
            }

            _surveyState.update { state ->
                state.copy(
                    fileLoggingStatus = null,  // Not needed for simplified UI
                    mqttStreamingStatus = null,  // Not needed for simplified UI
                    uploadSurveyStatus = null,  // Not needed for simplified UI
                    isAnyActive = isAnyActive,
                    lastUpdateTime = System.currentTimeMillis(),
                    totalRecordCount = getSurveySessionRecordCount(),
                    uploadRecordCount = getSurveySessionUploadRecordCount(),
                    isUploadActive = uploadActive
                )
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
        return networkSurveyService?.surveySessionStartTime
    }

    /**
     * Get the survey session record count from the service
     * @return Number of records processed in the current session
     */
    fun getSurveySessionRecordCount(): Int {
        return networkSurveyService?.surveySessionRecordCount ?: 0
    }

    /**
     * Get the survey session upload record count from the service
     * @return Number of records written to upload database in the current session
     */
    fun getSurveySessionUploadRecordCount(): Int {
        return networkSurveyService?.surveySessionUploadRecordCount ?: 0
    }

    override fun onCleared() {
        super.onCleared()
        networkSurveyService?.unregisterMqttConnectionStateListener(this)
        networkSurveyService?.unregisterLoggingChangeListener(this)
        networkSurveyService?.primaryLocationListener?.unregisterListener(this)
    }

}