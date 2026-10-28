package com.craxiom.networksurvey.ui.nsanalytics

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.uploader.NsAnalyticsUploadWorker
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import com.craxiom.networksurvey.util.NsAnalyticsUtils
import com.craxiom.networksurvey.util.NsUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.craxiom.networksurvey.ui.common.CountFormat
import com.craxiom.networksurvey.ui.survey.DeliveryQueueReader

/**
 * ViewModel for the NS Analytics page.
 *
 * Connects this phone to a workspace from a QR code or link ([NsAnalyticsRegistration]), and once
 * connected shows its uploads: the queue, automatic and manual uploads ([NsAnalyticsUploadTracker]),
 * the quota pause, and disconnecting. Starting and stopping surveys is the Survey screen's job.
 */
class NsAnalyticsConnectionViewModel @JvmOverloads constructor(
    application: Application,
    private val registration: NsAnalyticsRegistration = NsAnalyticsRegistration(application),
) : AndroidViewModel(application) {

    private val context = getApplication<Application>()
    private val database = SurveyDatabase.getInstance(context)
    private val workManager = WorkManager.getInstance(context)

    // Held only between a bind and the unbind (ServiceConnectionHandler sends null), so the view
    // model never outlives the service it reads.
    @SuppressLint("StaticFieldLeak")
    private var surveyService: NetworkSurveyService? = null
    private var pollingJob: Job? = null

    private val _uiState = MutableStateFlow(NsAnalyticsConnectionUiState(isLoading = true))
    val uiState: StateFlow<NsAnalyticsConnectionUiState> = _uiState.asStateFlow()

    private val _connectedWorkspace = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Emits the workspace name once this phone connects, so the page can return to the Survey screen. */
    val connectedWorkspace: SharedFlow<String> = _connectedWorkspace.asSharedFlow()

    private val uploads = NsAnalyticsUploadTracker(context, viewModelScope, _uiState, object : NsAnalyticsUploadTracker.Callbacks {
        override fun showMessage(message: String) = this@NsAnalyticsConnectionViewModel.showMessage(message)
        override suspend fun checkDeviceStatus(): DeregistrationInfo? = this@NsAnalyticsConnectionViewModel.checkDeviceStatus()
        override fun reloadConnectionState() = loadConnectionState()
    })

    private val registrationFlow = NsAnalyticsRegistrationFlow(context, viewModelScope, _uiState, registration) { workspace ->
        _connectedWorkspace.tryEmit(workspace)
        loadConnectionState()
    }

    init {
        loadConnectionState()
        // Check for pending QR data when screen loads
        checkAndProcessQrData()
    }

    /**
     * Called when the screen becomes visible. Starts polling the queue and observing upload work.
     */
    fun onStart() {
        startPolling()
        uploads.observeAllUploadWork()
    }

    /**
     * Called when the screen is no longer visible. Stops polling and removes observers
     * to save resources.
     */
    fun onStop() {
        pollingJob?.cancel()
        pollingJob = null
        uploads.removeUploadWorkTagObserver()
        uploads.trackedTagUploadWorkId = null
    }

    /**
     * Sets the NetworkSurveyService instance when bound from the UI.
     */
    fun setNetworkSurveyService(service: NetworkSurveyService?) {
        surveyService = service
        if (service != null) {
            viewModelScope.launch { updateQueueStatus() }
        }
    }

    /** Uploads the queue now. */
    fun uploadNow() = uploads.uploadNow()

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                updateQueueStatus()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Refreshes the number of records waiting to upload, the upload state that follows from it,
     * and whether a survey is sending to NS Analytics.
     */
    private suspend fun updateQueueStatus() {
        try {
            val queued = withContext(Dispatchers.IO) { database.nsAnalyticsDao().getPendingRecordCount() }

            // Update upload state based on queue, but don't override if uploading or recently completed
            val currentState = _uiState.value
            val results = uploads.results
            val recentlyCompletedUpload =
                System.currentTimeMillis() - results.lastUploadCompletionTime < UPLOAD_RESULT_PRESERVATION_MS
            val preserve = results.lastUploadWasFailure || recentlyCompletedUpload ||
                currentState.uploadState == UploadState.UPLOADING

            val newUploadState = when {
                preserve -> currentState.uploadState
                !currentState.isRegistered -> UploadState.UNAVAILABLE
                queued == 0 -> UploadState.EMPTY
                else -> UploadState.IDLE
            }
            val newUploadStatusMessage = when {
                preserve -> currentState.uploadStatusMessage
                newUploadState == UploadState.UNAVAILABLE -> context.getString(R.string.ns_analytics_upload_status_not_registered)
                newUploadState == UploadState.EMPTY -> context.getString(R.string.ns_analytics_upload_status_no_records)
                else -> context.getString(R.string.ns_analytics_upload_status_ready)
            }

            _uiState.value = _uiState.value.copy(
                isSurveyActive = surveyService?.isNsAnalyticsScanningActive == true,
                queuedRecords = queued,
                uploadState = newUploadState,
                uploadStatusMessage = newUploadStatusMessage
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update the NS Analytics queue status")
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        uploads.removeUploadWorkTagObserver()
    }

    fun loadConnectionState() {
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

                    // Get queue size
                    val queueSize = database.nsAnalyticsDao().getPendingRecordCount()

                    // Surface the persisted quota pause (set by the worker, possibly while the app
                    // was closed) so the screen shows the paused banner immediately on open.
                    val pauseInfo = NsAnalyticsSecureStorage.getQuotaPauseInfo(context)

                    // Determine initial upload state based on registration, quota pause, and queue
                    val initialUploadState = when {
                        !isRegistered -> UploadState.UNAVAILABLE
                        pauseInfo != null -> UploadState.UNAVAILABLE
                        queueSize == 0 -> UploadState.EMPTY
                        else -> UploadState.IDLE
                    }
                    val initialUploadStatusMessage = when {
                        !isRegistered -> context.getString(R.string.ns_analytics_upload_status_not_registered)
                        pauseInfo != null -> context.getString(R.string.ns_analytics_upload_status_quota_exceeded)
                        queueSize == 0 -> context.getString(R.string.ns_analytics_upload_status_no_records)
                        else -> context.getString(R.string.ns_analytics_upload_status_ready)
                    }

                    // Update UI immediately with cached data
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRegistered = isRegistered,
                        isConnected = isRegistered,
                        workspace = workspace,
                        workspaceName = storedWorkspaceName ?: context.getString(R.string.ns_analytics_unknown_workspace),
                        apiUrl = apiUrl,
                        autoUploadEnabled = autoUploadEnabled,
                        uploadFrequencyMinutes = uploadFrequency,
                        lastUploadTime = lastUploadTime,
                        queuedRecords = queueSize,
                        uploadState = initialUploadState,
                        uploadStatusMessage = initialUploadStatusMessage,
                        uploadPausedForQuota = pauseInfo != null,
                        quotaCurrentUsage = pauseInfo?.currentUsage ?: 0,
                        quotaMaxRecords = pauseInfo?.maxRecords ?: 0,
                        quotaMessage = pauseInfo?.message,
                        quotaWebUrl = pauseInfo?.webUrl,
                    )

                    // Update the queue and survey status after loading connection state
                    updateQueueStatus()
                    fetchAndUpdateDeviceStatus()

                    // Check device status to detect web-initiated deregistration
                    if (isRegistered && deviceToken != null && apiUrl != null) {
                        val deregInfo = checkDeviceStatus()
                        if (deregInfo != null) {
                            // Device was deregistered - checkDeviceStatus() already updated UI
                            Timber.i("Device deregistration detected on screen open")
                            return@withContext // Exit early, don't schedule uploads
                        }
                    }

                    // Schedule periodic uploads if auto-upload is enabled and there's pending data.
                    // Skip while quota-paused so opening the screen doesn't restart the doomed loop
                    // (schedulePeriodicUpload also guards on this, but skip explicitly for clarity).
                    if (autoUploadEnabled && queueSize > 0 && pauseInfo == null) {
                        NsAnalyticsUploadWorker.schedulePeriodicUpload(context, uploadFrequency)
                        Timber.d(
                            "Scheduled initial periodic uploads on app start (queue size: %d)",
                            queueSize
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load NS Analytics connection state")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = context.getString(R.string.ns_analytics_message_load_failed)
                )
            }
        }
    }

    /**
     * Asynchronously fetch device status from backend and update workspace name if changed.
     */
    private fun fetchAndUpdateDeviceStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val name = registration.fetchWorkspaceName() ?: return@launch
            if (name != _uiState.value.workspaceName) {
                NsAnalyticsSecureStorage.storeWorkspaceName(context, name)
                _uiState.value = _uiState.value.copy(workspaceName = name)
                Timber.d("Updated workspace name from device status: %s", name)
            }
        }
    }

    /**
     * Check device registration status and detect if device was deregistered from web.
     *
     * @return DeregistrationInfo if device is deregistered, null otherwise
     */
    suspend fun checkDeviceStatus(): DeregistrationInfo? {
        return withContext(Dispatchers.IO) {
            try {
                when (val result = NsAnalyticsUtils.checkDeviceRegistrationStatus(context)) {
                    is NsAnalyticsUtils.DeviceStatusResult.Deregistered -> {
                        Timber.i("Device deregistration detected via status check")

                        NsAnalyticsUtils.cleanupAfterDeregistration(
                            context
                        )

                        val deregInfo = DeregistrationInfo(
                            deregisteredAt = result.deregisteredAt,
                            source = result.source,
                            deregisteredBy = result.deregisteredBy,
                            reason = result.reason
                        )

                        _uiState.value = NsAnalyticsConnectionUiState(
                            isLoading = false,
                            isRegistered = false,
                            isConnected = false,
                            queuedRecords = _uiState.value.queuedRecords, // Preserve queue size
                            deregistrationInfo = deregInfo
                        )

                        return@withContext deregInfo
                    }

                    is NsAnalyticsUtils.DeviceStatusResult.Active -> {
                        Timber.d("Device status check: active")
                        return@withContext null
                    }

                    is NsAnalyticsUtils.DeviceStatusResult.CheckFailed -> {
                        Timber.d("Device status check failed: ${result.reason}")
                        return@withContext null
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Device status check failed")
                return@withContext null
            }
        }
    }

    /**
     * Clear deregistration info from UI state
     */
    fun clearDeregistrationInfo() {
        _uiState.value = _uiState.value.copy(deregistrationInfo = null)
    }

    /**
     * Dismiss the quota exceeded dialog.
     *
     * Only hides the modal dialog; the quota details are preserved so the in-app paused banner
     * (which stays visible while [NsAnalyticsConnectionUiState.uploadPausedForQuota] is true) keeps
     * showing the usage numbers and the manage-subscription link. The quota state is cleared when
     * uploads actually resume.
     */
    fun dismissQuotaDialog() {
        _uiState.value = _uiState.value.copy(
            showQuotaExceededDialog = false
        )
    }

    fun toggleAutoUpload(enabled: Boolean) {
        viewModelScope.launch {
            try {
                PreferenceUtils.setNsAnalyticsAutoUpload(context, enabled)
                _uiState.value = _uiState.value.copy(autoUploadEnabled = enabled)

                if (enabled) {
                    // Schedule periodic uploads if survey is active or there's data
                    val service = surveyService
                    val hasPendingRecords = withContext(Dispatchers.IO) {
                        database.nsAnalyticsDao().getPendingRecordCount() > 0
                    }
                    if (service?.isNsAnalyticsScanningActive == true || hasPendingRecords) {
                        val uploadFrequency = NsAnalyticsSecureStorage.getUploadFrequency(context)
                        NsAnalyticsUploadWorker.schedulePeriodicUpload(context, uploadFrequency)
                        showMessage(context.getString(R.string.ns_analytics_message_auto_upload_on))
                    } else {
                        showMessage(context.getString(R.string.ns_analytics_message_auto_upload_on))
                    }
                } else {
                    NsAnalyticsUploadWorker.cancelPeriodicUpload(context)
                    workManager.cancelAllWorkByTag(NsAnalyticsConstants.NS_ANALYTICS_PERIODIC_WORKER_TAG)
                    // The quota re-check uses a separate work name/tag and would otherwise survive
                    // the periodic cancel; stop it too when the user disables auto upload.
                    NsAnalyticsUploadWorker.cancelPausedRetryCheck(context)
                    // Disabling auto upload is an explicit user action, so clear any quota pause
                    // too. Otherwise the persisted pause flag would silently block scheduling if
                    // the user later re-enables auto upload. (Manual "Upload Now" still works and
                    // re-pauses if the workspace is still over quota.)
                    if (NsAnalyticsSecureStorage.isUploadPausedForQuota(context)) {
                        NsAnalyticsSecureStorage.clearQuotaPause(context)
                        NsAnalyticsNotificationHelper.clearUploadsPausedNotification(context)
                        // Reset the status row too, otherwise the red "quota exceeded" status
                        // lingers after the paused banner is removed.
                        val hasRecords = _uiState.value.queuedRecords > 0
                        _uiState.value = _uiState.value.copy(
                            uploadPausedForQuota = false,
                            uploadState = if (hasRecords) UploadState.IDLE else UploadState.EMPTY,
                            uploadStatusMessage = context.getString(
                                if (hasRecords) R.string.ns_analytics_upload_status_ready
                                else R.string.ns_analytics_upload_status_no_records
                            )
                        )
                    }
                    showMessage(context.getString(R.string.ns_analytics_message_auto_upload_off))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle auto upload")
                showMessage(context.getString(R.string.ns_analytics_message_auto_upload_failed))
            }
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val queueSize = database.nsAnalyticsDao().getPendingRecordCount()
                    database.nsAnalyticsDao().clearQueue()

                    // An empty queue cannot be over quota locally. Clear any quota pause so the
                    // device isn't stranded paused with no records left to act on.
                    if (NsAnalyticsSecureStorage.isUploadPausedForQuota(context)) {
                        NsAnalyticsSecureStorage.clearQuotaPause(context)
                        NsAnalyticsUploadWorker.cancelPausedRetryCheck(context)
                        NsAnalyticsNotificationHelper.clearUploadsPausedNotification(context)
                    }

                    _uiState.value = _uiState.value.copy(
                        queuedRecords = 0,
                        uploadPausedForQuota = false
                    )
                    // The page's status reads the queue on its own poll; read it again now
                    DeliveryQueueReader.refresh()
                    showMessage(CountFormat.plural(context.resources, R.plurals.ns_analytics_message_cleared, queueSize))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear queue")
                showMessage(context.getString(R.string.ns_analytics_message_clear_failed))
            }
        }
    }

    /**
     * Unregister this device from NS Analytics.
     * This is a user-initiated action that calls the backend API to unregister the device,
     * clears local credentials, but preserves queued data for potential re-registration.
     */
    fun unregisterDevice() {
        viewModelScope.launch {
            try {
                val service = surveyService
                if (service?.isNsAnalyticsScanningActive == true) {
                    showMessage(context.getString(R.string.ns_analytics_disconnect_stop_first))
                    return@launch
                }

                if (!NsUtils.isNetworkAvailable(context)) {
                    showMessage(context.getString(R.string.ns_analytics_message_offline))
                    return@launch
                }

                _uiState.value = _uiState.value.copy(isLoading = true)
                val failure = withContext(Dispatchers.IO) { registration.unregister() }

                // Update UI based on result
                if (failure == null) {
                    val queueSize = withContext(Dispatchers.IO) {
                        database.nsAnalyticsDao().getPendingRecordCount()
                    }

                    _uiState.value = NsAnalyticsConnectionUiState(
                        isLoading = false,
                        isRegistered = false,
                        isConnected = false,
                        queuedRecords = queueSize, // Preserve queue size
                        message = context.getString(R.string.ns_analytics_message_disconnected)
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = context.getString(R.string.ns_analytics_message_disconnect_failed, failure)
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to unregister device")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = context.getString(R.string.ns_analytics_message_disconnect_failed, e.message.orEmpty())
                )
            }
        }
    }

    /** Shows the confirmation for a code scanned or opened since the page was last shown. */
    fun checkAndProcessQrData() = registrationFlow.checkAndProcessQrData()

    fun confirmRegistration() = registrationFlow.confirmRegistration()

    fun cancelRegistration() = registrationFlow.cancelRegistration()

    fun retryRegistration() = registrationFlow.retryRegistration()

    fun dismissRegistrationError() = registrationFlow.dismissRegistrationError()

    fun dismissAlreadyRegisteredDialog() = registrationFlow.dismissAlreadyRegisteredDialog()

    private fun showMessage(message: String) {
        _uiState.value = _uiState.value.copy(message = message)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private companion object {
        /** How long to preserve upload result state before allowing status updates to override */
        const val UPLOAD_RESULT_PRESERVATION_MS = 5000L

        const val POLL_INTERVAL_MS = 2000L
    }
}
