package com.craxiom.networksurvey.ui.nsanalytics

import android.content.Context
import androidx.work.WorkInfo
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.uploader.NsAnalyticsUploadWorker
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Applies the outcome of an NS Analytics upload to the page's state: the completed result, a
 * failure by error type, a cancellation, or the quota-paused state. [NsAnalyticsUploadTracker]
 * decides when; this decides what the page shows.
 */
internal class NsAnalyticsUploadResults(
    private val context: Context,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<NsAnalyticsConnectionUiState>,
    private val callbacks: NsAnalyticsUploadTracker.Callbacks,
) {
    private val database = SurveyDatabase.getInstance(context)

    /**
     * Timestamp when last upload completed. Used to prevent the status polling from immediately
     * overwriting the upload result message after completion.
     */
    @Volatile
    var lastUploadCompletionTime = 0L
        private set

    /** Initial queue size when upload started, used to calculate records uploaded */
    @Volatile
    var uploadInitialQueueSize = 0

    /** Track if last upload was a failure - persists failure state until next upload attempt */
    @Volatile
    var lastUploadWasFailure = false

    /** Tracks if the current upload was triggered manually (vs auto/periodic) for dialog display */
    @Volatile
    var wasManualUpload = false

    /**
     * Shows the completion the upload worker stored in SharedPreferences (periodic work is
     * rescheduled before its output can be read), then clears it so it is shown only once.
     */
    fun applyStoredCompletion(workInfos: List<WorkInfo>) {
        try {
            if (NsAnalyticsSecureStorage.getLastUploadSuccess(context)) {
                handleUploadCompletionFromTag(workInfos, NsAnalyticsSecureStorage.getLastUploadRecordsCount(context))
                refreshLastUploadTimeFromStorage()
            } else {
                handleUploadFailureByErrorType(NsAnalyticsSecureStorage.getLastUploadErrorType(context))
            }
            NsAnalyticsSecureStorage.clearLastUploadCompletion(context)
        } catch (e: Exception) {
            Timber.e(e, "Failed to read upload completion data from SharedPreferences")
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
    fun handleUploadCompletionFromTag(
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
                    (uploadInitialQueueSize - state.value.queuedRecords).coerceAtLeast(0)
                }
            }
        }

        // If uploads were paused due to quota, this successful upload means the quota now has
        // room; the worker has already cleared the persisted pause and resumed the schedule, so
        // clear the in-app paused state too and let the user know.
        val wasPausedForQuota = state.value.uploadPausedForQuota
        if (wasPausedForQuota) {
            callbacks.showMessage(context.getString(R.string.ns_analytics_upload_resumed))
        }

        // CRITICAL: Update state SYNCHRONOUSLY to prevent race with polling job.
        // Use IDLE as initial state; we'll refine to EMPTY after checking queue async.
        state.value = state.value.copy(
            isUploading = false,
            uploadState = UploadState.IDLE,
            uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_complete),
            uploadProgress = 1f,
            lastUploadTime = lastUploadCompletionTime,
            uploadPausedForQuota = false,
            showQuotaExceededDialog = false,
            lastUploadResult = if (recordsUploaded > 0) {
                context.resources.getQuantityString(
                    R.plurals.ns_analytics_upload_result_records,
                    recordsUploaded,
                    recordsUploaded
                )
            } else null
        )

        // Async: Update queue count and refine state to EMPTY if queue is empty
        scope.launch {
            val remainingRecords = withContext(Dispatchers.IO) {
                database.nsAnalyticsDao().getPendingRecordCount()
            }
            val newState = if (remainingRecords == 0) UploadState.EMPTY else UploadState.IDLE

            state.value = state.value.copy(
                uploadState = newState,
                queuedRecords = remainingRecords
            )
        }
    }

    /**
     * Apply the quota-paused UI state shared by the quota-failure code paths (tag observer,
     * manual observer, and the SharedPreferences fallback). Automatic uploads have been paused by
     * the worker; the red status plus the in-app banner explain why, and the manual upload action
     * stays available so the user can resume.
     *
     * @param showDialog Whether to also surface the modal quota dialog (manual uploads only).
     */
    fun applyQuotaPausedState(
        currentUsage: Int,
        maxRecords: Int,
        quotaMessage: String?,
        quotaWebUrl: String?,
        showDialog: Boolean
    ) {
        state.value = state.value.copy(
            isUploading = false,
            uploadState = UploadState.UNAVAILABLE,
            uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_quota_exceeded),
            uploadProgress = 0f,
            uploadPausedForQuota = true,
            showQuotaExceededDialog = showDialog,
            quotaCurrentUsage = currentUsage,
            quotaMaxRecords = maxRecords,
            quotaMessage = quotaMessage,
            quotaWebUrl = quotaWebUrl
        )
    }

    /**
     * Handles upload failure detected by the tag-based observer.
     */
    fun handleUploadFailureFromTag(workInfo: WorkInfo) {
        val errorType = workInfo.outputData.getString(NsAnalyticsConstants.ERROR_OUTPUT_KEY_TYPE)

        if (errorType == NsAnalyticsConstants.ERROR_CODE_QUOTA_EXCEEDED) {
            markFailure()
            // Only show dialog for manual uploads, not auto/periodic uploads
            val shouldShowDialog = wasManualUpload
            wasManualUpload = false
            applyQuotaPausedState(
                workInfo.outputData.getInt(NsAnalyticsConstants.EXTRA_QUOTA_CURRENT_USAGE, 0),
                workInfo.outputData.getInt(NsAnalyticsConstants.EXTRA_QUOTA_MAX_RECORDS, 0),
                workInfo.outputData.getString(NsAnalyticsConstants.EXTRA_QUOTA_MESSAGE),
                workInfo.outputData.getString(NsAnalyticsConstants.EXTRA_QUOTA_WEB_URL),
                shouldShowDialog
            )
            return
        }

        handleUploadFailureByErrorType(
            errorType,
            reason = workInfo.outputData.getString(NsAnalyticsConstants.ERROR_OUTPUT_KEY_MESSAGE)
        )

        // The upload worker has already cleaned up after these; tell the person and catch up.
        when (errorType) {
            NsAnalyticsConstants.ERROR_CODE_DEVICE_DEREGISTERED -> {
                scope.launch { callbacks.checkDeviceStatus() }
                callbacks.showMessage(context.getString(R.string.ns_analytics_message_deregistered))
            }

            NsAnalyticsConstants.ERROR_CODE_REGISTRATION_INVALID -> {
                callbacks.showMessage(context.getString(R.string.ns_analytics_message_registration_expired))
                scope.launch { callbacks.reloadConnectionState() }
            }

            NsAnalyticsConstants.ERROR_CODE_VIEWER_CANNOT_UPLOAD ->
                callbacks.showMessage(context.getString(R.string.ns_analytics_message_viewer))

            NsAnalyticsConstants.ERROR_CODE_INVALID_TOKEN -> {
                callbacks.showMessage(context.getString(R.string.ns_analytics_message_session_expired))
                scope.launch { callbacks.reloadConnectionState() }
            }
        }
    }

    /**
     * Shows a failed upload by its error type. Also used by the fallback paths when WorkManager
     * output data is not available (e.g., periodic work that was rescheduled before we could read
     * the failure data), where the error type comes from SharedPreferences.
     *
     * @param reason The worker's message for an error type without its own status, if any.
     */
    fun handleUploadFailureByErrorType(errorType: String?, reason: String? = null) {
        markFailure()

        if (errorType == NsAnalyticsConstants.ERROR_CODE_QUOTA_EXCEEDED) {
            // Quota exceeded - show the paused banner but no dialog (this is the fallback,
            // auto-upload path). Read the persisted pause details so the banner has the
            // usage numbers and manage-subscription link.
            val pauseInfo = NsAnalyticsSecureStorage.getQuotaPauseInfo(context)
            applyQuotaPausedState(
                pauseInfo?.currentUsage ?: 0,
                pauseInfo?.maxRecords ?: 0,
                pauseInfo?.message,
                pauseInfo?.webUrl,
                showDialog = false
            )
            return
        }

        val status = FAILURE_STATUS[errorType]?.let { context.getString(it) }
            ?: if (!reason.isNullOrBlank()) {
                context.getString(R.string.ns_analytics_upload_status_failed_with_reason, reason)
            } else {
                context.getString(R.string.ns_analytics_upload_status_failed)
            }
        showUploadUnavailable(status)
    }

    private fun showUploadUnavailable(status: String) {
        state.value = state.value.copy(
            isUploading = false,
            uploadState = UploadState.UNAVAILABLE,
            uploadStatusMessage = status,
            uploadProgress = 0f
        )
    }

    private fun markFailure() {
        lastUploadWasFailure = true
        lastUploadCompletionTime = System.currentTimeMillis()
    }

    /**
     * Handles upload cancellation detected by the tag-based observer.
     */
    fun handleUploadCancelledFromTag() {
        markFailure()

        // A periodic 402 cancels the periodic work from inside the worker, so the work may surface
        // as CANCELLED rather than FAILED. The worker persists the quota pause synchronously first,
        // so detect it here and show the paused banner instead of a generic failure message.
        val pauseInfo = NsAnalyticsSecureStorage.getQuotaPauseInfo(context)
        if (pauseInfo != null) {
            applyQuotaPausedState(
                pauseInfo.currentUsage,
                pauseInfo.maxRecords,
                pauseInfo.message,
                pauseInfo.webUrl,
                showDialog = false
            )
            return
        }

        showUploadUnavailable(context.getString(R.string.ns_analytics_upload_status_failed))
    }

    /**
     * Refreshes the lastUploadTime from secure storage.
     * This ensures the UI shows the correct timestamp even if we miss a completion notification.
     */
    fun refreshLastUploadTimeFromStorage() {
        scope.launch {
            val storedTime = withContext(Dispatchers.IO) {
                NsAnalyticsSecureStorage.getLastUploadTime(context)
            }
            if (storedTime > state.value.lastUploadTime) {
                state.value = state.value.copy(lastUploadTime = storedTime)
            }
        }
    }

    private companion object {
        /** The status shown for each upload error type that has its own wording. */
        val FAILURE_STATUS = mapOf(
            NsAnalyticsConstants.ERROR_CODE_DEVICE_DEREGISTERED to R.string.ns_analytics_upload_status_device_unregistered,
            NsAnalyticsUploadWorker.ERROR_CODE_NOT_REGISTERED to R.string.ns_analytics_upload_status_not_registered,
            NsAnalyticsUploadWorker.ERROR_CODE_NO_CREDENTIALS to R.string.ns_analytics_upload_status_config_error,
            NsAnalyticsConstants.ERROR_CODE_REGISTRATION_INVALID to R.string.ns_analytics_upload_status_registration_invalid,
            NsAnalyticsConstants.ERROR_CODE_VIEWER_CANNOT_UPLOAD to R.string.ns_analytics_upload_status_viewer_cannot_upload,
            NsAnalyticsConstants.ERROR_CODE_INVALID_TOKEN to R.string.ns_analytics_upload_status_session_expired,
        )
    }
}
