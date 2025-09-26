package com.craxiom.networksurvey.ui.nsanalytics

import android.app.Application
import android.os.Build
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.data.api.DeviceRegistrationRequest
import com.craxiom.networksurvey.data.api.NsAnalyticsApiFactory
import com.craxiom.networksurvey.data.api.NsAnalyticsQrData
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.uploader.NsAnalyticsUploadWorker
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

/**
 * ViewModel for the NS Analytics connection screen.
 */
class NsAnalyticsConnectionViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val context = getApplication<Application>()
    private val database = SurveyDatabase.getInstance(context)
    private val workManager = WorkManager.getInstance(context)

    private var surveyService: NetworkSurveyService? = null
    private var pollingJob: Job? = null
    private var uploadWorkId: UUID? = null
    private var uploadProgressObserver: Observer<WorkInfo?>? = null

    private val _uiState = MutableStateFlow(NsAnalyticsConnectionUiState())
    val uiState: StateFlow<NsAnalyticsConnectionUiState> = _uiState.asStateFlow()

    init {
        loadConnectionState()
        // Check for pending QR data when screen loads
        checkAndProcessQrData()
    }

    /**
     * Called when the screen becomes visible. Starts polling for survey status.
     */
    fun onStart() {
        startPolling()
    }

    /**
     * Called when the screen is no longer visible. Stops polling to save resources.
     */
    fun onStop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Sets the NetworkSurveyService instance when bound from the UI.
     */
    fun setNetworkSurveyService(service: NetworkSurveyService?) {
        surveyService = service
        if (service != null) {
            // Immediately update status when service is connected
            viewModelScope.launch {
                updateSurveyStatus()
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                updateSurveyStatus()
                delay(2000) // Poll every 2 seconds
            }
        }
    }

    private suspend fun updateSurveyStatus() {
        try {
            val service = surveyService ?: return

            val isSurveyActive = service.isNsAnalyticsScanningActive
            val surveyStartTime = if (isSurveyActive) {
                service.nsAnalyticsSurveyStartTime
            } else {
                0L
            }

            // Get record counts grouped by type from database in a single efficient query
            val recordStats = withContext(Dispatchers.IO) {
                database.nsAnalyticsDao().getPendingRecordStats()
            }

            // Process the stats to group cellular protocols together
            var cellularCount = 0
            var wifiCount = 0
            var bluetoothCount = 0
            var gnssCount = 0

            recordStats.forEach { stat ->
                when (stat.recordType) {
                    NsAnalyticsConstants.RECORD_TYPE_GSM,
                    NsAnalyticsConstants.RECORD_TYPE_CDMA,
                    NsAnalyticsConstants.RECORD_TYPE_UMTS,
                    NsAnalyticsConstants.RECORD_TYPE_LTE,
                    NsAnalyticsConstants.RECORD_TYPE_NR -> {
                        cellularCount += stat.count
                    }

                    NsAnalyticsConstants.RECORD_TYPE_WIFI -> {
                        wifiCount = stat.count
                    }

                    NsAnalyticsConstants.RECORD_TYPE_BLUETOOTH -> {
                        bluetoothCount = stat.count
                    }

                    NsAnalyticsConstants.RECORD_TYPE_GNSS -> {
                        gnssCount = stat.count
                    }
                    // Ignore other record types like device_status and phone_state
                }
            }

            val totalQueuedRecords = cellularCount + wifiCount + bluetoothCount + gnssCount

            _uiState.value = _uiState.value.copy(
                isSurveyActive = isSurveyActive,
                surveyStartTime = surveyStartTime,
                cellularRecordCount = cellularCount,
                wifiRecordCount = wifiCount,
                bluetoothRecordCount = bluetoothCount,
                gnssRecordCount = gnssCount,
                queuedRecords = totalQueuedRecords
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update survey status")
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        // Clean up upload observer to prevent memory leaks
        uploadProgressObserver?.let { observer ->
            uploadWorkId?.let { id ->
                workManager.getWorkInfoByIdLiveData(id).removeObserver(observer)
            }
        }
        uploadProgressObserver = null
    }

    private fun loadConnectionState() {
        viewModelScope.launch {
            try {
                // Load cached data immediately without showing loading spinner
                withContext(Dispatchers.IO) {
                    val isRegistered = NsAnalyticsSecureStorage.isRegistered(context)
                    val workspace = NsAnalyticsSecureStorage.getWorkspaceId(context)
                    val apiUrl = NsAnalyticsSecureStorage.getApiUrl(context)
                    val autoUploadEnabled = PreferenceUtils.isNsAnalyticsAutoUpload(context)
                    val uploadFrequency = NsAnalyticsSecureStorage.getUploadFrequency(context)
                    val lastUploadTime = NsAnalyticsSecureStorage.getLastUploadTime(context)
                    val storedWorkspaceName = NsAnalyticsSecureStorage.getWorkspaceName(context)
                    val deviceToken = NsAnalyticsSecureStorage.getDeviceToken(context)

                    // Get protocol preferences
                    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                    val cellularEnabled = preferences.getBoolean(
                        NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_CELLULAR_ENABLED,
                        NsAnalyticsConstants.DEFAULT_CELLULAR_ENABLED
                    )
                    val wifiEnabled = preferences.getBoolean(
                        NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_WIFI_ENABLED,
                        NsAnalyticsConstants.DEFAULT_WIFI_ENABLED
                    )
                    val bluetoothEnabled = preferences.getBoolean(
                        NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_BLUETOOTH_ENABLED,
                        NsAnalyticsConstants.DEFAULT_BLUETOOTH_ENABLED
                    )
                    val gnssEnabled = preferences.getBoolean(
                        NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_GNSS_ENABLED,
                        NsAnalyticsConstants.DEFAULT_GNSS_ENABLED
                    )

                    // Get queue size
                    val queueSize = database.nsAnalyticsDao().getPendingRecordCount()

                    // Update UI immediately with cached data
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRegistered = isRegistered,
                        isConnected = isRegistered,
                        workspace = workspace,
                        workspaceName = storedWorkspaceName ?: "Unknown Workspace",
                        apiUrl = apiUrl,
                        autoUploadEnabled = autoUploadEnabled,
                        uploadFrequencyMinutes = uploadFrequency,
                        lastUploadTime = lastUploadTime,
                        queuedRecords = queueSize,
                        cellularEnabled = cellularEnabled,
                        wifiEnabled = wifiEnabled,
                        bluetoothEnabled = bluetoothEnabled,
                        gnssEnabled = gnssEnabled
                    )

                    // Update survey status after loading connection state
                    updateSurveyStatus()

                    // Asynchronously fetch latest device status from backend if registered
                    if (isRegistered && deviceToken != null && apiUrl != null) {
                        fetchAndUpdateDeviceStatus(deviceToken, apiUrl)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load NS Analytics connection state")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Failed to load connection state"
                )
            }
        }
    }

    /**
     * Asynchronously fetch device status from backend and update workspace name if changed
     */
    private fun fetchAndUpdateDeviceStatus(deviceToken: String, apiUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = NsAnalyticsApiFactory.createClient(apiUrl)
                val statusResponse = api.getDeviceStatus("Bearer $deviceToken")
                if (statusResponse.isSuccessful && statusResponse.body() != null) {
                    val status = statusResponse.body()!!
                    // Update workspace name if provided and different from current
                    status.workspaceName?.let { name ->
                        val currentName = _uiState.value.workspaceName
                        if (name != currentName) {
                            NsAnalyticsSecureStorage.storeWorkspaceName(context, name)
                            _uiState.value = _uiState.value.copy(workspaceName = name)
                            Timber.d("Updated workspace name from device status: %s", name)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(
                    e,
                    "Failed to fetch device status in background, continuing with cached data"
                )
            }
        }
    }

    fun toggleAutoUpload(enabled: Boolean) {
        viewModelScope.launch {
            try {
                PreferenceUtils.setNsAnalyticsAutoUpload(context, enabled)
                _uiState.value = _uiState.value.copy(autoUploadEnabled = enabled)

                if (enabled) {
                    // Schedule next upload
                    scheduleUpload()
                    showMessage("Auto upload enabled")
                } else {
                    // Cancel scheduled uploads
                    workManager.cancelAllWorkByTag("ns_analytics_upload")
                    showMessage("Auto upload disabled")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle auto upload")
                showMessage("Failed to update auto upload setting")
            }
        }
    }

    fun uploadNow() {
        if (_uiState.value.isUploading) return

        viewModelScope.launch {
            val initialQueueSize = _uiState.value.queuedRecords
            _uiState.value = _uiState.value.copy(
                isUploading = true,
                uploadProgress = 0f,
                uploadedRecords = 0,
                totalRecordsToUpload = initialQueueSize
            )

            try {
                // Trigger immediate upload via WorkManager
                val uploadWork = OneTimeWorkRequestBuilder<NsAnalyticsUploadWorker>()
                    .addTag("ns_analytics_upload")
                    .build()

                workManager.enqueue(uploadWork)
                uploadWorkId = uploadWork.id

                // Clean up any previous observer
                uploadProgressObserver?.let { observer ->
                    uploadWorkId?.let { id ->
                        workManager.getWorkInfoByIdLiveData(id).removeObserver(observer)
                    }
                }

                // Create new observer
                val observer = Observer<WorkInfo?> { workInfo ->
                    if (workInfo != null) {
                        when (workInfo.state) {
                            WorkInfo.State.RUNNING -> {
                                // Simulate progress based on queue changes
                                val currentQueue = _uiState.value.queuedRecords
                                val uploaded = initialQueueSize - currentQueue
                                val progress = if (initialQueueSize > 0) {
                                    uploaded.toFloat() / initialQueueSize
                                } else {
                                    0f
                                }
                                _uiState.value = _uiState.value.copy(
                                    uploadProgress = progress,
                                    uploadedRecords = uploaded
                                )
                            }

                            WorkInfo.State.SUCCEEDED -> {
                                _uiState.value = _uiState.value.copy(
                                    isUploading = false,
                                    uploadProgress = 1f,
                                    lastUploadTime = System.currentTimeMillis()
                                )
                                showMessage("Upload completed successfully")
                                loadConnectionState()
                                // Clean up observer after completion
                                uploadProgressObserver?.let {
                                    workManager.getWorkInfoByIdLiveData(uploadWork.id)
                                        .removeObserver(it)
                                    uploadProgressObserver = null
                                }
                            }

                            WorkInfo.State.FAILED -> {
                                _uiState.value = _uiState.value.copy(
                                    isUploading = false,
                                    uploadProgress = 0f
                                )
                                showMessage("Upload failed")
                                // Clean up observer after failure
                                uploadProgressObserver?.let {
                                    workManager.getWorkInfoByIdLiveData(uploadWork.id)
                                        .removeObserver(it)
                                    uploadProgressObserver = null
                                }
                            }

                            else -> {}
                        }
                    }
                }

                uploadProgressObserver = observer
                workManager.getWorkInfoByIdLiveData(uploadWork.id).observeForever(observer)
            } catch (e: Exception) {
                Timber.e(e, "Failed to trigger upload")
                showMessage("Failed to start upload")
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadProgress = 0f
                )
            }
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val queueSize = database.nsAnalyticsDao().getPendingRecordCount()
                    database.nsAnalyticsDao().clearQueue()

                    _uiState.value = _uiState.value.copy(queuedRecords = 0)
                    showMessage("Cleared $queueSize queued records")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear queue")
                showMessage("Failed to clear queue")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                // Clear all credentials and settings
                NsAnalyticsSecureStorage.clearAllCredentials(context)

                // Clear queue
                withContext(Dispatchers.IO) {
                    database.nsAnalyticsDao().clearQueue()
                }

                // Cancel any scheduled uploads
                workManager.cancelAllWorkByTag("ns_analytics_upload")

                _uiState.value = NsAnalyticsConnectionUiState(
                    isLoading = false,
                    isRegistered = false,
                    message = "Disconnected from NS Analytics"
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to disconnect")
                showMessage("Failed to disconnect")
            }
        }
    }

    private fun scheduleUpload() {
        // This would typically schedule the next upload based on frequency
        // For now, just enqueue a one-time request
        val uploadWork = OneTimeWorkRequestBuilder<NsAnalyticsUploadWorker>()
            .addTag("ns_analytics_upload")
            .build()

        workManager.enqueue(uploadWork)
    }

    /**
     * Check for pending QR data and process it if found
     */
    fun checkAndProcessQrData() {
        viewModelScope.launch {
            try {
                val qrData = NsAnalyticsSecureStorage.getQrData(context)
                if (qrData != null && !NsAnalyticsSecureStorage.isRegistered(context)) {
                    // We have QR data and device is not registered, so register it
                    registerDevice(qrData)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check for QR data")
            }
        }
    }

    /**
     * Register the device with NS Analytics using QR data
     */
    private suspend fun registerDevice(qrData: NsAnalyticsQrData) {
        _uiState.value = _uiState.value.copy(isLoading = true, message = "Registering device...")

        try {
            withContext(Dispatchers.IO) {
                // Generate a unique device ID if we don't have one
                val deviceId = NsAnalyticsSecureStorage.getDeviceId(context)
                    ?: UUID.randomUUID().toString()

                // Create the API client for the specified URL
                val api = NsAnalyticsApiFactory.createClient(qrData.apiUrl)

                // Create the registration request
                val request = DeviceRegistrationRequest(
                    token = qrData.token,
                    deviceId = deviceId,
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                    deviceModel = Build.MODEL,
                    osVersion = "Android ${Build.VERSION.RELEASE}",
                    appVersion = BuildConfig.VERSION_NAME
                )

                // Make the registration API call
                val response = api.registerDevice(request)

                if (response.isSuccessful && response.body() != null) {
                    val registrationResponse = response.body()!!

                    // Store the registration data
                    NsAnalyticsSecureStorage.storeRegistrationData(
                        context = context,
                        deviceToken = registrationResponse.deviceToken,
                        workspaceId = registrationResponse.workspaceId,
                        apiUrl = qrData.apiUrl,
                        deviceId = registrationResponse.deviceId,
                        workspaceName = registrationResponse.workspaceName
                    )

                    // Clear the QR data since we've successfully registered
                    NsAnalyticsSecureStorage.clearQrData(context)

                    // Update UI state
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRegistered = true,
                        isConnected = true,
                        workspace = registrationResponse.workspaceId,
                        workspaceName = registrationResponse.workspaceName ?: "Unknown Workspace",
                        apiUrl = qrData.apiUrl,
                        message = "Device registered successfully. Enable data collection when ready."
                    )

                } else {
                    throw Exception("Registration failed: ${response.code()} ${response.message()}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to register device")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                message = "Registration failed: ${e.message}"
            )
            // Don't clear QR data on failure so user can retry
        }
    }

    private fun showMessage(message: String) {
        _uiState.value = _uiState.value.copy(message = message)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /**
     * Toggle cellular protocol collection for NS Analytics.
     */
    fun toggleCellularProtocol(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                preferences.edit {
                    putBoolean(
                        NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_CELLULAR_ENABLED,
                        enabled
                    )
                }

                _uiState.value = _uiState.value.copy(cellularEnabled = enabled)
                Timber.d("NS Analytics cellular protocol ${if (enabled) "enabled" else "disabled"}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle cellular protocol")
                showMessage("Failed to update cellular protocol setting")
            }
        }
    }

    /**
     * Toggle Wi-Fi protocol collection for NS Analytics.
     */
    fun toggleWifiProtocol(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                preferences.edit {
                    putBoolean(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_WIFI_ENABLED, enabled)
                }

                _uiState.value = _uiState.value.copy(wifiEnabled = enabled)
                Timber.d("NS Analytics Wi-Fi protocol ${if (enabled) "enabled" else "disabled"}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle Wi-Fi protocol")
                showMessage("Failed to update Wi-Fi protocol setting")
            }
        }
    }

    /**
     * Toggle Bluetooth protocol collection for NS Analytics.
     */
    fun toggleBluetoothProtocol(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                preferences.edit {
                    putBoolean(
                        NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_BLUETOOTH_ENABLED,
                        enabled
                    )
                }

                _uiState.value = _uiState.value.copy(bluetoothEnabled = enabled)
                Timber.d("NS Analytics Bluetooth protocol ${if (enabled) "enabled" else "disabled"}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle Bluetooth protocol")
                showMessage("Failed to update Bluetooth protocol setting")
            }
        }
    }

    /**
     * Toggle GNSS protocol collection for NS Analytics.
     */
    fun toggleGnssProtocol(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                preferences.edit {
                    putBoolean(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_GNSS_ENABLED, enabled)
                }

                _uiState.value = _uiState.value.copy(gnssEnabled = enabled)
                Timber.d("NS Analytics GNSS protocol ${if (enabled) "enabled" else "disabled"}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle GNSS protocol")
                showMessage("Failed to update GNSS protocol setting")
            }
        }
    }

    /**
     * Toggle NS Analytics survey scanning on or off.
     * This starts or stops the data collection for NS Analytics.
     */
    fun toggleSurvey() {
        viewModelScope.launch {
            try {
                val service = surveyService
                if (service == null) {
                    showMessage("Service not connected. Please try again.")
                    return@launch
                }

                // Check if device is registered
                if (!_uiState.value.isRegistered) {
                    showMessage("Please connect to NS Analytics first")
                    return@launch
                }

                val isCurrentlyScanning = service.isNsAnalyticsScanningActive
                val result = service.toggleNsAnalyticsScanning(!isCurrentlyScanning)

                if (result.success) {
                    val message = if (!isCurrentlyScanning) {
                        "Survey started"
                    } else {
                        "Survey stopped"
                    }
                    showMessage(message)

                    // Update the UI state immediately
                    _uiState.value = _uiState.value.copy(
                        isSurveyActive = !isCurrentlyScanning,
                        surveyStartTime = if (!isCurrentlyScanning) System.currentTimeMillis() else 0L
                    )
                } else {
                    showMessage(result.message)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle NS Analytics survey")
                showMessage("Failed to toggle survey: ${e.message}")
            }
        }
    }
}

