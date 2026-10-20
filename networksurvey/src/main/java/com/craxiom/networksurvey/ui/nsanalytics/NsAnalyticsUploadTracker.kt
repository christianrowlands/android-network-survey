package com.craxiom.networksurvey.ui.nsanalytics

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.logging.db.uploader.NsAnalyticsUploadWorker
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

/**
 * Follows NS Analytics upload work in WorkManager (manual, periodic and quota re-check uploads)
 * and keeps the upload part of the NS Analytics page's state current: progress, the result, and
 * the failure and quota-paused states. Moved out of [NsAnalyticsConnectionViewModel] unchanged.
 */
internal class NsAnalyticsUploadTracker(
    private val context: Context,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<NsAnalyticsConnectionUiState>,
    private val callbacks: Callbacks,
) {
    /** What the tracker needs from the view model. */
    interface Callbacks {
        fun showMessage(message: String)

        /** Checks whether the device was deregistered, updating the page if it was. */
        suspend fun checkDeviceStatus(): DeregistrationInfo?

        fun reloadConnectionState()
    }

    private val workManager = WorkManager.getInstance(context)

    private var uploadWorkTagObserver: Observer<List<WorkInfo>>? = null

    /** LiveData instance for tag observer - stored to ensure proper removal */
    private var uploadWorkTagLiveData: LiveData<List<WorkInfo>>? = null

    /** ID of the upload work currently being tracked by the tag observer */
    @Volatile
    var trackedTagUploadWorkId: UUID? = null

    /** Applies each upload's outcome to the page. */
    val results = NsAnalyticsUploadResults(context, scope, state, callbacks)

    /**
     * Observes all upload work by tag to catch both periodic and manual uploads.
     * This provides real-time UI updates when any upload work runs.
     */
    fun observeAllUploadWork() {
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
    fun removeUploadWorkTagObserver() {
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
                if (state.value.uploadState != UploadState.UPLOADING) {
                    // Clear failure state when new upload starts
                    results.lastUploadWasFailure = false
                    results.uploadInitialQueueSize = state.value.queuedRecords
                    state.value = state.value.copy(
                        isUploading = true,
                        uploadState = UploadState.UPLOADING,
                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_uploading),
                        uploadProgress = 0f,
                        totalRecordsToUpload = results.uploadInitialQueueSize
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
                        results.handleUploadFailureFromTag(trackedWork)
                        trackedTagUploadWorkId = null
                    }

                    WorkInfo.State.CANCELLED -> {
                        // Only treat as failure if we were actively uploading
                        // (cancelled periodic work after success also shows CANCELLED for the next scheduled run)
                        if (state.value.uploadState == UploadState.UPLOADING) {
                            results.handleUploadCancelledFromTag()
                        }
                        trackedTagUploadWorkId = null
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        results.handleUploadCompletionFromTag(workInfos)
                        results.refreshLastUploadTimeFromStorage()
                        trackedTagUploadWorkId = null
                    }

                    WorkInfo.State.ENQUEUED -> {
                        // Periodic work: WorkManager reschedules immediately after SUCCESS,
                        // so we never see SUCCEEDED state - only ENQUEUED for next run.
                        // Progress data is also cleared when rescheduled, so we check
                        // SharedPreferences for explicit completion data set by the worker.

                        if (state.value.uploadState == UploadState.UPLOADING) {
                            // Capture and clear the tracked ID FIRST to prevent re-processing
                            // by duplicate LiveData callbacks
                            val capturedTrackedId = trackedTagUploadWorkId
                            trackedTagUploadWorkId = null

                            if (capturedTrackedId != null) {
                                val lastWorkId = readStoredCompletionWorkId()
                                if (lastWorkId == capturedTrackedId.toString()) {
                                    results.applyStoredCompletion(workInfos)
                                } else if (lastWorkId != null && state.value.uploadState == UploadState.UPLOADING) {
                                    // ACTUAL work ID mismatch - we have completion data for a different work
                                    // This is unexpected - reset to IDLE only if still uploading
                                    Timber.w(
                                        "ENQUEUED: Work ID mismatch. captured=%s, found=%s. Resetting to IDLE.",
                                        capturedTrackedId,
                                        lastWorkId
                                    )
                                    state.value = state.value.copy(
                                        isUploading = false,
                                        uploadState = UploadState.IDLE,
                                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_ready),
                                        uploadProgress = 0f
                                    )
                                }
                                // else: No completion data in SharedPrefs (lastWorkId == null)
                                // This means either:
                                // 1. First callback already processed completion and cleared SharedPrefs
                                // 2. Work is still running/no completion yet
                                // Either way, do NOT reset - would overwrite the correct state
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

            state.value.uploadState == UploadState.UPLOADING -> {
                // Fallback: UI thinks we're uploading but we never captured an ID.
                // This can happen if:
                // 1. Work completed before we observed RUNNING state
                // 2. User navigated away (clearing trackedTagUploadWorkId) and returned
                // 3. Periodic work completed and was rescheduled to ENQUEUED
                val succeededWork = workInfos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
                if (succeededWork != null) {
                    results.handleUploadCompletionFromTag(workInfos)
                    results.refreshLastUploadTimeFromStorage()
                } else {
                    // No SUCCEEDED work visible - check SharedPreferences for completion data.
                    // This handles periodic work that completed and was rescheduled to ENQUEUED.
                    // No completion data means the upload might still be running or queued.
                    if (readStoredCompletionWorkId() != null) results.applyStoredCompletion(workInfos)
                }
            }

            else -> {
                // No action needed - no upload in progress and no tracked work
            }
        }
    }

    /**
     * @return The work ID of the completion the upload worker stored in SharedPreferences, or null
     * when there is none (or it could not be read; the polling then determines the state).
     */
    private fun readStoredCompletionWorkId(): String? = try {
        NsAnalyticsSecureStorage.getLastUploadWorkId(context)
    } catch (e: Exception) {
        Timber.e(e, "Failed to read upload completion data from SharedPreferences")
        null
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
            state.value = state.value.copy(
                uploadProgress = progress,
                uploadedRecords = recordsUploaded,
                uploadStatusMessage = workerMessage
                    ?: context.getString(R.string.ns_analytics_upload_status_uploading)
            )
        }
    }

    /** The upload stopped and cannot continue on its own: the red status with [status]. */

    fun uploadNow() {
        // Don't trigger another upload if one is already in progress
        if (state.value.uploadState == UploadState.UPLOADING) return

        results.lastUploadWasFailure = false
        results.wasManualUpload = true

        scope.launch {
            // Trigger immediate upload and get the result
            val result = withContext(Dispatchers.IO) {
                NsAnalyticsUploadWorker.triggerImmediateUpload(context)
            }

            when (result) {
                is NsAnalyticsUploadWorker.Companion.TriggerResult.Started -> {
                    // Track this work ID so the tag observer can properly detect completion
                    trackedTagUploadWorkId = result.workId

                    val initialQueueSize = state.value.queuedRecords
                    state.value = state.value.copy(
                        isUploading = true,
                        uploadState = UploadState.UPLOADING,
                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_uploading),
                        uploadProgress = 0f,
                        uploadedRecords = 0,
                        totalRecordsToUpload = initialQueueSize
                    )
                }

                is NsAnalyticsUploadWorker.Companion.TriggerResult.AlreadyRunning -> {
                    state.value = state.value.copy(
                        uploadState = UploadState.UPLOADING,
                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_already_running)
                    )
                }

                is NsAnalyticsUploadWorker.Companion.TriggerResult.NoRecords -> {
                    state.value = state.value.copy(
                        uploadState = UploadState.EMPTY,
                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_no_records)
                    )
                }

                // Not asked for here (this screen reports no network right away), but handled the same way
                is NsAnalyticsUploadWorker.Companion.TriggerResult.WaitingForNetwork,
                is NsAnalyticsUploadWorker.Companion.TriggerResult.NoNetwork -> {
                    state.value = state.value.copy(
                        uploadState = UploadState.UNAVAILABLE,
                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_no_network)
                    )
                }

                is NsAnalyticsUploadWorker.Companion.TriggerResult.MdmBlocked -> {
                    state.value = state.value.copy(
                        uploadState = UploadState.UNAVAILABLE,
                        uploadStatusMessage = context.getString(R.string.ns_analytics_upload_status_mdm_blocked)
                    )
                }
            }
        }
    }
}
