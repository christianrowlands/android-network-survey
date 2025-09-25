package com.craxiom.networksurvey.ui.nsanalytics

import android.app.Application
import android.os.Build
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.data.api.DeviceRegistrationRequest
import com.craxiom.networksurvey.data.api.NsAnalyticsApiFactory
import com.craxiom.networksurvey.data.api.NsAnalyticsQrData
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.uploader.NsAnalyticsUploadWorker
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _uiState = MutableStateFlow(NsAnalyticsConnectionUiState())
    val uiState: StateFlow<NsAnalyticsConnectionUiState> = _uiState.asStateFlow()

    init {
        loadConnectionState()
        // Check for pending QR data when screen loads
        checkAndProcessQrData()
    }

    private fun loadConnectionState() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                withContext(Dispatchers.IO) {
                    val isRegistered = NsAnalyticsSecureStorage.isRegistered(context)
                    val workspace = NsAnalyticsSecureStorage.getWorkspaceId(context)
                    val apiUrl = NsAnalyticsSecureStorage.getApiUrl(context)
                    val autoUploadEnabled = PreferenceUtils.isNsAnalyticsAutoUpload(context)
                    val uploadFrequency = NsAnalyticsSecureStorage.getUploadFrequency(context)
                    val lastUploadTime = NsAnalyticsSecureStorage.getLastUploadTime(context)

                    // Get protocol preferences
                    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                    val cellularEnabled = preferences.getBoolean(
                        NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_CELLULAR_ENABLED,
                        true
                    )
                    val wifiEnabled = preferences.getBoolean(
                        NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_WIFI_ENABLED,
                        true
                    )
                    val bluetoothEnabled = preferences.getBoolean(
                        NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_BLUETOOTH_ENABLED,
                        false
                    )
                    val gnssEnabled = preferences.getBoolean(
                        NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_GNSS_ENABLED,
                        false
                    )

                    // Get queue size
                    val queueSize = database.nsAnalyticsDao().getPendingRecordCount()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRegistered = isRegistered,
                        isConnected = isRegistered,
                        workspace = workspace,
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

    fun refreshStatus() {
        loadConnectionState()
        checkAndProcessQrData()
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
            _uiState.value = _uiState.value.copy(isUploading = true)

            try {
                // Trigger immediate upload via WorkManager
                val uploadWork = OneTimeWorkRequestBuilder<NsAnalyticsUploadWorker>()
                    .addTag("ns_analytics_upload")
                    .build()

                workManager.enqueue(uploadWork)
                showMessage("Upload started")

                // Refresh status after a delay to show updated queue
                kotlinx.coroutines.delay(2000)
                loadConnectionState()
            } catch (e: Exception) {
                Timber.e(e, "Failed to trigger upload")
                showMessage("Failed to start upload")
            } finally {
                _uiState.value = _uiState.value.copy(isUploading = false)
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
                        deviceId = registrationResponse.deviceId
                    )

                    // Clear the QR data since we've successfully registered
                    NsAnalyticsSecureStorage.clearQrData(context)

                    // Update UI state
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRegistered = true,
                        isConnected = true,
                        workspace = registrationResponse.workspaceId,
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
}

/**
 * UI state for the NS Analytics connection screen.
 */
data class NsAnalyticsConnectionUiState(
    val isLoading: Boolean = false,
    val isRegistered: Boolean = false,
    val isConnected: Boolean = false,
    val workspace: String? = null,
    val apiUrl: String? = null,
    val autoUploadEnabled: Boolean = false,
    val uploadFrequencyMinutes: Int = NsAnalyticsConstants.DEFAULT_UPLOAD_FREQUENCY,
    val lastUploadTime: Long = 0,
    val queuedRecords: Int = 0,
    val isUploading: Boolean = false,
    val message: String? = null,
    // Protocol selection states
    val cellularEnabled: Boolean = true,
    val wifiEnabled: Boolean = true,
    val bluetoothEnabled: Boolean = false,
    val gnssEnabled: Boolean = false
)