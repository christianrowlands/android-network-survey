package com.craxiom.networksurvey.logging.db.uploader

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.data.api.NsAnalyticsApiFactory
import com.craxiom.networksurvey.data.api.RecordBatch
import com.craxiom.networksurvey.data.api.UploadBatchRequest
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.NsAnalyticsQueueEntity
import com.craxiom.networksurvey.ui.nsanalytics.NsAnalyticsNotificationHelper
import com.craxiom.networksurvey.util.MdmUtils
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import com.craxiom.networksurvey.util.NsAnalyticsUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import kotlin.math.ceil

/**
 * Worker responsible for uploading NS Analytics records in batches.
 */
class NsAnalyticsUploadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val database = SurveyDatabase.getInstance(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting NS Analytics upload")

            // Defense in depth: the schedule/trigger paths already gate on MDM, but a delayed
            // quota re-check could fire after MDM later disables NS Analytics.
            if (!MdmUtils.isNsAnalyticsAllowed(applicationContext)) {
                Timber.w("NS Analytics upload skipped: disabled by MDM policy")
                return@withContext Result.failure()
            }

            // Check if registered and get credentials
            if (!NsAnalyticsSecureStorage.isRegistered(applicationContext)) {
                Timber.w("Device not registered with NS Analytics")
                NsAnalyticsSecureStorage.saveUploadCompletion(
                    applicationContext,
                    id.toString(),
                    false,
                    0,
                    ERROR_CODE_NOT_REGISTERED
                )
                val outputData = Data.Builder()
                    .putString(
                        NsAnalyticsConstants.ERROR_OUTPUT_KEY_TYPE,
                        ERROR_CODE_NOT_REGISTERED
                    )
                    .build()
                return@withContext Result.failure(outputData)
            }

            val deviceToken = NsAnalyticsSecureStorage.getDeviceToken(applicationContext)
            val apiUrl = NsAnalyticsSecureStorage.getApiUrl(applicationContext)
            if (deviceToken == null || apiUrl == null) {
                Timber.w("Missing NS Analytics credentials (token or API URL)")
                NsAnalyticsSecureStorage.saveUploadCompletion(
                    applicationContext,
                    id.toString(),
                    false,
                    0,
                    ERROR_CODE_NO_CREDENTIALS
                )
                val outputData = Data.Builder()
                    .putString(
                        NsAnalyticsConstants.ERROR_OUTPUT_KEY_TYPE,
                        ERROR_CODE_NO_CREDENTIALS
                    )
                    .build()
                return@withContext Result.failure(outputData)
            }

            // Create API client
            val apiClient = NsAnalyticsApiFactory.createClient(apiUrl)

            // Get total count of pending records
            val totalPendingCount = database.nsAnalyticsDao().getPendingRecordCount()

            if (totalPendingCount == 0) {
                Timber.d("No pending records to upload")
                // An empty queue cannot be over quota locally; if we were paused, resume so the
                // device isn't stranded with no records to act on.
                resumeFromQuotaPauseIfNeeded()
                return@withContext Result.success()
            }

            // Calculate total number of batches needed
            val batchSize = NsAnalyticsConstants.MAX_BATCH_SIZE
            val totalBatches = ceil(totalPendingCount.toDouble() / batchSize).toInt()
            var totalRecordsUploaded = 0
            var totalRecordsProcessed = 0

            Timber.i("Starting upload of $totalPendingCount records in $totalBatches batches")

            // Process all batches
            for (batchIndex in 0 until totalBatches) {
                // Check if worker has been stopped
                if (isStopped) {
                    Timber.d("Upload cancelled, stopping upload processing loop")
                    return@withContext Result.failure()
                }

                // Report progress
                val progress = (batchIndex * 100) / totalBatches
                reportProgress(
                    progress,
                    100,
                    "Uploading batch ${batchIndex + 1} of $totalBatches...",
                    totalRecordsUploaded
                )

                // Get next batch of pending records
                val pendingRecords = database.nsAnalyticsDao()
                    .getPendingRecords(batchSize)

                if (pendingRecords.isEmpty()) {
                    // No more records to process
                    break
                }

                // Group records by type
                val recordBatches = groupRecordsByType(pendingRecords)

                // Create upload request
                val uploadRequest = UploadBatchRequest(
                    deviceToken = deviceToken,
                    batchId = UUID.randomUUID().toString(),
                    records = recordBatches
                )

                // Perform upload
                val response = apiClient.uploadBatch(
                    token = "Bearer $deviceToken",
                    batch = uploadRequest
                )

                // Log detailed response information for debugging
                Timber.d(
                    "NS Analytics upload response - Status: %d, Message: %s",
                    response.code(), response.message()
                )
                Timber.d("Response isSuccessful: %s", response.isSuccessful)

                val responseBody = response.body()

                // Read error body ONCE at the top (ResponseBody can only be consumed once)
                // This must be done before any other code tries to access it
                val errorBodyString = response.errorBody()?.string()

                if (responseBody != null) {
                    Timber.d(
                        "Response body - status: %s, processed: %d, failed: %d, message: %s",
                        responseBody.status,
                        responseBody.processed,
                        responseBody.failed,
                        responseBody.message
                    )
                } else {
                    Timber.w("Response body is null despite status code %d", response.code())

                    // Log error body content (already read above to prevent double consumption)
                    if (!errorBodyString.isNullOrEmpty()) {
                        Timber.w("Error body content: %s", errorBodyString)
                    }
                }

                if (response.isSuccessful && responseBody?.isSuccess == true) {
                    // Mark records as uploaded
                    val recordIds = pendingRecords.map { it.id }
                    database.nsAnalyticsDao().markAsUploaded(
                        recordIds,
                        System.currentTimeMillis()
                    )

                    // Update workspace name if provided in response
                    responseBody.workspaceName?.let { workspaceName ->
                        NsAnalyticsSecureStorage.storeWorkspaceName(
                            applicationContext,
                            workspaceName
                        )
                    }

                    totalRecordsUploaded += pendingRecords.size
                    totalRecordsProcessed += responseBody.processed

                    Timber.i(
                        "Batch %d/%d: Successfully uploaded %d records (processed: %d)",
                        batchIndex + 1, totalBatches, pendingRecords.size, responseBody.processed
                    )

                } else {
                    // Use errorBodyString that was already read above

                    // Enhanced failure logging
                    val failureReason = when {
                        !response.isSuccessful -> "HTTP error: ${response.code()} ${response.message()}"
                        responseBody == null -> "Response body is null"
                        !responseBody.isSuccess -> "API returned status=${responseBody.status}, failed=${responseBody.failed}: ${responseBody.message ?: "no message"}"
                        else -> "Unknown failure condition"
                    }

                    Timber.e(
                        "NS Analytics upload failed for batch %d/%d: %s",
                        batchIndex + 1, totalBatches, failureReason
                    )

                    // Check for quota exceeded errors
                    if (response.code() == 402 && !errorBodyString.isNullOrEmpty()) {
                        val quotaError = NsAnalyticsUtils.parseQuotaError(errorBodyString)
                        if (quotaError?.errorCode == NsAnalyticsConstants.ERROR_CODE_QUOTA_EXCEEDED) {
                            Timber.w("Quota exceeded error detected: ${quotaError.message}")
                            // Return failure with quota details to inform user
                            val outputData = Data.Builder()
                                .putString(
                                    NsAnalyticsConstants.ERROR_OUTPUT_KEY_TYPE,
                                    NsAnalyticsConstants.ERROR_CODE_QUOTA_EXCEEDED
                                )
                                .putInt(
                                    NsAnalyticsConstants.EXTRA_QUOTA_CURRENT_USAGE,
                                    quotaError.currentUsage
                                )
                                .putInt(
                                    NsAnalyticsConstants.EXTRA_QUOTA_MAX_RECORDS,
                                    quotaError.maxRecords
                                )
                                .putString(
                                    NsAnalyticsConstants.EXTRA_QUOTA_LIMIT_TYPE,
                                    quotaError.limitType
                                )
                                .putString(
                                    NsAnalyticsConstants.EXTRA_QUOTA_MESSAGE,
                                    quotaError.message
                                )

                            // Add web URL if provided
                            quotaError.webUrl?.let { webUrl ->
                                outputData.putString(
                                    NsAnalyticsConstants.EXTRA_QUOTA_WEB_URL,
                                    webUrl
                                )
                            }

                            // Pause automatic uploads. A quota-exceeded state almost never
                            // resolves on its own, so retrying on the ~15 min schedule is pure
                            // wasted server load. Persist the pause, stop the recurring schedule,
                            // and tell the user (notification + in-app banner) so they can resume.
                            val retryAfterSeconds =
                                parseRetryAfterSeconds(response.headers()["Retry-After"])
                            NsAnalyticsSecureStorage.saveQuotaPause(
                                applicationContext,
                                quotaError.currentUsage,
                                quotaError.maxRecords,
                                quotaError.message,
                                quotaError.webUrl,
                                System.currentTimeMillis() + retryAfterSeconds * 1000L
                            )
                            cancelPeriodicUpload(applicationContext)
                            // Honor the server Retry-After by scheduling a single delayed
                            // re-check (safety net for users who upgrade but never reopen the app).
                            schedulePausedRetryCheck(applicationContext, retryAfterSeconds)
                            NsAnalyticsNotificationHelper.showUploadsPausedNotification(
                                applicationContext,
                                quotaError.message
                            )

                            NsAnalyticsSecureStorage.saveUploadCompletion(
                                applicationContext,
                                id.toString(),
                                false,
                                0,
                                NsAnalyticsConstants.ERROR_CODE_QUOTA_EXCEEDED
                            )
                            return@withContext Result.failure(outputData.build())
                        }
                    }

                    // Check for registration invalid errors (HTTP 410)
                    if (response.code() == 410 && !errorBodyString.isNullOrEmpty()) {
                        val errorCode = NsAnalyticsUtils.parseErrorCodeFromString(errorBodyString)
                        if (errorCode == NsAnalyticsConstants.ERROR_CODE_REGISTRATION_INVALID) {
                            Timber.w("Registration invalid error detected")
                            NsAnalyticsUtils.cleanupAfterDeregistration(applicationContext)
                            NsAnalyticsSecureStorage.saveUploadCompletion(
                                applicationContext,
                                id.toString(),
                                false,
                                0,
                                NsAnalyticsConstants.ERROR_CODE_REGISTRATION_INVALID
                            )
                            val outputData = Data.Builder()
                                .putString(
                                    NsAnalyticsConstants.ERROR_OUTPUT_KEY_TYPE,
                                    NsAnalyticsConstants.ERROR_CODE_REGISTRATION_INVALID
                                )
                                .putString(
                                    NsAnalyticsConstants.ERROR_OUTPUT_KEY_MESSAGE,
                                    "Device registration has expired"
                                )
                                .build()
                            return@withContext Result.failure(outputData)
                        }
                    }

                    // Check for 403/401 errors (deregistration, viewer role, invalid token)
                    if (response.code() == 403 || response.code() == 401) {
                        val errorCode = if (!errorBodyString.isNullOrEmpty()) {
                            NsAnalyticsUtils.parseErrorCodeFromString(errorBodyString)
                        } else {
                            null
                        }

                        when (errorCode) {
                            NsAnalyticsConstants.ERROR_CODE_DEVICE_DEREGISTERED -> {
                                Timber.w("Device deregistered error detected, checking device status")
                                checkDeviceStatusAfterError()
                                NsAnalyticsSecureStorage.saveUploadCompletion(
                                    applicationContext,
                                    id.toString(),
                                    false,
                                    0,
                                    NsAnalyticsConstants.ERROR_CODE_DEVICE_DEREGISTERED
                                )
                                val outputData = Data.Builder()
                                    .putString(
                                        NsAnalyticsConstants.ERROR_OUTPUT_KEY_TYPE,
                                        NsAnalyticsConstants.ERROR_CODE_DEVICE_DEREGISTERED
                                    )
                                    .putString(
                                        NsAnalyticsConstants.ERROR_OUTPUT_KEY_MESSAGE,
                                        "Device has been unregistered"
                                    )
                                    .build()
                                return@withContext Result.failure(outputData)
                            }

                            NsAnalyticsConstants.ERROR_CODE_VIEWER_CANNOT_UPLOAD -> {
                                Timber.w("Viewer cannot upload error detected")
                                NsAnalyticsSecureStorage.saveUploadCompletion(
                                    applicationContext,
                                    id.toString(),
                                    false,
                                    0,
                                    NsAnalyticsConstants.ERROR_CODE_VIEWER_CANNOT_UPLOAD
                                )
                                val outputData = Data.Builder()
                                    .putString(
                                        NsAnalyticsConstants.ERROR_OUTPUT_KEY_TYPE,
                                        NsAnalyticsConstants.ERROR_CODE_VIEWER_CANNOT_UPLOAD
                                    )
                                    .putString(
                                        NsAnalyticsConstants.ERROR_OUTPUT_KEY_MESSAGE,
                                        "Your role does not allow uploading data"
                                    )
                                    .build()
                                return@withContext Result.failure(outputData)
                            }

                            NsAnalyticsConstants.ERROR_CODE_INVALID_TOKEN -> {
                                Timber.w("Invalid token error detected")
                                NsAnalyticsUtils.cleanupAfterDeregistration(applicationContext)
                                NsAnalyticsSecureStorage.saveUploadCompletion(
                                    applicationContext,
                                    id.toString(),
                                    false,
                                    0,
                                    NsAnalyticsConstants.ERROR_CODE_INVALID_TOKEN
                                )
                                val outputData = Data.Builder()
                                    .putString(
                                        NsAnalyticsConstants.ERROR_OUTPUT_KEY_TYPE,
                                        NsAnalyticsConstants.ERROR_CODE_INVALID_TOKEN
                                    )
                                    .putString(
                                        NsAnalyticsConstants.ERROR_OUTPUT_KEY_MESSAGE,
                                        "Session has expired"
                                    )
                                    .build()
                                return@withContext Result.failure(outputData)
                            }
                        }
                    }

                    // Increment retry count for failed batch
                    val recordIds = pendingRecords.map { it.id }
                    database.nsAnalyticsDao().incrementRetryCount(
                        recordIds,
                        System.currentTimeMillis()
                    )

                    // If upload fails, we should retry the entire worker
                    return@withContext Result.retry()
                }

                // Update progress after successful batch
                val progressAfter = ((batchIndex + 1) * 100) / totalBatches
                reportProgress(
                    progressAfter,
                    100,
                    "Completed batch ${batchIndex + 1} of $totalBatches",
                    totalRecordsUploaded
                )
            }

            // Update overall stats after all batches are complete
            if (totalRecordsUploaded > 0) {
                val workspaceId = NsAnalyticsSecureStorage.getWorkspaceId(applicationContext)
                if (workspaceId != null) {
                    database.nsAnalyticsDao().updateUploadStats(
                        workspaceId,
                        System.currentTimeMillis(),
                        totalRecordsUploaded
                    )
                }

                NsAnalyticsSecureStorage.saveUploadCompletion(
                    applicationContext,
                    id.toString(),
                    true,
                    totalRecordsUploaded
                )

                // Clean up old uploaded records (keep last 7 days)
                val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                database.nsAnalyticsDao().cleanupOldUploadedRecords(sevenDaysAgo)
            }

            Timber.i(
                "Upload process completed. Total records uploaded: %d (processed: %d)",
                totalRecordsUploaded, totalRecordsProcessed
            )

            // The upload completed without a 402, so the quota now has room. If we were paused,
            // clear the pause and resume automatic uploads.
            resumeFromQuotaPauseIfNeeded()

            // Report final results
            val outputData = Data.Builder()
                .putBoolean(NsAnalyticsConstants.EXTRA_UPLOAD_SUCCESS, true)
                .putInt(NsAnalyticsConstants.EXTRA_RECORDS_UPLOADED, totalRecordsUploaded)
                .build()

            return@withContext Result.success(outputData)

        } catch (e: Exception) {
            Timber.e(e, "NS Analytics upload failed with exception")
            return@withContext Result.retry()
        }
    }

    /**
     * Report progress to the system and any observers
     */
    private fun reportProgress(value: Int, max: Int, message: String, recordsUploaded: Int = 0) {
        if (isStopped) {
            return
        }

        setProgressAsync(
            Data.Builder()
                .putInt("progress", value)
                .putInt("progressMax", max)
                .putString("progressMessage", message)
                .putInt("recordsUploaded", recordsUploaded)
                .build()
        )
    }

    /**
     * Group records by type for batch upload
     */
    private fun groupRecordsByType(records: List<NsAnalyticsQueueEntity>): List<RecordBatch> {
        val groupedRecords = records.groupBy { it.recordType }

        return groupedRecords.map { (type, entities) ->
            val messages = entities.mapNotNull { entity ->
                try {
                    // Parse JSON string as JsonElement to preserve number types (integers stay as integers)
                    JsonParser.parseString(entity.protobufJson)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse JSON for record ${entity.id}")
                    null
                }
            }

            RecordBatch(
                type = type,
                messages = messages
            )
        }
    }


    /**
     * Check device status after detecting deregistration error.
     * This clears local credentials if device is indeed deregistered.
     */
    private suspend fun checkDeviceStatusAfterError() {
        try {
            when (val result = NsAnalyticsUtils.checkDeviceRegistrationStatus(applicationContext)) {
                is NsAnalyticsUtils.DeviceStatusResult.Deregistered -> {
                    // Device is deregistered - clear local credentials
                    Timber.i("Device confirmed as deregistered, clearing local credentials")
                    NsAnalyticsUtils.cleanupAfterDeregistration(applicationContext)
                }

                is NsAnalyticsUtils.DeviceStatusResult.Active -> {
                    Timber.d("Device status check: still active despite error")
                }

                is NsAnalyticsUtils.DeviceStatusResult.CheckFailed -> {
                    Timber.w("Failed to verify device status after error: ${result.reason}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check device status after error")
        }
    }

    /**
     * If automatic uploads were paused due to a quota-exceeded response, clear that pause and
     * resume normal operation.
     *
     * Called whenever an upload run completes without a 402, which signals either that the quota
     * now has room (records were accepted) or that the local queue is empty (in which case staying
     * paused would needlessly strand the device). If quota is still exceeded, the next real upload
     * simply re-pauses.
     */
    private fun resumeFromQuotaPauseIfNeeded() {
        if (!NsAnalyticsSecureStorage.isUploadPausedForQuota(applicationContext)) return

        Timber.i("Upload completed while quota-paused; resuming automatic uploads")
        NsAnalyticsSecureStorage.clearQuotaPause(applicationContext)
        cancelPausedRetryCheck(applicationContext)
        NsAnalyticsNotificationHelper.clearUploadsPausedNotification(applicationContext)

        // Re-arm the periodic schedule if auto-upload is still enabled. schedulePeriodicUpload
        // re-checks MDM and the (now-cleared) pause flag itself, so the clear-then-schedule order
        // above matters.
        if (PreferenceUtils.isNsAnalyticsAutoUpload(applicationContext)) {
            val frequency = NsAnalyticsSecureStorage.getUploadFrequency(applicationContext)
            schedulePeriodicUpload(applicationContext, frequency)
        }
    }

    companion object {
        /** Unique work name for periodic uploads */
        private const val NS_ANALYTICS_UPLOAD_WORK_NAME = "ns_analytics_upload"

        /** Unique work name for immediate/manual uploads (separate from periodic to avoid conflicts) */
        private const val NS_ANALYTICS_IMMEDIATE_UPLOAD_WORK_NAME = "ns_analytics_immediate_upload"

        /** Unique work name for the single delayed re-check after a quota pause */
        private const val NS_ANALYTICS_QUOTA_RECHECK_WORK_NAME = "ns_analytics_quota_recheck"

        /** Error code indicating device is not registered with NS Analytics */
        const val ERROR_CODE_NOT_REGISTERED = "not_registered"

        /** Error code indicating missing credentials (no device token or API URL) */
        const val ERROR_CODE_NO_CREDENTIALS = "no_credentials"

        /**
         * Result of attempting to trigger an immediate upload.
         * Used to provide feedback to the UI about why an upload did or didn't start.
         */
        sealed class TriggerResult {
            /** New upload work was successfully enqueued */
            data class Started(val workId: UUID) : TriggerResult()

            /** An upload is already running or enqueued */
            data object AlreadyRunning : TriggerResult()

            /** No records in the queue to upload */
            data object NoRecords : TriggerResult()

            /** Network is not available */
            data object NoNetwork : TriggerResult()

            /** Upload blocked by MDM policy */
            data object MdmBlocked : TriggerResult()
        }

        /**
         * Check if any upload work (periodic or immediate) is currently running.
         * Uses TAG-based query to see all upload work regardless of unique name.
         * Only blocks if work is actually RUNNING, not just ENQUEUED (scheduled for later).
         */
        private fun isUploadRunning(context: Context): Boolean {
            val workManager = WorkManager.getInstance(context)

            // Check by TAG to see ALL upload work (both periodic and immediate)
            // since they use separate unique names but share the same TAG
            val workInfos = workManager.getWorkInfosByTag(
                NsAnalyticsConstants.NS_ANALYTICS_UPLOAD_WORKER_TAG
            ).get()

            // Only block if work is ACTUALLY running, not just scheduled for later
            return workInfos.any { it.state == WorkInfo.State.RUNNING }
        }

        /**
         * Schedule periodic uploads.
         *
         * @param context The application context
         * @param intervalMinutes The interval between uploads in minutes
         * @param replaceExisting If true, replaces any existing periodic work (use when interval
         *        changes). If false, keeps existing work to avoid triggering immediate upload
         *        (use for initial setup or screen loads).
         */
        @Suppress("unused")
        fun schedulePeriodicUpload(
            context: Context,
            intervalMinutes: Int,
            replaceExisting: Boolean = false
        ) {
            // Check if NS Analytics is allowed via MDM
            if (!MdmUtils.isNsAnalyticsAllowed(context)) {
                Timber.w("NS Analytics upload scheduling prevented by MDM policy")
                return
            }

            // Don't re-arm the recurring schedule while uploads are paused due to an exceeded
            // quota; otherwise opening the screen or starting a survey would restart the doomed
            // every-15-min loop. The pause is cleared on a successful upload (manual or re-check).
            if (NsAnalyticsSecureStorage.isUploadPausedForQuota(context)) {
                Timber.i("NS Analytics periodic upload scheduling skipped: paused due to quota")
                return
            }

            if (intervalMinutes <= 0) {
                // Real-time mode - don't schedule periodic work
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val uploadRequest = PeriodicWorkRequestBuilder<NsAnalyticsUploadWorker>(
                intervalMinutes.toLong(), java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(NsAnalyticsConstants.NS_ANALYTICS_PERIODIC_WORKER_TAG)
                .addTag(NsAnalyticsConstants.NS_ANALYTICS_UPLOAD_WORKER_TAG)
                .build()

            // Use KEEP to preserve existing scheduled work (avoids triggering immediate upload).
            // Use REPLACE only when the interval has changed and we need to reschedule.
            val policy = if (replaceExisting) {
                ExistingPeriodicWorkPolicy.REPLACE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            }

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NS_ANALYTICS_UPLOAD_WORK_NAME,
                policy,
                uploadRequest
            )

            Timber.i("Scheduled NS Analytics uploads every $intervalMinutes minutes (replace=$replaceExisting)")
        }

        /**
         * Cancel periodic uploads
         */
        @Suppress("unused")
        fun cancelPeriodicUpload(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(NS_ANALYTICS_UPLOAD_WORK_NAME)
            Timber.i("Cancelled NS Analytics periodic uploads")
        }

        /**
         * Schedule a single delayed upload attempt after a quota pause, honoring the server's
         * Retry-After hint. When it runs the normal upload logic applies: on success the worker
         * resumes automatic uploads; on another 402 it re-pauses and reschedules the next re-check.
         *
         * Uses a distinct unique work name so it does not collide with the (cancelled) periodic
         * work or any manual upload. It is tagged with the shared upload tag so the ViewModel's
         * observer still tracks it; a delayed/ENQUEUED request is not RUNNING, so it does not block
         * a manual "Upload Now".
         *
         * @param delaySeconds Delay before the re-check, from the server Retry-After hint.
         */
        fun schedulePausedRetryCheck(context: Context, delaySeconds: Long) {
            if (!MdmUtils.isNsAnalyticsAllowed(context)) {
                Timber.w("NS Analytics quota re-check scheduling prevented by MDM policy")
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<NsAnalyticsUploadWorker>()
                .setInitialDelay(delaySeconds, java.util.concurrent.TimeUnit.SECONDS)
                .setConstraints(constraints)
                .addTag(NsAnalyticsConstants.NS_ANALYTICS_UPLOAD_WORKER_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                NS_ANALYTICS_QUOTA_RECHECK_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Timber.i("Scheduled NS Analytics quota re-check in %d seconds", delaySeconds)
        }

        /**
         * Cancel any pending quota re-check.
         */
        fun cancelPausedRetryCheck(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(NS_ANALYTICS_QUOTA_RECHECK_WORK_NAME)
        }

        /**
         * Parse the Retry-After header into a clamped seconds value. The server sends an integer
         * number of seconds (86400); the HTTP-date form is not handled. Falls back to the 24h
         * default when the header is missing or non-numeric, and clamps absurd or non-positive
         * values to the [1, 7 days] range.
         *
         * Visible for testing.
         */
        internal fun parseRetryAfterSeconds(headerValue: String?): Long {
            val parsed = headerValue?.trim()?.toLongOrNull()
                ?: NsAnalyticsConstants.DEFAULT_QUOTA_RETRY_AFTER_SECONDS
            return parsed.coerceIn(1L, NsAnalyticsConstants.MAX_QUOTA_RETRY_AFTER_SECONDS)
        }

        /**
         * Trigger immediate upload.
         * @return [TriggerResult] indicating the outcome of the trigger attempt.
         *         If successful, returns [TriggerResult.Started] with the work ID for observation.
         */
        @Synchronized
        fun triggerImmediateUpload(context: Context): TriggerResult {
            // Check if NS Analytics is allowed via MDM
            if (!MdmUtils.isNsAnalyticsAllowed(context)) {
                Timber.w("NS Analytics immediate upload prevented by MDM policy")
                return TriggerResult.MdmBlocked
            }

            // Check if any upload work is already running or enqueued
            if (isUploadRunning(context)) {
                Timber.d("Upload already running or enqueued")
                return TriggerResult.AlreadyRunning
            }

            // Check for records before enqueuing
            val database = SurveyDatabase.getInstance(context)
            val recordCount = database.nsAnalyticsDao().getPendingRecordCount()
            if (recordCount == 0) {
                Timber.d("No records to upload")
                return TriggerResult.NoRecords
            }

            // Quick check for network availability to provide immediate user feedback.
            // Note: This only checks if the network *declares* internet capability; actual connectivity
            // (captive portals, DNS failures, etc.) will be verified when the worker runs.
            // WorkManager's NetworkType.CONNECTED constraint provides additional verification.
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(network)
            val hasNetwork =
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            if (!hasNetwork) {
                Timber.d("No network connection available")
                return TriggerResult.NoNetwork
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val uploadRequest = OneTimeWorkRequestBuilder<NsAnalyticsUploadWorker>()
                .setConstraints(constraints)
                .addTag(NsAnalyticsConstants.NS_ANALYTICS_UPLOAD_WORKER_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                NS_ANALYTICS_IMMEDIATE_UPLOAD_WORK_NAME,
                ExistingWorkPolicy.REPLACE,  // Replace any stuck immediate work
                uploadRequest
            )
            Timber.i("Triggered immediate NS Analytics upload")
            return TriggerResult.Started(uploadRequest.id)
        }
    }
}