/**
 * UI state for the NS Analytics connection screen.
 */
data class NsAnalyticsConnectionUiState(
    val isLoading: Boolean = false,
    val isRegistered: Boolean = false,
    val isConnected: Boolean = false,
    val workspace: String? = null,
    val workspaceName: String? = null,
    val apiUrl: String? = null,
    val autoUploadEnabled: Boolean = false,
    val uploadFrequencyMinutes: Int = NsAnalyticsConstants.DEFAULT_UPLOAD_FREQUENCY,
    val lastUploadTime: Long = 0,
    val queuedRecords: Int = 0,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f, // Upload progress 0-1
    val uploadedRecords: Int = 0,
    val totalRecordsToUpload: Int = 0,
    val message: String? = null,
    val cellularEnabled: Boolean = NsAnalyticsConstants.DEFAULT_CELLULAR_ENABLED,
    val wifiEnabled: Boolean = NsAnalyticsConstants.DEFAULT_WIFI_ENABLED,
    val bluetoothEnabled: Boolean = NsAnalyticsConstants.DEFAULT_BLUETOOTH_ENABLED,
    val gnssEnabled: Boolean = NsAnalyticsConstants.DEFAULT_GNSS_ENABLED,
    val isSurveyActive: Boolean = false,
    val surveyStartTime: Long = 0,
    val cellularRecordCount: Int = 0,
    val wifiRecordCount: Int = 0,
    val bluetoothRecordCount: Int = 0,
    val gnssRecordCount: Int = 0
)