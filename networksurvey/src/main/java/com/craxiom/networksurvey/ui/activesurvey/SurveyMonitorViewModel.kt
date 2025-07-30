package com.craxiom.networksurvey.ui.activesurvey

import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.UmtsRecord
import com.craxiom.mqttlibrary.IConnectionStateListener
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.listeners.ILoggingChangeListener
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.activesurvey.model.ActiveSurveyState
import com.craxiom.networksurvey.ui.activesurvey.model.SurveyTrack
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.util.CellularUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import timber.log.Timber

/**
 * ViewModel for the Active Survey screen that manages survey status monitoring
 */
class SurveyMonitorViewModel : ViewModel(), IConnectionStateListener,
    ILoggingChangeListener, LocationListener, ICellularSurveyRecordListener {

    private var towerDetectionManager: TowerDetectionManager? = null

    private val _surveyState = MutableStateFlow(ActiveSurveyState())
    val surveyState: StateFlow<ActiveSurveyState> = _surveyState.asStateFlow()

    private val _isNewTowerDetected = MutableStateFlow(false)
    val isNewTowerDetected: StateFlow<Boolean> = _isNewTowerDetected.asStateFlow()

    private val _lastServingCellKey = MutableStateFlow<String?>(null)

    private val _servingCellInfo = MutableStateFlow<ServingCellInfo?>(null)
    val servingCellInfo: StateFlow<ServingCellInfo?> = _servingCellInfo.asStateFlow()

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
     * Initialize the TowerDetectionManager with a Context.
     * This should be called from the UI layer where Context is available.
     */
    fun initializeTowerDetectionManager(context: android.content.Context) {
        if (towerDetectionManager == null) {
            towerDetectionManager = TowerDetectionManager(context)
        }
    }

    /**
     * Sets the NetworkSurveyService reference
     */
    fun setNetworkSurveyService(service: NetworkSurveyService?) {
        // Unregister from old service
        networkSurveyService?.unregisterMqttConnectionStateListener(this)
        networkSurveyService?.unregisterLoggingChangeListener(this)
        networkSurveyService?.unregisterCellularSurveyRecordListener(this)
        networkSurveyService?.primaryLocationListener?.unregisterListener(this)

        networkSurveyService = service

        // Register with new service
        service?.let {
            it.registerMqttConnectionStateListener(this)
            it.registerLoggingChangeListener(this)
            it.registerCellularSurveyRecordListener(this)
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

            // Check MQTT, gRPC and upload status
            val mqttActive = service.isMqttStreamingActive
            val grpcActive = service.isGrpcConnectionActive
            val uploadActive = service.isUploadScanningActive

            val isAnyActive = fileLoggingActive || mqttActive || grpcActive || uploadActive

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
                    isUploadActive = uploadActive,
                    isFileLoggingActive = fileLoggingActive,
                    isMqttActive = mqttActive,
                    isGrpcActive = grpcActive
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

    /**
     * Check if the serving cell is a new tower.
     * Called when new tower alerts are enabled and serving cell changes.
     */
    fun checkServingCellForNewTower(
        servingCellInfo: ServingCellInfo?,
        isNewTowerAlertsEnabled: Boolean,
        onNewTowerDetected: () -> Unit
    ) {
        // Use the parameter if provided, otherwise use the local state
        val cellInfo = servingCellInfo ?: _servingCellInfo.value

        if (!isNewTowerAlertsEnabled || cellInfo?.servingCell == null) {
            return
        }

        val cellularRecord = cellInfo.servingCell
        val protocol = cellularRecord.cellularProtocol
        val record = cellularRecord.cellularRecord

        // Extract cell identity based on protocol
        val (mcc, mnc, area, cellId, radio) = when (protocol) {
            CellularProtocol.LTE -> {
                val lte = record as LteRecord
                val data = lte.data
                listOf(
                    data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                    data.tac?.value ?: 0, data.eci?.value?.toLong() ?: 0L, "LTE"
                )
            }

            CellularProtocol.NR -> {
                val nr = record as NrRecord
                val data = nr.data
                listOf(
                    data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                    data.tac?.value ?: 0, data.nci?.value ?: 0L, "NR"
                )
            }

            CellularProtocol.GSM -> {
                val gsm = record as GsmRecord
                val data = gsm.data
                listOf(
                    data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                    data.lac?.value ?: 0, data.ci?.value?.toLong() ?: 0L, "GSM"
                )
            }

            CellularProtocol.UMTS -> {
                val umts = record as UmtsRecord
                val data = umts.data
                listOf(
                    data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                    data.lac?.value ?: 0, data.cid?.value?.toLong() ?: 0L, "UMTS"
                )
            }

            else -> return
        }

        val mccInt = (mcc as Number).toInt()
        val mncInt = (mnc as Number).toInt()
        val areaInt = (area as Number).toInt()
        val cellIdLong = (cellId as Number).toLong()
        val radioStr = radio as String

        // Create a unique key for this cell
        val cellKey = "$mccInt-$mncInt-$areaInt-$cellIdLong"

        // Check if this is a different cell than the last one
        if (_lastServingCellKey.value != cellKey) {
            _lastServingCellKey.value = cellKey

            // Check if this is a new tower
            viewModelScope.launch {
                val manager = towerDetectionManager
                if (manager == null) {
                    Timber.w("TowerDetectionManager not initialized")
                    return@launch
                }

                val isNew = manager.checkIfTowerIsNew(
                    mccInt, mncInt, areaInt, cellIdLong, radioStr
                )

                if (isNew) {
                    _isNewTowerDetected.value = true
                    onNewTowerDetected()

                    // Keep the indicator visible until next cell change
                    // (handled by next cell change)
                } else {
                    _isNewTowerDetected.value = false
                }
            }
        }
    }

    /**
     * ICellularSurveyRecordListener implementation - Called when new cellular records are received
     */
    override fun onCellularBatch(cellularGroup: List<CellularRecordWrapper>, subscriptionId: Int) {
        Timber.d("onCellularBatch called with ${cellularGroup.size} records")

        // Find the serving cell in the batch
        cellularGroup.forEach { cellularRecord ->
            if (CellularUtils.isServingCell(cellularRecord.cellularRecord)) {
                // Extract signal value for logging
                val signalValue = when (cellularRecord.cellularProtocol) {
                    CellularProtocol.LTE -> {
                        val lte = cellularRecord.cellularRecord as LteRecord
                        lte.data?.rsrp?.value
                    }

                    CellularProtocol.NR -> {
                        val nr = cellularRecord.cellularRecord as NrRecord
                        nr.data?.ssRsrp?.value
                    }

                    CellularProtocol.GSM -> {
                        val gsm = cellularRecord.cellularRecord as GsmRecord
                        gsm.data?.signalStrength?.value
                    }

                    CellularProtocol.UMTS -> {
                        val umts = cellularRecord.cellularRecord as UmtsRecord
                        umts.data?.rscp?.value
                    }

                    else -> null
                }

                _servingCellInfo.value =
                    ServingCellInfo(cellularRecord, subscriptionId, System.currentTimeMillis())
                Timber.d("Updated serving cell: ${cellularRecord.cellularProtocol}, Signal: $signalValue")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkSurveyService?.unregisterMqttConnectionStateListener(this)
        networkSurveyService?.unregisterLoggingChangeListener(this)
        networkSurveyService?.unregisterCellularSurveyRecordListener(this)
        networkSurveyService?.primaryLocationListener?.unregisterListener(this)
    }

}