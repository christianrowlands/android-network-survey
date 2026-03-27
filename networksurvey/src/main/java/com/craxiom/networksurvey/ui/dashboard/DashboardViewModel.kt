package com.craxiom.networksurvey.ui.dashboard

import android.app.Application
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.mqttlibrary.IConnectionStateListener
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.listeners.ILoggingChangeListener
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.uploader.NsUploaderWorker
import com.craxiom.networksurvey.model.SurveyTypes
import com.craxiom.networksurvey.services.BatteryMonitor
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.util.LocationStatusHelper
import com.craxiom.networksurvey.util.MdmUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * ViewModel for the Dashboard screen. Uses separate StateFlows per card
 * so only the affected card recomposes when state changes.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application),
    LocationListener,
    IConnectionStateListener,
    ILoggingChangeListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    BatteryMonitor.IBatteryLevelListener,
    NetworkSurveyService.IQueueBackpressureStateListener,
    NetworkSurveyService.IMqttDropModeStateListener {

    private val context get() = getApplication<Application>()
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    private val _locationState = MutableStateFlow(LocationUiState())
    val locationState: StateFlow<LocationUiState> = _locationState.asStateFlow()

    private val _batteryState = MutableStateFlow(BatteryUiState())
    val batteryState: StateFlow<BatteryUiState> = _batteryState.asStateFlow()

    private val _queueState = MutableStateFlow(QueueUiState())
    val queueState: StateFlow<QueueUiState> = _queueState.asStateFlow()

    private val _loggingState = MutableStateFlow(LoggingUiState())
    val loggingState: StateFlow<LoggingUiState> = _loggingState.asStateFlow()

    private val _mqttState = MutableStateFlow(MqttUiState())
    val mqttState: StateFlow<MqttUiState> = _mqttState.asStateFlow()

    private val _uploadState = MutableStateFlow(UploadUiState())
    val uploadState: StateFlow<UploadUiState> = _uploadState.asStateFlow()

    private val _nsAnalyticsState = MutableStateFlow(NsAnalyticsUiState())
    val nsAnalyticsState: StateFlow<NsAnalyticsUiState> = _nsAnalyticsState.asStateFlow()

    private val _events = MutableSharedFlow<DashboardEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<DashboardEvent> = _events.asSharedFlow()

    var service: NetworkSurveyService? = null
        private set

    private var pollingJob: Job? = null

    /**
     * Called when the service is connected. Registers all listeners and syncs initial state.
     */
    fun onServiceConnected(networkSurveyService: NetworkSurveyService) {
        service = networkSurveyService

        // Register listeners
        networkSurveyService.registerLocationListener(this)
        networkSurveyService.registerMqttConnectionStateListener(this)
        networkSurveyService.registerLoggingChangeListener(this)
        networkSurveyService.batteryMonitor?.register(this)
        networkSurveyService.registerQueueBackpressureStateListener(this)
        networkSurveyService.registerMqttDropModeStateListener(this)
        preferences.registerOnSharedPreferenceChangeListener(this)

        // Sync initial state from service
        val latestLocation = networkSurveyService.primaryLocationListener?.latestLocation
        if (latestLocation != null) updateLocationFromLocation(latestLocation)

        updateMqttConnectionState(networkSurveyService.mqttConnectionState)
        readMqttStreamSettings()
        readAutoUploadSetting()
        syncLoggingState(networkSurveyService)

        // Upload scanning state
        val uploadActive = networkSurveyService.isUploadScanningActive
        val activeSurveys = mutableSetOf<SurveyTypes>()
        if (uploadActive) {
            if (networkSurveyService.isCellularScanningActive) activeSurveys.add(SurveyTypes.CELLULAR)
            if (networkSurveyService.isWifiScanningActive) activeSurveys.add(SurveyTypes.WIFI)
        }
        _uploadState.update {
            it.copy(
                visible = MdmUtils.isExternalDataUploadAllowed(context),
                scanningActive = uploadActive,
                activeSurveys = activeSurveys,
            )
        }

        updateBatteryStatus(networkSurveyService)
        updateQueueStatus(
            networkSurveyService.isPausedForQueueBackpressure,
            networkSurveyService.isMqttDroppingMessages,
        )
        updateNsAnalyticsState()

        // Check MDM for MQTT toggle
        _mqttState.update {
            it.copy(
                isMqttToggleHiddenByMdm = MdmUtils.isUnderMdmControlAndEnabled(
                    context,
                    MqttConstants.PROPERTY_MQTT_CONNECTION_HOST,
                ),
            )
        }

        // Start periodic polling for upload/NS Analytics counts
        startPolling()
    }

    /**
     * Called when the service is disconnecting. Unregisters all listeners and cancels polling.
     */
    fun onServiceDisconnecting(networkSurveyService: NetworkSurveyService) {
        pollingJob?.cancel()
        pollingJob = null
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        networkSurveyService.unregisterLocationListener(this)
        networkSurveyService.unregisterLoggingChangeListener(this)
        networkSurveyService.unregisterMqttConnectionStateListener(this)
        networkSurveyService.batteryMonitor?.unregister(this)
        networkSurveyService.unregisterQueueBackpressureStateListener(this)
        networkSurveyService.unregisterMqttDropModeStateListener(this)
        service = null
    }

    override fun onCleared() {
        super.onCleared()
        service?.let { onServiceDisconnecting(it) }
    }

    // ========== Location Listener ==========

    override fun onLocationChanged(location: Location) {
        updateLocationFromLocation(location)
    }

    override fun onProviderEnabled(provider: String) {
        if (LocationManager.GPS_PROVIDER == provider) {
            initializeLocationState()
        }
    }

    override fun onProviderDisabled(provider: String) {
        if (LocationManager.GPS_PROVIDER == provider) {
            _locationState.update {
                it.copy(
                    state = LocationStatusHelper.LocationState.GPS_DISABLED,
                    hasLocation = false,
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // No-op
    }

    // ========== MQTT Connection Listener ==========

    override fun onConnectionStateChange(connectionState: ConnectionState) {
        updateMqttConnectionState(connectionState)
    }

    // ========== Logging Change Listener ==========

    override fun onLoggingChanged() {
        service?.let { svc ->
            syncLoggingState(svc)
            updateBatteryStatus(svc)
        }
    }

    // ========== SharedPreferences Listener ==========

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == null) return
        when (key) {
            NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED,
            NetworkSurveyConstants.PROPERTY_MQTT_PHONE_STATE_STREAM_ENABLED,
            NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED,
            NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED,
            NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED,
            NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED,
                -> readMqttStreamSettings()

            NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED -> readAutoUploadSetting()

            NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID,
            NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB,
                -> queryUploadQueueCount()

            NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT -> {
                service?.let { updateBatteryStatus(it) }
            }

            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_WORKSPACE_NAME,
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_URL,
                -> updateNsAnalyticsState()
        }
    }

    // ========== Battery Listener ==========

    override fun onBatteryLevelChanged(newLevel: Int) {
        service?.let { updateBatteryStatus(it) }
    }

    override fun onBatteryLevelBelowThreshold(currentLevel: Int, threshold: Int) {
        service?.let { updateBatteryStatus(it) }
    }

    override fun onBatteryLevelAboveThreshold(currentLevel: Int, threshold: Int) {
        service?.let { updateBatteryStatus(it) }
    }

    // ========== Queue Backpressure Listener ==========

    override fun onQueueBackpressureStateChanged(isPaused: Boolean) {
        val isDropping = service?.isMqttDroppingMessages ?: false
        updateQueueStatus(isPaused, isDropping)
    }

    // ========== MQTT Drop Mode Listener ==========

    override fun onMqttDropModeStateChanged(isDropping: Boolean) {
        val isPaused = service?.isPausedForQueueBackpressure ?: false
        updateQueueStatus(isPaused, isDropping)
    }

    // ========== Public Actions ==========

    /**
     * Initialize location state based on current permissions and GPS status.
     */
    fun initializeLocationState() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        var hasGps = false
        var providerEnabled = false

        if (hasPermission) {
            val locationManager =
                context.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager != null) {
                val locationProvider =
                    locationManager.getProvider(LocationManager.GPS_PROVIDER)
                hasGps = locationProvider != null
                providerEnabled =
                    hasGps && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }
        }

        val currentLocation = if (hasPermission) _locationState.value.let {
            if (it.hasLocation) Location("").apply {
                latitude = it.latitude
                longitude = it.longitude
                altitude = it.altitude
                accuracy = it.accuracy
            } else null
        } else null

        val state = LocationStatusHelper.determineState(
            currentLocation,
            hasPermission,
            providerEnabled,
            hasGps,
        )

        _locationState.update {
            it.copy(
                state = state,
                hasLocation = currentLocation != null,
            )
        }
    }

    /**
     * Toggle cellular logging via the service.
     */
    fun toggleCellularLogging(enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val svc = service ?: return@launch
            val enabled = svc.toggleCellularLogging(enable)

            withContext(Dispatchers.Main) {
                if (enabled == null) {
                    _events.emit(DashboardEvent.ShowToast(context.getString(com.craxiom.networksurvey.R.string.cellular_logging_toggle_failed)))
                    syncLoggingState(svc)
                } else {
                    syncLoggingState(svc)
                    val msgRes = if (enabled) {
                        com.craxiom.networksurvey.R.string.cellular_logging_start_toast
                    } else {
                        com.craxiom.networksurvey.R.string.cellular_logging_stop_toast
                    }
                    _events.emit(DashboardEvent.ShowToast(context.getString(msgRes)))

                    if (enabled && svc.isPhoneStateAutoStartedByCellular) {
                        _events.emit(DashboardEvent.ShowToast(context.getString(com.craxiom.networksurvey.R.string.phone_state_auto_started_toast)))
                    }
                }
            }
        }
    }

    /**
     * Toggle phone state logging via the service.
     */
    fun togglePhoneStateLogging(enable: Boolean) {
        toggleLogging(
            enable,
            { it.togglePhoneStateLogging(enable) },
            com.craxiom.networksurvey.R.string.phone_state_logging_toggle_failed,
            com.craxiom.networksurvey.R.string.phone_state_logging_start_toast,
            com.craxiom.networksurvey.R.string.phone_state_logging_stop_toast,
        )
    }

    /**
     * Toggle Wi-Fi logging via the service.
     */
    fun toggleWifiLogging(enable: Boolean) {
        toggleLogging(
            enable,
            { it.toggleWifiLogging(enable) },
            com.craxiom.networksurvey.R.string.wifi_logging_toggle_failed,
            com.craxiom.networksurvey.R.string.wifi_logging_start_toast,
            com.craxiom.networksurvey.R.string.wifi_logging_stop_toast,
        )
    }

    /**
     * Toggle Bluetooth logging via the service.
     */
    fun toggleBluetoothLogging(enable: Boolean) {
        toggleLogging(
            enable,
            { it.toggleBluetoothLogging(enable) },
            com.craxiom.networksurvey.R.string.bluetooth_logging_toggle_failed,
            com.craxiom.networksurvey.R.string.bluetooth_logging_start_toast,
            com.craxiom.networksurvey.R.string.bluetooth_logging_stop_toast,
        )
    }

    /**
     * Toggle GNSS logging via the service.
     */
    fun toggleGnssLogging(enable: Boolean) {
        toggleLogging(
            enable,
            { it.toggleGnssLogging(enable) },
            com.craxiom.networksurvey.R.string.gnss_logging_toggle_failed,
            com.craxiom.networksurvey.R.string.gnss_logging_start_toast,
            com.craxiom.networksurvey.R.string.gnss_logging_stop_toast,
        )
    }

    /**
     * Toggle CDR logging via the service.
     */
    fun toggleCdrLogging(enable: Boolean) {
        toggleLogging(
            enable,
            { it.toggleCdrLogging(enable) },
            com.craxiom.networksurvey.R.string.cdr_logging_toggle_failed,
            com.craxiom.networksurvey.R.string.cdr_logging_start_toast,
            com.craxiom.networksurvey.R.string.cdr_logging_stop_toast,
        )
    }

    /**
     * Connect or disconnect MQTT.
     */
    fun toggleMqttConnection(connect: Boolean) {
        val svc = service
        if (svc == null) {
            viewModelScope.launch {
                _events.emit(
                    DashboardEvent.ShowToast(
                        context.getString(com.craxiom.networksurvey.R.string.mqtt_connection_not_ready)
                    )
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (connect) {
                val attempting = svc.connectToMqttBrokerUsingSavedConnectionInfo()
                if (!attempting) {
                    _mqttState.update { it.copy(connectionState = ConnectionState.DISCONNECTED) }
                    _events.emit(
                        DashboardEvent.ShowSnackbar(
                            message = context.getString(com.craxiom.networksurvey.R.string.mqtt_connection_info_not_set),
                            actionLabel = "Open",
                            action = null, // Navigation handled by screen
                        )
                    )
                }
            } else {
                svc.disconnectFromMqttBroker()
                updateMqttConnectionState(ConnectionState.DISCONNECTED)
            }
        }
    }

    /**
     * Toggle upload scanning (start/stop saving records for community upload).
     */
    fun toggleUploadScanning(enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val svc = service
            val result = if (svc != null) {
                svc.toggleUploadRecordSaving(enable)
            } else {
                com.craxiom.networksurvey.model.UploadScanningResult(
                    false, false,
                    context.getString(com.craxiom.networksurvey.R.string.upload_saving_toggle_failed),
                )
            }

            withContext(Dispatchers.Main) {
                if (result.success) {
                    _uploadState.update {
                        it.copy(
                            scanningActive = result.isEnabled,
                            activeSurveys = if (result.isEnabled) result.surveysStarted else emptySet(),
                        )
                    }
                } else {
                    _uploadState.update {
                        it.copy(scanningActive = false, activeSurveys = emptySet())
                    }
                }
                _events.emit(DashboardEvent.ShowToast(result.message))
            }
        }
    }

    /**
     * Start the community upload worker with the given configuration options.
     * Saves the upload preferences and enqueues the WorkManager task.
     *
     * @return true if the upload was enqueued, false if no internet is available
     */
    fun startUpload(
        uploadToOpenCellId: Boolean,
        anonymously: Boolean,
        uploadToBeaconDb: Boolean,
        retry: Boolean,
    ): Boolean {
        val editor = preferences.edit()
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, uploadToOpenCellId)
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, anonymously)
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, uploadToBeaconDb)
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED, retry)
        editor.apply()

        if (!com.craxiom.networksurvey.util.NsUtils.isNetworkAvailable(context)) {
            return false
        }

        _uploadState.update { it.copy(uploadButtonEnabled = false) }

        val inputData = Data.Builder()
            .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, uploadToOpenCellId)
            .putBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, anonymously)
            .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, uploadToBeaconDb)
            .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED, retry)
            .putString(NsUploaderWorker.INPUT_SOURCE, NsUploaderWorker.SOURCE_MANUAL)
            .build()

        val uploadWorkRequest = OneTimeWorkRequest.Builder(NsUploaderWorker::class.java)
            .addTag(NsUploaderWorker.WORKER_TAG)
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            NetworkSurveyConstants.COMMUNITY_UPLOAD_UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            uploadWorkRequest,
        )

        return true
    }

    /**
     * Save the "don't show upload dialog" preference.
     */
    fun setShowUploadDialog(show: Boolean) {
        preferences.edit()
            .putBoolean(NetworkSurveyConstants.PROPERTY_SHOW_CONFIG_UPLOAD_DIALOG, !show)
            .apply()
    }

    /**
     * Check whether the upload configuration dialog should be shown.
     */
    fun shouldShowUploadConfigDialog(): Boolean {
        return preferences.getBoolean(
            NetworkSurveyConstants.PROPERTY_SHOW_CONFIG_UPLOAD_DIALOG, true
        )
    }

    /**
     * Toggle the NS Analytics survey on/off.
     */
    fun toggleNsAnalyticsSurvey() {
        val svc = service ?: return

        viewModelScope.launch {
            try {
                val isCurrentlyScanning = svc.isNsAnalyticsScanningActive
                val result = svc.toggleNsAnalyticsScanning(!isCurrentlyScanning)
                if (!result.success) {
                    _events.emit(DashboardEvent.ShowToast(result.message))
                }
                updateNsAnalyticsState()
            } catch (e: Exception) {
                Timber.e(e, "Error toggling NS Analytics scanning")
                _events.emit(DashboardEvent.ShowToast("Failed to toggle NS Analytics survey"))
            }
        }
    }

    /**
     * Disable the streaming queue limit by setting it to 0.
     */
    fun disableQueueLimit() {
        preferences.edit()
            .putString(NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT, "0")
            .apply()
    }

    /**
     * Update the upload progress state from WorkManager observation.
     */
    fun updateUploadProgress(progress: UploadProgressState) {
        _uploadState.update { it.copy(uploadProgress = progress) }
        if (progress is UploadProgressState.Finished || progress is UploadProgressState.Hidden) {
            queryUploadQueueCount()
        }
    }

    // ========== Private Helpers ==========

    private fun updateLocationFromLocation(location: Location) {
        _locationState.update {
            LocationUiState(
                state = LocationStatusHelper.LocationState.FIX,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                accuracy = location.accuracy,
                hasLocation = true,
            )
        }
    }

    private fun updateMqttConnectionState(connectionState: ConnectionState) {
        _mqttState.update { it.copy(connectionState = connectionState) }
    }

    private fun readMqttStreamSettings() {
        _mqttState.update {
            it.copy(
                cellularStreamEnabled = preferences.getBoolean(
                    NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED,
                    NetworkSurveyConstants.DEFAULT_MQTT_CELLULAR_STREAM_SETTING,
                ),
                phoneStateStreamEnabled = preferences.getBoolean(
                    NetworkSurveyConstants.PROPERTY_MQTT_PHONE_STATE_STREAM_ENABLED,
                    NetworkSurveyConstants.DEFAULT_MQTT_PHONE_STATE_STREAM_SETTING,
                ),
                wifiStreamEnabled = preferences.getBoolean(
                    NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED,
                    NetworkSurveyConstants.DEFAULT_MQTT_WIFI_STREAM_SETTING,
                ),
                bluetoothStreamEnabled = preferences.getBoolean(
                    NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED,
                    NetworkSurveyConstants.DEFAULT_MQTT_BLUETOOTH_STREAM_SETTING,
                ),
                gnssStreamEnabled = preferences.getBoolean(
                    NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED,
                    NetworkSurveyConstants.DEFAULT_MQTT_GNSS_STREAM_SETTING,
                ),
                deviceStatusStreamEnabled = preferences.getBoolean(
                    NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED,
                    NetworkSurveyConstants.DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING,
                ),
            )
        }
    }

    private fun readAutoUploadSetting() {
        val enabled = preferences.getBoolean(
            NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED,
            false,
        )
        _uploadState.update { it.copy(autoUploadEnabled = enabled) }
    }

    private fun syncLoggingState(svc: NetworkSurveyService) {
        _loggingState.update {
            LoggingUiState(
                cellularEnabled = svc.isCellularLoggingEnabled,
                phoneStateEnabled = svc.isPhoneStateLoggingEnabled,
                phoneStateAutoStarted = svc.isPhoneStateLoggingEnabled && svc.isPhoneStateAutoStartedByCellular,
                wifiEnabled = svc.isWifiLoggingEnabled,
                bluetoothEnabled = svc.isBluetoothLoggingEnabled,
                gnssEnabled = svc.isGnssLoggingEnabled,
                cdrEnabled = svc.isCdrLoggingEnabled,
            )
        }
    }

    private fun updateBatteryStatus(svc: NetworkSurveyService) {
        val batteryManagementEnabled = PreferenceUtils.isBatteryManagementEnabled(context)
        val batteryLevel = svc.currentBatteryLevel

        if (!batteryManagementEnabled || batteryLevel < 0) {
            _batteryState.update { BatteryUiState(visible = false) }
            return
        }

        val batteryThreshold = PreferenceUtils.getBatteryThresholdPercent(context)
        val isPaused = svc.isPausedForBattery
        val isWarning = !isPaused && batteryLevel <= batteryThreshold + 5

        _batteryState.update {
            BatteryUiState(
                visible = isPaused || isWarning,
                isPaused = isPaused,
                batteryLevel = batteryLevel,
                batteryThreshold = batteryThreshold,
            )
        }
    }

    private fun updateQueueStatus(isPaused: Boolean, isDropping: Boolean) {
        _queueState.update {
            QueueUiState(
                visible = isPaused || isDropping,
                isPaused = isPaused,
                isDropping = isDropping,
                isUnderMdmControl = MdmUtils.isUnderMdmControlAndEnabled(
                    context,
                    NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT,
                ),
            )
        }
    }

    private fun updateNsAnalyticsState() {
        val isRegistered = PreferenceUtils.isNsAnalyticsRegistered(context)
        if (!isRegistered) {
            _nsAnalyticsState.update { NsAnalyticsUiState(visible = false) }
            return
        }

        val svc = service
        val isSurveyRunning = try {
            svc?.isNsAnalyticsScanningActive ?: false
        } catch (e: Exception) {
            false
        }
        val surveyStartTime = if (isSurveyRunning) svc?.nsAnalyticsSurveyStartTime ?: 0L else 0L

        _nsAnalyticsState.update {
            it.copy(
                visible = true,
                isSurveyActive = isSurveyRunning,
                surveyStartTime = surveyStartTime,
            )
        }

        // Query counts in background
        viewModelScope.launch(Dispatchers.IO) {
            queryNsAnalyticsRecordCounts()
        }
    }

    fun queryUploadQueueCount() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = SurveyDatabase.getInstance(context).surveyRecordDao()
                val cellularCount = NsUploaderWorker.getTotalCellularRecordsForUpload(dao)
                val wifiCount = dao.wifiRecordCountForUpload

                _uploadState.update {
                    it.copy(
                        cellularQueueCount = cellularCount,
                        wifiQueueCount = wifiCount,
                        uploadButtonEnabled = cellularCount + wifiCount > 0,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error querying upload queue count")
            }
        }
    }

    private suspend fun queryNsAnalyticsRecordCounts() {
        try {
            val database = SurveyDatabase.getInstance(context)
            val stats = database.nsAnalyticsDao().pendingRecordStats

            var cellularCount = 0
            var phoneStateCount = 0
            var wifiCount = 0
            var bluetoothCount = 0
            var gnssCount = 0

            stats.forEach { stat ->
                when (stat.recordType) {
                    NsAnalyticsConstants.RECORD_TYPE_GSM,
                    NsAnalyticsConstants.RECORD_TYPE_CDMA,
                    NsAnalyticsConstants.RECORD_TYPE_UMTS,
                    NsAnalyticsConstants.RECORD_TYPE_LTE,
                    NsAnalyticsConstants.RECORD_TYPE_NR,
                        -> cellularCount += stat.count

                    NsAnalyticsConstants.RECORD_TYPE_PHONE_STATE -> phoneStateCount = stat.count
                    NsAnalyticsConstants.RECORD_TYPE_WIFI -> wifiCount = stat.count
                    NsAnalyticsConstants.RECORD_TYPE_BLUETOOTH -> bluetoothCount = stat.count
                    NsAnalyticsConstants.RECORD_TYPE_GNSS -> gnssCount = stat.count
                }
            }

            _nsAnalyticsState.update {
                it.copy(
                    cellularCount = cellularCount,
                    wifiCount = wifiCount,
                    bluetoothCount = bluetoothCount,
                    gnssCount = gnssCount,
                    phoneStateCount = phoneStateCount,
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting NS Analytics record counts")
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(6_000)
                queryUploadQueueCount()

                if (PreferenceUtils.isNsAnalyticsRegistered(context)) {
                    queryNsAnalyticsRecordCounts()
                    // Also refresh survey active state
                    val svc = service
                    if (svc != null) {
                        val isSurveyRunning = try {
                            svc.isNsAnalyticsScanningActive
                        } catch (e: Exception) {
                            false
                        }

                        val surveyStartTime = if (isSurveyRunning) {
                            svc.nsAnalyticsSurveyStartTime
                        } else 0L

                        _nsAnalyticsState.update {
                            it.copy(
                                isSurveyActive = isSurveyRunning,
                                surveyStartTime = surveyStartTime,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun toggleLogging(
        enable: Boolean,
        serviceAction: (NetworkSurveyService) -> Boolean?,
        failedMsgRes: Int,
        startMsgRes: Int,
        stopMsgRes: Int,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val svc = service ?: return@launch
            val enabled = serviceAction(svc)

            withContext(Dispatchers.Main) {
                if (enabled == null) {
                    _events.emit(DashboardEvent.ShowToast(context.getString(failedMsgRes)))
                } else {
                    _events.emit(
                        DashboardEvent.ShowToast(
                            context.getString(if (enabled) startMsgRes else stopMsgRes)
                        )
                    )
                }
                syncLoggingState(svc)
            }
        }
    }
}
