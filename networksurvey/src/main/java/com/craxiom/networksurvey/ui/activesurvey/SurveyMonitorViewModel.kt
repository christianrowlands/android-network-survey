package com.craxiom.networksurvey.ui.activesurvey

import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.UmtsRecord
import com.craxiom.mqttlibrary.IConnectionStateListener
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.listeners.ILoggingChangeListener
import com.craxiom.networksurvey.listeners.IMissionIdListener
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.activesurvey.model.ActiveSurveyState
import com.craxiom.networksurvey.ui.activesurvey.model.NsAnalyticsInfo
import com.craxiom.networksurvey.ui.activesurvey.model.SurveyTrack
import com.craxiom.networksurvey.ui.activesurvey.model.SurveyTrackBuilder
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import com.craxiom.networksurvey.util.NsAnalyticsUtils
import com.craxiom.networksurvey.util.NsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import timber.log.Timber

/**
 * ViewModel for the Active Survey screen that manages survey status monitoring
 */
class SurveyMonitorViewModel(
    private val application: Application
) : AndroidViewModel(application), IConnectionStateListener,
    ILoggingChangeListener, LocationListener, ICellularSurveyRecordListener, IMissionIdListener {

    private var towerDetectionManager: TowerDetectionManager? = null

    private val _surveyState = MutableStateFlow(ActiveSurveyState())
    val surveyState: StateFlow<ActiveSurveyState> = _surveyState.asStateFlow()

    private val _isNewTowerDetected = MutableStateFlow(false)
    val isNewTowerDetected: StateFlow<Boolean> = _isNewTowerDetected.asStateFlow()

    private val _lastServingCellKey = MutableStateFlow<String?>(null)

    private val _servingCellInfo = MutableStateFlow<ServingCellInfo?>(null)
    val servingCellInfo: StateFlow<ServingCellInfo?> = _servingCellInfo.asStateFlow()

    private var networkSurveyService: NetworkSurveyService? = null
    private val database = SurveyDatabase.getInstance(application)

    // The map trail. Appends are cheap; the immutable snapshot the UI reads is rebuilt at most
    // once every TRACK_EMIT_INTERVAL_MS so a long session does not copy a growing list per fix.
    private val trackBuilder = SurveyTrackBuilder()
    private var lastTrackEmitMs = 0L

    // Refresh interval for statistics - increased to reduce main thread load
    private val STATS_REFRESH_INTERVAL_MS = 10000L  // 10 seconds

    // NS Analytics device status check interval (5 minutes)
    private val NS_ANALYTICS_STATUS_CHECK_INTERVAL_MS = 5 * 60 * 1000L
    private var lastNsAnalyticsStatusCheckTime = 0L

    init {
        // Start periodic updates
        startPeriodicUpdates()
    }

    /**
     * Initialize the TowerDetectionManager with a Context.
     * This should be called from the UI layer where Context is available.
     */
    fun initializeTowerDetectionManager(context: Context) {
        if (towerDetectionManager == null) {
            // Use application context explicitly to avoid any potential Activity context leak
            towerDetectionManager = TowerDetectionManager(context.applicationContext)
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
        networkSurveyService?.unregisterMissionIdListener(this)
        networkSurveyService?.primaryLocationListener?.unregisterListener(this)

        networkSurveyService = service

        // Register with new service
        service?.let {
            it.registerMqttConnectionStateListener(this)
            it.registerLoggingChangeListener(this)
            it.registerCellularSurveyRecordListener(this)
            it.registerMissionIdListener(this)
            // Re-registering means fixes were missed while this screen was away, so the trail has
            // to break here rather than draw a straight line across wherever the device went. The
            // trail itself is kept; only the segment ends.
            trackBuilder.setGapThreshold(SurveyTrackBuilder.gapThresholdFor(it.locationUpdateRateMs))
            trackBuilder.startNewSegment()
            it.primaryLocationListener?.registerListener(this)

            // Get initial states
            updateSurveyStates()
        }
    }

    /**
     * Appends the fix to the map trail while a survey is running. The exposed state is rebuilt at
     * most once every [TRACK_EMIT_INTERVAL_MS], because a polyline redrawing twice a second is
     * indistinguishable to the user from one redrawing on every fix, and rebuilding it per fix is
     * quadratic over a long session.
     */
    override fun onLocationChanged(location: Location) {
        if (!_surveyState.value.isAnyActive) return

        val now = System.currentTimeMillis()
        val kept = trackBuilder.add(location.latitude, location.longitude, now)
        if (!kept && _surveyState.value.currentTrack != null) return
        if (now - lastTrackEmitMs < TRACK_EMIT_INTERVAL_MS) return

        lastTrackEmitMs = now
        emitTrack()
    }

    private fun emitTrack() {
        val segments = trackBuilder.segments()
            .map { segment -> segment.map { LatLng(it.latitude, it.longitude) } }
        _surveyState.update { state ->
            state.copy(currentTrack = if (segments.isEmpty()) null else SurveyTrack(segments))
        }
    }

    @Deprecated("Deprecated in API level 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // Not needed for our use case
    }

    override fun onProviderEnabled(provider: String) {
        Timber.d("Location provider enabled: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        // A disabled provider means the next fix could be anywhere, so break the trail
        Timber.d("Location provider disabled: $provider")
        trackBuilder.startNewSegment()
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
     * IMissionIdListener implementation. Update immediately so the Mission ID row reflects a roll
     * the instant it happens, independent of the periodic refresh.
     */
    override fun onMissionIdChanged(missionId: String?, missionSessionActive: Boolean) {
        _surveyState.update { state ->
            state.copy(missionId = if (missionSessionActive) (missionId ?: "") else "")
        }
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

            // Check NS Analytics status
            val nsAnalyticsActive = service.isNsAnalyticsScanningActive
            val nsAnalyticsInfo = if (nsAnalyticsActive) {
                // Periodically check device status to detect web-initiated deregistration
                checkNsAnalyticsDeviceStatusIfNeeded()

                NsAnalyticsInfo(
                    isEnabled = true,
                    isRegistered = service.isNsAnalyticsRegistered,
                    queuedRecords = withContext(Dispatchers.IO) {
                        database.nsAnalyticsDao().getPendingRecordCount()
                    },
                    uploadedRecords = NsAnalyticsSecureStorage.getLastUploadRecordsCount(application),
                    lastUploadTime = null,
                    workspaceId = service.nsAnalyticsWorkspaceId,
                    errorMessage = null
                )
            } else null

            val isAnyActive =
                fileLoggingActive || mqttActive || grpcActive || uploadActive || nsAnalyticsActive

            // A survey just started, so the trail belongs to this session and not the previous one
            if (isAnyActive && !_surveyState.value.isAnyActive) {
                trackBuilder.clear()
                _surveyState.update { state -> state.copy(currentTrack = null) }
            }

            _surveyState.update { state ->
                state.copy(
                    fileLoggingStatus = null,  // Not needed for simplified UI
                    mqttStreamingStatus = null,  // Not needed for simplified UI
                    uploadSurveyStatus = null,  // Not needed for simplified UI
                    nsAnalyticsInfo = nsAnalyticsInfo,
                    isAnyActive = isAnyActive,
                    lastUpdateTime = System.currentTimeMillis(),
                    totalRecordCount = getSurveySessionRecordCount(),
                    uploadRecordCount = getSurveySessionUploadRecordCount(),
                    isUploadActive = uploadActive,
                    isFileLoggingActive = fileLoggingActive,
                    isMqttActive = mqttActive,
                    isGrpcActive = grpcActive,
                    isNsAnalyticsActive = nsAnalyticsActive,
                    missionId = if (service.isMissionSessionActive) (service.rolledMissionId
                        ?: "") else ""
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
     * Check NS Analytics device status if enough time has elapsed since last check.
     * This helps detect if device was deregistered from the web dashboard.
     */
    private fun checkNsAnalyticsDeviceStatusIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastNsAnalyticsStatusCheckTime < NS_ANALYTICS_STATUS_CHECK_INTERVAL_MS) {
            return // Not time to check yet
        }

        lastNsAnalyticsStatusCheckTime = now

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Use centralized status check utility
                when (val result = NsAnalyticsUtils.checkDeviceRegistrationStatus(application)) {
                    is NsAnalyticsUtils.DeviceStatusResult.Deregistered -> {
                        // Device has been deregistered - clean up
                        NsAnalyticsUtils.cleanupAfterDeregistration(application)

                        // Stop NS Analytics survey if running
                        networkSurveyService?.let { service ->
                            if (service.isNsAnalyticsScanningActive) {
                                withContext(Dispatchers.Main) {
                                    service.toggleNsAnalyticsScanning(false)
                                }
                                Timber.i("Stopped NS Analytics survey due to deregistration")
                            }
                        }

                        withContext(Dispatchers.Main) {
                            updateSurveyStates()
                        }
                    }

                    is NsAnalyticsUtils.DeviceStatusResult.Active -> {
                        // Device is still active
                        Timber.d("NS Analytics device status check: active")
                    }

                    is NsAnalyticsUtils.DeviceStatusResult.CheckFailed -> {
                        // Check failed - log and continue
                        Timber.d("NS Analytics device status check failed: ${result.reason}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "NS Analytics device status check failed in Survey Monitor")
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

        // Extract cell identity based on protocol, using PLMN strings to preserve MNC leading zeros
        val (mccStr, mncStr, areaVal, cellIdVal, radioVal) = when (protocol) {
            CellularProtocol.LTE -> {
                val lte = record as LteRecord
                val data = lte.data
                val mccMnc = NsUtils.extractMccMncStrings(
                    data.hasPlmn(),
                    if (data.hasPlmn()) data.plmn.value else null,
                    data.mcc?.value ?: 0, data.mnc?.value ?: 0
                )
                listOf(
                    mccMnc[0], mccMnc[1],
                    data.tac?.value ?: 0, data.eci?.value?.toLong() ?: 0L, "LTE"
                )
            }

            CellularProtocol.NR -> {
                val nr = record as NrRecord
                val data = nr.data
                val mccMnc = NsUtils.extractMccMncStrings(
                    data.hasPlmn(),
                    if (data.hasPlmn()) data.plmn.value else null,
                    data.mcc?.value ?: 0, data.mnc?.value ?: 0
                )
                listOf(
                    mccMnc[0], mccMnc[1],
                    data.tac?.value ?: 0, data.nci?.value ?: 0L, "NR"
                )
            }

            CellularProtocol.GSM -> {
                val gsm = record as GsmRecord
                val data = gsm.data
                val mccMnc = NsUtils.extractMccMncStrings(
                    data.hasPlmn(),
                    if (data.hasPlmn()) data.plmn.value else null,
                    data.mcc?.value ?: 0, data.mnc?.value ?: 0
                )
                listOf(
                    mccMnc[0], mccMnc[1],
                    data.lac?.value ?: 0, data.ci?.value?.toLong() ?: 0L, "GSM"
                )
            }

            CellularProtocol.UMTS -> {
                val umts = record as UmtsRecord
                val data = umts.data
                val mccMnc = NsUtils.extractMccMncStrings(
                    data.hasPlmn(),
                    if (data.hasPlmn()) data.plmn.value else null,
                    data.mcc?.value ?: 0, data.mnc?.value ?: 0
                )
                listOf(
                    mccMnc[0], mccMnc[1],
                    data.lac?.value ?: 0, data.cid?.value?.toLong() ?: 0L, "UMTS"
                )
            }

            else -> return
        }

        val mcc = mccStr as String
        val mnc = mncStr as String
        val areaInt = (areaVal as Number).toInt()
        val cellIdLong = (cellIdVal as Number).toLong()
        val radioStr = radioVal as String

        // Create a unique key for this cell
        val cellKey = "$mcc-$mnc-$areaInt-$cellIdLong"

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
                    mcc, mnc, areaInt, cellIdLong, radioStr
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
        towerDetectionManager = null
        networkSurveyService?.unregisterMqttConnectionStateListener(this)
        networkSurveyService?.unregisterLoggingChangeListener(this)
        networkSurveyService?.unregisterCellularSurveyRecordListener(this)
        networkSurveyService?.unregisterMissionIdListener(this)
        networkSurveyService?.primaryLocationListener?.unregisterListener(this)
    }

    companion object {
        /** How often the map trail state is rebuilt for the UI, regardless of the fix rate. */
        private const val TRACK_EMIT_INTERVAL_MS = 2_000L
    }
}