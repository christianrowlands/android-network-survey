package com.craxiom.networksurvey.ui.nsanalytics

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.data.api.DeviceRegistrationRequest
import com.craxiom.networksurvey.data.api.NsAnalyticsApiFactory
import com.craxiom.networksurvey.data.api.NsAnalyticsQrData
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.uploader.NsAnalyticsUploadWorker
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import com.craxiom.networksurvey.util.NsAnalyticsUtils
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
 * Represents the current state of the upload system.
 * Used to provide clear feedback to users about upload availability.
 */
enum class UploadState {
    /** Ready to upload, has records in queue */
    IDLE,

    /** No records in queue */
    EMPTY,

    /** Upload currently in progress */
    UPLOADING,

    /** Cannot upload (no network, MDM blocked, etc.) */
    UNAVAILABLE
}

/**
 * ViewModel for the NS Analytics connection screen.
 *
 * Manages the state and business logic for NS Analytics integration, including:
 * - Device registration and QR code scanning
 * - Upload scheduling and progress monitoring
 * - Record type toggles (cellular, wifi, bluetooth, GNSS)
 * - Quota exceeded detection and error handling
 * - Device deregistration and cleanup
 * - Survey status tracking and real-time statistics
 *
 * This ViewModel observes WorkManager for upload progress and errors,
 * maintains UI state via StateFlow, and coordinates with the NetworkSurveyService
 * for survey control.
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
    private var uploadWorkTagObserver: Observer<List<WorkInfo>>? = null

    /** LiveData instance for tag observer - stored to ensure proper removal */
    private var uploadWorkTagLiveData: LiveData<List<WorkInfo>>? = null

    /** ID of the upload work currently being tracked by the tag observer */
    @Volatile
    private var trackedTagUploadWorkId: UUID? = null

    /**
     * Timestamp when last upload completed. Used to prevent [updateSurveyStatus] from
     * immediately overwriting the upload result message after completion.
     */
    @Volatile
    private var lastUploadCompletionTime = 0L

    /** Initial queue size when upload started, used to calculate records uploaded */
    @Volatile
    private var uploadInitialQueueSize = 0

    /** Track if last upload was a failure - persists failure state until next upload attempt */
    @Volatile
    private var lastUploadWasFailure = false

    /** Tracks if the current upload was triggered manually (vs auto/periodic) for dialog display */
    @Volatile
    private var wasManualUpload = false

    private companion object {
        /** How long to preserve upload result state before allowing status updates to override */
        const val UPLOAD_RESULT_PRESERVATION_MS = 5000L
    }

    private val _uiState = MutableStateFlow(NsAnalyticsConnectionUiState(isLoading = true))
    val uiState: StateFlow<NsAnalyticsConnectionUiState> = _uiState.asStateFlow()

    init {
        loadConnectionState()
        // Check for pending QR data when screen loads
        checkAndProcessQrData()
    }

    /**
     * Called when the screen becomes visible. Starts polling for survey status
     * and observing upload work.
     */
    fun onStart() {
        startPolling()
        observeAllUploadWork()
    }

    /**
     * Called when the screen is no longer visible. Stops polling and removes observers
     * to save resources.
     */
    fun onStop() {
        pollingJob?.cancel()
        pollingJob = null
        removeUploadWorkTagObserver()
        trackedTagUploadWorkId = null
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

            // Update upload state based on queue, but don't override if uploading or recently completed
            val currentState = _uiState.value
            val timeSinceLastUpload = System.currentTimeMillis() - lastUploadCompletionTime
            val recentlyCompletedUpload = timeSinceLastUpload < UPLOAD_RESULT_PRESERVATION_MS

            val newUploadState = when {
                // Preserve failure state until next upload attempt
                lastUploadWasFailure -> currentState.uploadState
                // Preserve recent upload result state (takes priority to prevent race condition)
                recentlyCompletedUpload -> currentState.uploadState
                // Preserve UPLOADING state only if NOT recently completed
                currentState.uploadState == UploadState.UPLOADING -> UploadState.UPLOADING
                !currentState.isRegistered -> UploadState.UNAVAILABLE
                totalQueuedRecords == 0 -> UploadState.EMPTY
                else -> UploadState.IDLE
            }
            val newUploadStatusMessage = when {
                // Preserve failure message until next upload attempt
                lastUploadWasFailure -> currentState.uploadStatusMessage
                // Preserve recent upload result message (takes priority)
                recentlyCompletedUpload -> currentState.uploadStatusMessage
                // Preserve message during active upload
                currentState.uploadState == UploadState.UPLOADING -> currentState.uploadStatusMessage
                newUploadState == UploadState.UNAVAILABLE -> context.getString(R.string.ns_analytics_upload_status_not_registered)
                newUploadState == UploadState.EMPTY -> context.getString(R.string.ns_analytics_upload_status_no_records)
                newUploadState == UploadState.IDLE -> context.getString(R.string.ns_analytics_upload_status_ready)
                else -> currentState.uploadStatusMessage
            }

            _uiState.value = _uiState.value.copy(
                isSurveyActive = isSurveyActive,
                surveyStartTime = surveyStartTime,
                cellularRecordCount = cellularCount,
                wifiRecordCount = wifiCount,
                bluetoothRecordCount = bluetoothCount,
                gnssRecordCount = gnssCount,
                queuedRecords = totalQueuedRecords,
                uploadState = newUploadState,
                uploadStatusMessage = newUploadStatusMessage
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update survey status")
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        removeUploadProgressObserver()
        removeUploadWorkTagObserver()
    }

    /**
     * Safely remove the upload progress observer to prevent memory leaks.
     * This method can be called multiple times safely.
     */
    private fun removeUploadProgressObserver() {
        uploadProgressObserver?.let { observer ->
            uploadWorkId?.let { id ->
                try {
                    workManager.getWorkInfoByIdLiveData(id).removeObserver(observer)
                } catch (e: Exception) {
                    Timber.w(e, "Error removing upload progress observer")
                }
            }
        }
        uploadProgressObserver = null
        uploadWorkId = null
    }

    /**
     * Observes all upload work by tag to catch both periodic and manual uploads.
     * This provides real-time UI updates when any upload work runs.
     */
    private fun observeAllUploadWork() {
        // Don't set up duplicate observer
        if (uploadWorkTagObserver != null) return

        uploadWorkTagObserver = Observer { workInfos ->
            handleUploadWorkChanges(workInfos)
        }
        // Store the LiveData reference to ensure we remove from the SAME instance later.
        // getWorkInfosByTagLiveData() returns a NEW instance each time, so if we don't store it,
        // removeObserver() will operate on a different instance and the observer will never be removed.
        uploadWorkTagLiveData =
            workManager.getWorkInfosByTagLiveData(NsAnalyticsConstants.NS_ANALYTICS_UPLOAD_WORKER_TAG)
        uploadWorkTagLiveData?.observeForever(uploadWorkTagObserver!!)
    }

    /**
     * Removes the tag-based upload work observer.
     */
    private fun removeUploadWorkTagObserver() {
        uploadWorkTagObserver?.let { observer ->
            try {
                // Remove from the SAME LiveData instance we observed on.
                // Using the stored reference is critical - calling getWorkInfosByTagLiveData()
                // again would return a different instance, and the observer would never be removed.
                uploadWorkTagLiveData?.removeObserver(observer)
            } catch (e: Exception) {
                Timber.w(e, "Error removing upload work tag observer")
            }
        }
        uploadWorkTagObserver = null
        uploadWorkTagLiveData = null
    }

    /**
     * Handles changes to upload work state from the tag-based observer.
     * Updates UI for running uploads and handles completion, failure, and cancellation.
     */
    private fun handleUploadWorkChanges(workInfos: List<WorkInfo>) {
        val runningWork = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }

        when {
            runningWork != null -> {
                // Capture the ID when we first see RUNNING state
                if (trackedTagUploadWorkId == null) {
                    trackedTagUploadWorkId = runningWork.id
                }
                // Upload is running - update UI if not already showing upload state
                if (_uiState.value.uploadState != UploadState.UPLOADING) {
                    // Clear failure state when new upload starts
                    lastUploadWasFailure = false
                    uploadInitialQueueSize = _uiState.value.queuedRecords
                    _uiState.value = _uiState.value.copy(
                        isUploading = true,
                        uploadState = UploadState.UPLOADING,
                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_uploading),
                        uploadProgress = 0f,
                        totalRecordsToUpload = uploadInitialQueueSize
                    )
                }
                // Update progress from work info
                updateUploadProgressFromWorkInfo(runningWork)
            }

            trackedTagUploadWorkId != null -> {
                // Only react to the work we were tracking - prevents stale work from affecting UI
                val trackedWork = workInfos.firstOrNull { it.id == trackedTagUploadWorkId }
                when (trackedWork?.state) {
                    WorkInfo.State.FAILED -> {
                        handleUploadFailureFromTag(trackedWork)
                        trackedTagUploadWorkId = null
                    }

                    WorkInfo.State.CANCELLED -> {
                        // Only treat as failure if we were actively uploading
                        // (cancelled periodic work after success also shows CANCELLED for the next scheduled run)
                        if (_uiState.value.uploadState == UploadState.UPLOADING) {
                            handleUploadCancelledFromTag()
                        }
                        trackedTagUploadWorkId = null
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        handleUploadCompletionFromTag(workInfos)
                        refreshLastUploadTimeFromStorage()
                        trackedTagUploadWorkId = null
                    }

                    WorkInfo.State.ENQUEUED -> {
                        // Periodic work: WorkManager reschedules immediately after SUCCESS,
                        // so we never see SUCCEEDED state - only ENQUEUED for next run.
                        // Progress data is also cleared when rescheduled, so we check
                        // SharedPreferences for explicit completion data set by the worker.

                        if (_uiState.value.uploadState == UploadState.UPLOADING) {
                            // Capture and clear the tracked ID FIRST to prevent re-processing
                            // by duplicate LiveData callbacks
                            val capturedTrackedId = trackedTagUploadWorkId
                            trackedTagUploadWorkId = null

                            if (capturedTrackedId != null) {
                                try {
                                    val lastWorkId =
                                        NsAnalyticsSecureStorage.getLastUploadWorkId(context)

                                    if (lastWorkId == capturedTrackedId.toString()) {
                                        val success =
                                            NsAnalyticsSecureStorage.getLastUploadSuccess(context)
                                        val recordsCount =
                                            NsAnalyticsSecureStorage.getLastUploadRecordsCount(
                                                context
                                            )

                                        if (success) {
                                            handleUploadCompletionFromTag(workInfos, recordsCount)
                                            refreshLastUploadTimeFromStorage()
                                        } else {
                                            val errorType =
                                                NsAnalyticsSecureStorage.getLastUploadErrorType(
                                                    context
                                                )
                                            handleUploadFailureByErrorType(errorType)
                                        }

                                        NsAnalyticsSecureStorage.clearLastUploadCompletion(context)
                                    } else if (lastWorkId != null) {
                                        // ACTUAL work ID mismatch - we have completion data for a different work
                                        // This is unexpected - reset to IDLE only if still uploading
                                        if (_uiState.value.uploadState == UploadState.UPLOADING) {
                                            Timber.w(
                                                "ENQUEUED: Work ID mismatch. captured=%s, found=%s. Resetting to IDLE.",
                                                capturedTrackedId,
                                                lastWorkId
                                            )
                                            _uiState.value = _uiState.value.copy(
                                                isUploading = false,
                                                uploadState = UploadState.IDLE,
                                                uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_ready),
                                                uploadProgress = 0f
                                            )
                                        }
                                    }
                                    // else: No completion data in SharedPrefs (lastWorkId == null)
                                    // This means either:
                                    // 1. First callback already processed completion and cleared SharedPrefs
                                    // 2. Work is still running/no completion yet
                                    // Either way, do NOT reset - would overwrite the correct state
                                } catch (e: Exception) {
                                    Timber.e(
                                        e,
                                        "Failed to read upload completion data from SharedPreferences"
                                    )
                                    // Log error - state will be determined by polling
                                }
                            }
                        } else {
                            // Not uploading - clear tracking
                            trackedTagUploadWorkId = null
                        }
                    }

                    else -> {
                        // Still processing or work was pruned - no action needed
                    }
                }
            }

            _uiState.value.uploadState == UploadState.UPLOADING -> {
                // Fallback: UI thinks we're uploading but we never captured an ID.
                // This can happen if:
                // 1. Work completed before we observed RUNNING state
                // 2. User navigated away (clearing trackedTagUploadWorkId) and returned
                // 3. Periodic work completed and was rescheduled to ENQUEUED
                val succeededWork = workInfos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
                if (succeededWork != null) {
                    handleUploadCompletionFromTag(workInfos)
                    refreshLastUploadTimeFromStorage()
                } else {
                    // No SUCCEEDED work visible - check SharedPreferences for completion data.
                    // This handles periodic work that completed and was rescheduled to ENQUEUED.
                    try {
                        val lastWorkId = NsAnalyticsSecureStorage.getLastUploadWorkId(context)

                        if (lastWorkId != null) {
                            val success = NsAnalyticsSecureStorage.getLastUploadSuccess(context)
                            val recordsCount =
                                NsAnalyticsSecureStorage.getLastUploadRecordsCount(context)

                            if (success) {
                                handleUploadCompletionFromTag(workInfos, recordsCount)
                                refreshLastUploadTimeFromStorage()
                            } else {
                                // Read error type from SharedPreferences and handle accordingly
                                val errorType =
                                    NsAnalyticsSecureStorage.getLastUploadErrorType(context)
                                handleUploadFailureByErrorType(errorType)
                            }
                            NsAnalyticsSecureStorage.clearLastUploadCompletion(context)
                        }
                        // else: No completion data - upload might still be running or queued
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to read completion data in fallback")
                    }
                }
            }

            else -> {
                // No action needed - no upload in progress and no tracked work
            }
        }
    }

    /**
     * Updates the upload progress UI from WorkInfo progress data.
     */
    private fun updateUploadProgressFromWorkInfo(workInfo: WorkInfo) {
        val workerProgress = workInfo.progress.getInt("progress", -1)
        val workerMax = workInfo.progress.getInt("progressMax", 100)
        val workerMessage = workInfo.progress.getString("progressMessage")
        val recordsUploaded = workInfo.progress.getInt("recordsUploaded", 0)

        if (workerProgress >= 0 && workerMax > 0) {
            val progress = workerProgress.toFloat() / workerMax
            _uiState.value = _uiState.value.copy(
                uploadProgress = progress,
                uploadedRecords = recordsUploaded,
                uploadStatusMessage = workerMessage
                    ?: context.getString(R.string.ns_analytics_upload_status_uploading)
            )
        }
    }

    /**
     * Handles upload completion detected by the tag-based observer.
     *
     * IMPORTANT: The primary state update is done SYNCHRONOUSLY to prevent a race condition
     * with the polling job in [updateSurveyStatus]. If we updated state asynchronously,
     * the polling job could run during the async window and "lock in" the UPLOADING state
     * via its preservation logic.
     */
    private fun handleUploadCompletionFromTag(
        workInfos: List<WorkInfo>,
        recordsCountOverride: Int? = null
    ) {
        // Find most recently finished work
        val completedWork = workInfos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }

        // Clear failure state on success
        lastUploadWasFailure = false
        lastUploadCompletionTime = System.currentTimeMillis()

        // Get records uploaded - prefer override (from SharedPrefs), then output data, then estimate
        val recordsUploaded = when {
            recordsCountOverride != null && recordsCountOverride > 0 -> recordsCountOverride
            else -> {
                val outputRecords = completedWork?.outputData?.getInt(
                    NsAnalyticsConstants.EXTRA_RECORDS_UPLOADED, 0
                ) ?: 0
                if (outputRecords > 0) {
                    outputRecords
                } else {
                    // Estimate from initial queue size (may not account for new records during upload)
                    (uploadInitialQueueSize - _uiState.value.queuedRecords).coerceAtLeast(0)
                }
            }
        }

        // CRITICAL: Update state SYNCHRONOUSLY to prevent race with polling job.
        // Use IDLE as initial state; we'll refine to EMPTY after checking queue async.
        _uiState.value = _uiState.value.copy(
            isUploading = false,
            uploadState = UploadState.IDLE,
            uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_complete),
            uploadProgress = 1f,
            lastUploadTime = lastUploadCompletionTime,
            lastUploadResult = if (recordsUploaded > 0) {
                context.getString(R.string.ns_analytics_upload_result_records, recordsUploaded)
            } else null
        )

        // Async: Update queue count and refine state to EMPTY if queue is empty
        viewModelScope.launch {
            val remainingRecords = withContext(Dispatchers.IO) {
                database.nsAnalyticsDao().getPendingRecordCount()
            }
            val newState = if (remainingRecords == 0) UploadState.EMPTY else UploadState.IDLE

            _uiState.value = _uiState.value.copy(
                uploadState = newState,
                queuedRecords = remainingRecords
            )
        }
    }

    /**
     * Handles upload failure detected by the tag-based observer.
     */
    private fun handleUploadFailureFromTag(workInfo: WorkInfo) {
        lastUploadWasFailure = true
        lastUploadCompletionTime = System.currentTimeMillis()

        val errorType = workInfo.outputData.getString(NsAnalyticsConstants.ERROR_OUTPUT_KEY_TYPE)

        when (errorType) {
            NsAnalyticsConstants.ERROR_CODE_QUOTA_EXCEEDED -> {
                // Extract quota details from work output data
                val currentUsage = workInfo.outputData.getInt(
                    NsAnalyticsConstants.EXTRA_QUOTA_CURRENT_USAGE, 0
                )
                val maxRecords = workInfo.outputData.getInt(
                    NsAnalyticsConstants.EXTRA_QUOTA_MAX_RECORDS, 0
                )
                val quotaMessage = workInfo.outputData.getString(
                    NsAnalyticsConstants.EXTRA_QUOTA_MESSAGE
                )
                val quotaWebUrl = workInfo.outputData.getString(
                    NsAnalyticsConstants.EXTRA_QUOTA_WEB_URL
                )

                // Only show dialog for manual uploads, not auto/periodic uploads
                val shouldShowDialog = wasManualUpload
                wasManualUpload = false

                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadState = UploadState.UNAVAILABLE,
                    uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_quota_exceeded),
                    uploadProgress = 0f,
                    showQuotaExceededDialog = shouldShowDialog,
                    quotaCurrentUsage = currentUsage,
                    quotaMaxRecords = maxRecords,
                    quotaMessage = quotaMessage,
                    quotaWebUrl = quotaWebUrl
                )
            }

            NsAnalyticsConstants.ERROR_CODE_DEVICE_DEREGISTERED -> {
                // Device was deregistered - trigger deregistration detection
                viewModelScope.launch {
                    checkDeviceStatus()
                }
                showMessage("Device has been unregistered. Please scan a QR code to re-register.")
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadState = UploadState.UNAVAILABLE,
                    uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_device_unregistered),
                    uploadProgress = 0f
                )
            }

            NsAnalyticsUploadWorker.ERROR_CODE_NOT_REGISTERED -> {
                // Device not registered - non-retriable
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadState = UploadState.UNAVAILABLE,
                    uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_not_registered),
                    uploadProgress = 0f
                )
            }

            NsAnalyticsUploadWorker.ERROR_CODE_NO_CREDENTIALS -> {
                // Missing credentials - non-retriable configuration error
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadState = UploadState.UNAVAILABLE,
                    uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_config_error),
                    uploadProgress = 0f
                )
            }

            NsAnalyticsConstants.ERROR_CODE_REGISTRATION_INVALID -> {
                // Registration has expired - user needs to re-register
                showMessage("Your device registration has expired. Please scan a new QR code.")
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadState = UploadState.UNAVAILABLE,
                    uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_registration_invalid),
                    uploadProgress = 0f
                )
                // Reload to show unregistered state (cleanup already done in worker)
                viewModelScope.launch {
                    loadConnectionState()
                }
            }

            NsAnalyticsConstants.ERROR_CODE_VIEWER_CANNOT_UPLOAD -> {
                // User has viewer role - cannot upload
                showMessage("Your role does not allow uploading data. Contact the workspace owner.")
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadState = UploadState.UNAVAILABLE,
                    uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_viewer_cannot_upload),
                    uploadProgress = 0f
                )
            }

            NsAnalyticsConstants.ERROR_CODE_INVALID_TOKEN -> {
                // Token is invalid - similar to deregistration
                showMessage("Your session has expired. Please scan a new QR code to re-register.")
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadState = UploadState.UNAVAILABLE,
                    uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_session_expired),
                    uploadProgress = 0f
                )
                // Reload to show unregistered state (cleanup already done in worker)
                viewModelScope.launch {
                    loadConnectionState()
                }
            }

            else -> {
                // Unknown failure - try to get error message from work output
                val errorMessage = workInfo.outputData.getString(
                    NsAnalyticsConstants.ERROR_OUTPUT_KEY_MESSAGE
                )
                val statusMessage = if (!errorMessage.isNullOrBlank()) {
                    context.getString(
                        R.string.ns_analytics_upload_status_failed_with_reason,
                        errorMessage
                    )
                } else {
                    context.getString(R.string.ns_analytics_upload_status_failed)
                }

                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadState = UploadState.UNAVAILABLE,  // RED indicator for failure
                    uploadStatusMessage = statusMessage,
                    uploadProgress = 0f
                )
            }
        }
    }

    /**
     * Handles upload failure based on error type read from SharedPreferences.
     * Used by fallback paths when WorkManager outputData is not available
     * (e.g., periodic work that was rescheduled before we could read the failure data).
     */
    private fun handleUploadFailureByErrorType(errorType: String?) {
        lastUploadWasFailure = true
        lastUploadCompletionTime = System.currentTimeMillis()

        when (errorType) {
            NsAnalyticsConstants.ERROR_CODE_QUOTA_EXCEEDED -> {
                // Quota exceeded - show specific message but no dialog (this is fallback path)
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadState = UploadState.UNAVAILABLE,
                    uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_quota_exceeded),
                    uploadProgress = 0f,
                    showQuotaExceededDialog = false  // No dialog for fallback path (auto-upload)
                )
            }

            NsAnalyticsConstants.ERROR_CODE_DEVICE_DEREGISTERED -> {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadState = UploadState.UNAVAILABLE,
                    uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_device_unregistered),
                    uploadProgress = 0f
                )
            }

            NsAnalyticsConstants.ERROR_CODE_REGISTRATION_INVALID -> {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadState = UploadState.UNAVAILABLE,
                    uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_registration_invalid),
                    uploadProgress = 0f
                )
            }

            NsAnalyticsConstants.ERROR_CODE_VIEWER_CANNOT_UPLOAD -> {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadState = UploadState.UNAVAILABLE,
                    uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_viewer_cannot_upload),
                    uploadProgress = 0f
                )
            }

            NsAnalyticsConstants.ERROR_CODE_INVALID_TOKEN -> {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadState = UploadState.UNAVAILABLE,
                    uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_session_expired),
                    uploadProgress = 0f
                )
            }

            else -> {
                // Generic failure - unknown error type
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadState = UploadState.UNAVAILABLE,
                    uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_failed),
                    uploadProgress = 0f
                )
            }
        }
    }

    /**
     * Handles upload cancellation detected by the tag-based observer.
     */
    private fun handleUploadCancelledFromTag() {
        lastUploadWasFailure = true
        lastUploadCompletionTime = System.currentTimeMillis()

        _uiState.value = _uiState.value.copy(
            isUploading = false,
            uploadState = UploadState.UNAVAILABLE,
            uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_failed),
            uploadProgress = 0f
        )
    }

    /**
     * Refreshes the lastUploadTime from secure storage.
     * This ensures the UI shows the correct timestamp even if we miss a completion notification.
     */
    private fun refreshLastUploadTimeFromStorage() {
        viewModelScope.launch {
            val storedTime = withContext(Dispatchers.IO) {
                NsAnalyticsSecureStorage.getLastUploadTime(context)
            }
            if (storedTime > _uiState.value.lastUploadTime) {
                _uiState.value = _uiState.value.copy(lastUploadTime = storedTime)
            }
        }
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

                    // Determine initial upload state based on queue
                    val initialUploadState = when {
                        !isRegistered -> UploadState.UNAVAILABLE
                        queueSize == 0 -> UploadState.EMPTY
                        else -> UploadState.IDLE
                    }
                    val initialUploadStatusMessage = when (initialUploadState) {
                        UploadState.UNAVAILABLE -> context.getString(R.string.ns_analytics_upload_status_not_registered)
                        UploadState.EMPTY -> context.getString(R.string.ns_analytics_upload_status_no_records)
                        UploadState.IDLE -> context.getString(R.string.ns_analytics_upload_status_ready)
                        UploadState.UPLOADING -> context.getString(R.string.ns_analytics_upload_status_uploading)
                    }

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
                        uploadState = initialUploadState,
                        uploadStatusMessage = initialUploadStatusMessage,
                        cellularEnabled = cellularEnabled,
                        wifiEnabled = wifiEnabled,
                        bluetoothEnabled = bluetoothEnabled,
                        gnssEnabled = gnssEnabled
                    )

                    // Update survey status after loading connection state
                    updateSurveyStatus()
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

                    // Schedule periodic uploads if auto-upload is enabled and there's pending data
                    if (autoUploadEnabled && queueSize > 0) {
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
                    message = "Failed to load connection state"
                )
            }
        }
    }

    /**
     * Asynchronously fetch device status from backend and update workspace name if changed.
     */
    private fun fetchAndUpdateDeviceStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val deviceToken = NsAnalyticsSecureStorage.getDeviceToken(context) ?: return@launch
                val apiUrl = NsAnalyticsSecureStorage.getApiUrl(context) ?: return@launch

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
     * Dismiss the quota exceeded dialog and clear quota-related state.
     *
     * This resets the dialog visibility flag and clears all quota usage information
     * from the UI state, including current usage, max records, quota message, and
     * the web URL for subscription management.
     */
    fun dismissQuotaDialog() {
        _uiState.value = _uiState.value.copy(
            showQuotaExceededDialog = false,
            quotaCurrentUsage = 0,
            quotaMaxRecords = 0,
            quotaMessage = null,
            quotaWebUrl = null
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
                        showMessage("Auto upload enabled (every $uploadFrequency minutes)")
                    } else {
                        showMessage("Auto upload enabled")
                    }
                } else {
                    NsAnalyticsUploadWorker.cancelPeriodicUpload(context)
                    workManager.cancelAllWorkByTag(NsAnalyticsConstants.NS_ANALYTICS_PERIODIC_WORKER_TAG)
                    showMessage("Auto upload disabled")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle auto upload")
                showMessage("Failed to update auto upload setting")
            }
        }
    }

    fun uploadNow() {
        // Don't trigger another upload if one is already in progress
        if (_uiState.value.uploadState == UploadState.UPLOADING) return

        lastUploadWasFailure = false
        wasManualUpload = true

        viewModelScope.launch {
            // Trigger immediate upload and get the result
            val result = withContext(Dispatchers.IO) {
                NsAnalyticsUploadWorker.triggerImmediateUpload(context)
            }

            when (result) {
                is NsAnalyticsUploadWorker.Companion.TriggerResult.Started -> {
                    // Track this work ID so the tag observer can properly detect completion
                    trackedTagUploadWorkId = result.workId

                    val initialQueueSize = _uiState.value.queuedRecords
                    _uiState.value = _uiState.value.copy(
                        isUploading = true,
                        uploadState = UploadState.UPLOADING,
                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_uploading),
                        uploadProgress = 0f,
                        uploadedRecords = 0,
                        totalRecordsToUpload = initialQueueSize
                    )
                }

                is NsAnalyticsUploadWorker.Companion.TriggerResult.AlreadyRunning -> {
                    _uiState.value = _uiState.value.copy(
                        uploadState = UploadState.UPLOADING,
                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_already_running)
                    )
                }

                is NsAnalyticsUploadWorker.Companion.TriggerResult.NoRecords -> {
                    _uiState.value = _uiState.value.copy(
                        uploadState = UploadState.EMPTY,
                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_no_records)
                    )
                }

                is NsAnalyticsUploadWorker.Companion.TriggerResult.NoNetwork -> {
                    _uiState.value = _uiState.value.copy(
                        uploadState = UploadState.UNAVAILABLE,
                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_no_network)
                    )
                }

                is NsAnalyticsUploadWorker.Companion.TriggerResult.MdmBlocked -> {
                    _uiState.value = _uiState.value.copy(
                        uploadState = UploadState.UNAVAILABLE,
                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_mdm_blocked)
                    )
                }
            }
        }
    }

    /**
     * Observes the upload work progress and updates UI state accordingly.
     *
     * @param initialQueueSize The number of records in the queue when upload started
     * @param workId The specific work ID to observe (from triggerImmediateUpload)
     */
    private fun observeUploadProgress(initialQueueSize: Int, workId: UUID) {
        try {
            // Use the provided work ID directly instead of searching by tag.
            // This prevents observing the wrong work when both periodic and immediate
            // uploads are scheduled (they share the same tag).
            uploadWorkId = workId

            val observer = Observer<WorkInfo?> { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.ENQUEUED -> {
                            // Worker returned Result.retry() - waiting for backoff before retry
                            val retryAttempt = workInfo.runAttemptCount
                            val message = if (retryAttempt > 1) {
                                context.getString(
                                    R.string.ns_analytics_upload_status_retry_attempt,
                                    retryAttempt
                                )
                            } else {
                                context.getString(R.string.ns_analytics_upload_status_waiting_retry)
                            }
                            _uiState.value = _uiState.value.copy(
                                uploadStatusMessage = message
                                // Keep isUploading=true, uploadState=UPLOADING - work is still in progress
                            )
                        }

                        WorkInfo.State.RUNNING -> {
                            // Try to get progress from worker first
                            val workerProgress = workInfo.progress.getInt("progress", -1)
                            val workerMax = workInfo.progress.getInt("progressMax", 100)
                            val workerMessage = workInfo.progress.getString("progressMessage")

                            val progress: Float
                            val uploadedRecords: Int

                            if (workerProgress >= 0 && workerMax > 0) {
                                // Use worker-reported progress
                                progress = workerProgress.toFloat() / workerMax
                                uploadedRecords = (initialQueueSize * progress).toInt()
                            } else {
                                // Fallback to queue-based estimation
                                val currentQueue = _uiState.value.queuedRecords
                                uploadedRecords = initialQueueSize - currentQueue
                                progress = if (initialQueueSize > 0) {
                                    uploadedRecords.toFloat() / initialQueueSize
                                } else {
                                    0f
                                }
                            }

                            _uiState.value = _uiState.value.copy(
                                uploadProgress = progress,
                                uploadedRecords = uploadedRecords,
                                uploadStatusMessage = workerMessage
                                    ?: context.getString(R.string.ns_analytics_upload_status_uploading)
                            )
                        }

                        WorkInfo.State.SUCCEEDED -> {
                            val recordsUploaded = workInfo.outputData.getInt(
                                NsAnalyticsConstants.EXTRA_RECORDS_UPLOADED, initialQueueSize
                            )
                            lastUploadCompletionTime = System.currentTimeMillis()

                            // Query actual queue state - new records may have arrived during upload
                            viewModelScope.launch {
                                val remainingRecords = withContext(Dispatchers.IO) {
                                    database.nsAnalyticsDao().getPendingRecordCount()
                                }
                                val newState =
                                    if (remainingRecords == 0) UploadState.EMPTY else UploadState.IDLE

                                _uiState.value = _uiState.value.copy(
                                    isUploading = false,
                                    uploadState = newState,
                                    uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_complete),
                                    uploadProgress = 1f,
                                    lastUploadTime = lastUploadCompletionTime,
                                    lastUploadResult = context.getString(
                                        R.string.ns_analytics_upload_result_records,
                                        recordsUploaded
                                    ),
                                    queuedRecords = remainingRecords
                                )
                            }
                            removeUploadProgressObserver()
                        }

                        WorkInfo.State.FAILED -> {
                            // Check error type to determine appropriate user feedback
                            val errorType = workInfo.outputData.getString(
                                NsAnalyticsConstants.ERROR_OUTPUT_KEY_TYPE
                            )

                            when (errorType) {
                                NsAnalyticsConstants.ERROR_CODE_QUOTA_EXCEEDED -> {
                                    // Extract quota details from work output data
                                    val currentUsage = workInfo.outputData.getInt(
                                        NsAnalyticsConstants.EXTRA_QUOTA_CURRENT_USAGE, 0
                                    )
                                    val maxRecords = workInfo.outputData.getInt(
                                        NsAnalyticsConstants.EXTRA_QUOTA_MAX_RECORDS, 0
                                    )
                                    val quotaMessage = workInfo.outputData.getString(
                                        NsAnalyticsConstants.EXTRA_QUOTA_MESSAGE
                                    )
                                    val quotaWebUrl = workInfo.outputData.getString(
                                        NsAnalyticsConstants.EXTRA_QUOTA_WEB_URL
                                    )

                                    // Update UI state to show quota dialog
                                    _uiState.value = _uiState.value.copy(
                                        isUploading = false,
                                        uploadState = UploadState.UNAVAILABLE,
                                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_quota_exceeded),
                                        uploadProgress = 0f,
                                        showQuotaExceededDialog = true,
                                        quotaCurrentUsage = currentUsage,
                                        quotaMaxRecords = maxRecords,
                                        quotaMessage = quotaMessage,
                                        quotaWebUrl = quotaWebUrl
                                    )
                                }

                                NsAnalyticsConstants.ERROR_CODE_DEVICE_DEREGISTERED -> {
                                    // Device was deregistered - trigger deregistration detection
                                    viewModelScope.launch {
                                        checkDeviceStatus()
                                    }
                                    showMessage("Device has been unregistered. Please scan a QR code to re-register.")
                                    _uiState.value = _uiState.value.copy(
                                        isUploading = false,
                                        uploadState = UploadState.UNAVAILABLE,
                                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_device_unregistered),
                                        uploadProgress = 0f
                                    )
                                }

                                NsAnalyticsUploadWorker.ERROR_CODE_NOT_REGISTERED -> {
                                    // Device not registered - non-retriable
                                    _uiState.value = _uiState.value.copy(
                                        isUploading = false,
                                        uploadState = UploadState.UNAVAILABLE,
                                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_not_registered),
                                        uploadProgress = 0f
                                    )
                                }

                                NsAnalyticsUploadWorker.ERROR_CODE_NO_CREDENTIALS -> {
                                    // Missing credentials - non-retriable configuration error
                                    _uiState.value = _uiState.value.copy(
                                        isUploading = false,
                                        uploadState = UploadState.UNAVAILABLE,
                                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_config_error),
                                        uploadProgress = 0f
                                    )
                                }

                                else -> {
                                    // Unknown failure - Result.failure() means NO automatic retry
                                    _uiState.value = _uiState.value.copy(
                                        isUploading = false,
                                        uploadState = UploadState.UNAVAILABLE,
                                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_failed),
                                        uploadProgress = 0f
                                    )
                                }
                            }

                            removeUploadProgressObserver()
                        }

                        WorkInfo.State.CANCELLED -> {
                            _uiState.value = _uiState.value.copy(
                                isUploading = false,
                                uploadState = UploadState.UNAVAILABLE,
                                uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_failed),
                                uploadProgress = 0f
                            )
                            removeUploadProgressObserver()
                        }

                        else -> {}
                    }
                }
            }

            uploadProgressObserver = observer
            workManager.getWorkInfoByIdLiveData(workId).observeForever(observer)
        } catch (e: Exception) {
            Timber.e(e, "Failed to observe upload progress")
            _uiState.value = _uiState.value.copy(
                isUploading = false,
                uploadState = UploadState.IDLE,
                uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_failed),
                uploadProgress = 0f
            )
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
                    showMessage("Please stop the active survey before unregistering")
                    return@launch
                }

                val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val activeNetwork = connectivityManager?.activeNetworkInfo
                if (activeNetwork?.isConnected != true) {
                    showMessage("Cannot unregister while offline. Please connect to the internet and try again.")
                    return@launch
                }

                val deviceToken = withContext(Dispatchers.IO) {
                    NsAnalyticsSecureStorage.getDeviceToken(context)
                }
                val apiUrl = withContext(Dispatchers.IO) {
                    NsAnalyticsSecureStorage.getApiUrl(context)
                }

                if (deviceToken == null || apiUrl == null) {
                    Timber.w("Missing credentials for unregister")
                    showMessage("Failed to unregister: missing credentials")
                    return@launch
                }

                _uiState.value =
                    _uiState.value.copy(isLoading = true, message = "Unregistering device...")

                val result = withContext(Dispatchers.IO) {
                    try {
                        val api = NsAnalyticsApiFactory.createClient(apiUrl)
                        val response = api.unregisterDevice("Bearer $deviceToken")

                        if (response.isSuccessful && response.body()?.success == true) {
                            // Success - clear credentials locally but preserve queue
                            NsAnalyticsUtils.cleanupAfterDeregistration(context)

                            Timber.i("Device unregistered successfully")
                            "success"
                        } else if (response.code() == 401 || response.code() == 403) {
                            // Already unregistered or invalid token - clear credentials anyway
                            NsAnalyticsUtils.cleanupAfterDeregistration(context)

                            Timber.w("Device already unregistered or invalid token")
                            "success"
                        } else {
                            Timber.e(
                                "Unregister failed: %d %s",
                                response.code(),
                                response.message()
                            )
                            "error:${response.message()}"
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Unregister API call failed")
                        "error:${e.message ?: "Unknown error"}"
                    }
                }

                // Update UI based on result
                if (result == "success") {
                    val queueSize = withContext(Dispatchers.IO) {
                        database.nsAnalyticsDao().getPendingRecordCount()
                    }

                    _uiState.value = NsAnalyticsConnectionUiState(
                        isLoading = false,
                        isRegistered = false,
                        isConnected = false,
                        queuedRecords = queueSize, // Preserve queue size
                        message = "Device unregistered successfully"
                    )
                } else {
                    val errorMsg = result.removePrefix("error:")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "Failed to unregister: $errorMsg"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to unregister device")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Failed to unregister: ${e.message}"
                )
            }
        }
    }

    /**
     * Schedule periodic uploads based on current settings.
     * This is called when survey starts or when there's data to upload.
     */
    private fun schedulePeriodicUploadsIfNeeded() {
        viewModelScope.launch {
            try {
                // Re-verify auto-upload preference to prevent race conditions
                val autoUploadEnabled = withContext(Dispatchers.IO) {
                    PreferenceUtils.isNsAnalyticsAutoUpload(context)
                }

                // Only schedule if auto-upload is enabled
                if (!autoUploadEnabled) {
                    Timber.d("Skipping upload scheduling - auto-upload is disabled")
                    return@launch
                }

                // Check if there's work to do (survey active or pending records)
                val service = surveyService
                val hasPendingRecords = withContext(Dispatchers.IO) {
                    database.nsAnalyticsDao().getPendingRecordCount() > 0
                }

                if (service?.isNsAnalyticsScanningActive == true || hasPendingRecords) {
                    val uploadFrequency = NsAnalyticsSecureStorage.getUploadFrequency(context)
                    NsAnalyticsUploadWorker.schedulePeriodicUpload(context, uploadFrequency)
                    Timber.d("Scheduled periodic uploads every %d minutes", uploadFrequency)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to schedule periodic uploads")
            }
        }
    }

    /**
     * Check for pending QR data and show appropriate dialog.
     * Shows registration confirmation if not registered,
     * or already registered dialog if user is already in a workspace.
     */
    fun checkAndProcessQrData() {
        viewModelScope.launch {
            try {
                val qrData = NsAnalyticsSecureStorage.getQrData(context)
                if (qrData != null) {
                    if (!NsAnalyticsSecureStorage.isRegistered(context)) {
                        // Not registered - show confirmation dialog to proceed
                        _uiState.value = _uiState.value.copy(
                            pendingQrData = qrData,
                            showRegistrationConfirmDialog = true
                        )
                    } else {
                        // Already registered - show conflict dialog
                        Timber.i(
                            "Device already registered, showing conflict dialog for QR workspace: %s",
                            qrData.workspaceName ?: qrData.workspaceId
                        )
                        _uiState.value = _uiState.value.copy(
                            pendingQrData = qrData,
                            showAlreadyRegisteredDialog = true
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check for QR data")
            }
        }
    }

    /**
     * User confirmed registration - proceed with registering the device
     */
    fun confirmRegistration() {
        val qrData = _uiState.value.pendingQrData
        if (qrData != null) {
            Timber.i("User confirmed registration for workspace: %s", qrData.workspaceId)
            _uiState.value = _uiState.value.copy(showRegistrationConfirmDialog = false)
            viewModelScope.launch {
                registerDevice(qrData)
            }
        } else {
            Timber.w("confirmRegistration called but no pending QR data")
        }
    }

    /**
     * User canceled registration - clear pending QR data
     */
    fun cancelRegistration() {
        Timber.i("User canceled registration")
        _uiState.value = _uiState.value.copy(
            pendingQrData = null,
            showRegistrationConfirmDialog = false
        )
        // Clear the stored QR data
        NsAnalyticsSecureStorage.clearQrData(context)
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
                    ?: Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID
                    )

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

                    // Use server-provided workspace name, fall back to deep link name, then default
                    val finalWorkspaceName = registrationResponse.workspaceName
                        ?: qrData.workspaceName
                        ?: "Unknown Workspace"

                    // Store the registration data
                    NsAnalyticsSecureStorage.storeRegistrationData(
                        context = context,
                        deviceToken = registrationResponse.deviceToken,
                        workspaceId = registrationResponse.workspaceId,
                        apiUrl = qrData.apiUrl,
                        deviceId = registrationResponse.deviceId,
                        workspaceName = finalWorkspaceName
                    )

                    // Clear the QR data since we've successfully registered
                    NsAnalyticsSecureStorage.clearQrData(context)

                    // Update UI state and show success dialog
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRegistered = true,
                        isConnected = true,
                        workspace = registrationResponse.workspaceId,
                        workspaceName = finalWorkspaceName,
                        apiUrl = qrData.apiUrl,
                        pendingQrData = null,
                        showRegistrationSuccessDialog = true
                    )
                    Timber.i(
                        "Device registered successfully for workspace: %s",
                        finalWorkspaceName
                    )

                } else {
                    throw Exception("Registration failed: ${response.code()} ${response.message()}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to register device")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                registrationError = e.message ?: "Unknown error occurred"
            )
            // Don't clear QR data on failure so user can retry
        }
    }

    /**
     * User dismissed the success dialog
     */
    fun dismissSuccessDialog() {
        _uiState.value = _uiState.value.copy(showRegistrationSuccessDialog = false)
    }

    /**
     * User wants to retry registration after an error
     */
    fun retryRegistration() {
        val qrData = _uiState.value.pendingQrData
        if (qrData != null) {
            Timber.i("Retrying registration for workspace: %s", qrData.workspaceId)
            _uiState.value = _uiState.value.copy(registrationError = null)
            viewModelScope.launch {
                registerDevice(qrData)
            }
        } else {
            Timber.w("retryRegistration called but no pending QR data")
            _uiState.value = _uiState.value.copy(registrationError = null)
        }
    }

    /**
     * User canceled registration after an error
     */
    fun dismissRegistrationError() {
        Timber.i("User dismissed registration error")
        _uiState.value = _uiState.value.copy(
            registrationError = null,
            pendingQrData = null
        )
        // Clear the stored QR data
        NsAnalyticsSecureStorage.clearQrData(context)
    }

    /**
     * User dismissed the already registered dialog - clear pending QR data.
     */
    fun dismissAlreadyRegisteredDialog() {
        Timber.i("User dismissed already registered dialog")
        _uiState.value = _uiState.value.copy(
            showAlreadyRegisteredDialog = false,
            pendingQrData = null
        )
        // Clear the stored QR data since user acknowledged
        NsAnalyticsSecureStorage.clearQrData(context)
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
     * Show the Bluetooth permission rationale dialog.
     */
    fun showBluetoothPermissionRationale() {
        _uiState.value = _uiState.value.copy(showBluetoothPermissionDialog = true)
    }

    /**
     * Dismiss the Bluetooth permission rationale dialog.
     */
    fun dismissBluetoothPermissionDialog() {
        _uiState.value = _uiState.value.copy(showBluetoothPermissionDialog = false)
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
                        // Survey started - schedule periodic uploads if auto-upload is enabled
                        schedulePeriodicUploadsIfNeeded()
                        "Survey started"
                    } else {
                        // Survey stopped - cancel periodic uploads
                        // Note: NetworkSurveyService handles the immediate upload trigger to avoid duplicates
                        NsAnalyticsUploadWorker.cancelPeriodicUpload(context)
                        Timber.d("Canceled periodic uploads after survey stop")
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
 * Information about device deregistration (when detected from backend)
 */
data class DeregistrationInfo(
    val deregisteredAt: String?,
    val source: String?, // "web" or "device"
    val deregisteredBy: String?,
    val reason: String?
)

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
    val autoUploadEnabled: Boolean = NsAnalyticsConstants.DEFAULT_AUTO_UPLOAD_ENABLED,
    val uploadFrequencyMinutes: Int = NsAnalyticsConstants.DEFAULT_UPLOAD_FREQUENCY,
    val lastUploadTime: Long = 0,
    val queuedRecords: Int = 0,
    val isUploading: Boolean = false,
    val uploadState: UploadState = UploadState.IDLE,
    val uploadStatusMessage: String = "",
    val uploadProgress: Float = 0f, // Upload progress 0-1
    val uploadedRecords: Int = 0,
    val totalRecordsToUpload: Int = 0,
    val lastUploadResult: String? = null, // e.g., "234 records" or "Failed: quota exceeded"
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
    val gnssRecordCount: Int = 0,
    val deregistrationInfo: DeregistrationInfo? = null,
    val showQuotaExceededDialog: Boolean = false,
    val quotaCurrentUsage: Int = 0,
    val quotaMaxRecords: Int = 0,
    val quotaMessage: String? = null,
    val quotaWebUrl: String? = null,
    // Registration confirmation dialog states
    val pendingQrData: NsAnalyticsQrData? = null,
    val showRegistrationConfirmDialog: Boolean = false,
    val showRegistrationSuccessDialog: Boolean = false,
    val registrationError: String? = null,
    // Already registered dialog state (shown when user scans QR while registered)
    val showAlreadyRegisteredDialog: Boolean = false,
    val showBluetoothPermissionDialog: Boolean = false
)