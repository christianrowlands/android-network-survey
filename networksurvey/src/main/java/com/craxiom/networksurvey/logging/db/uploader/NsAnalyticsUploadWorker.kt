package com.craxiom.networksurvey.logging.db.uploader

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.data.api.NsAnalyticsApiFactory
import com.craxiom.networksurvey.data.api.RecordBatch
import com.craxiom.networksurvey.data.api.UploadBatchRequest
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.NsAnalyticsQueueEntity
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
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

            // Check if registered and get credentials
            if (!NsAnalyticsSecureStorage.isRegistered(applicationContext)) {
                Timber.w("Device not registered with NS Analytics")
                return@withContext Result.failure()
            }

            val deviceToken = NsAnalyticsSecureStorage.getDeviceToken(applicationContext)
                ?: return@withContext Result.failure()
            val apiUrl = NsAnalyticsSecureStorage.getApiUrl(applicationContext)
                ?: return@withContext Result.failure()

            // Create API client
            val apiClient = NsAnalyticsApiFactory.createClient(apiUrl)

            // Get total count of pending records
            val totalPendingCount = database.nsAnalyticsDao().getPendingRecordCount()

            if (totalPendingCount == 0) {
                Timber.d("No pending records to upload")
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
                    "Uploading batch ${batchIndex + 1} of $totalBatches..."
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

                    // Try to get raw response for debugging
                    val errorBody = response.errorBody()?.string()
                    if (!errorBody.isNullOrEmpty()) {
                        Timber.w("Error body content: %s", errorBody)
                    }
                }

                if (response.isSuccessful && responseBody?.isSuccess == true) {
                    // Mark records as uploaded
                    val recordIds = pendingRecords.map { it.id }
                    database.nsAnalyticsDao().markAsUploaded(
                        recordIds,
                        System.currentTimeMillis()
                    )

                    totalRecordsUploaded += pendingRecords.size
                    totalRecordsProcessed += responseBody.processed

                    Timber.i(
                        "Batch %d/%d: Successfully uploaded %d records (processed: %d)",
                        batchIndex + 1, totalBatches, pendingRecords.size, responseBody.processed
                    )

                } else {
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
                    "Completed batch ${batchIndex + 1} of $totalBatches"
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

                NsAnalyticsSecureStorage.updateLastUploadTime(applicationContext)

                // Clean up old uploaded records (keep last 7 days)
                val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                database.nsAnalyticsDao().cleanupOldUploadedRecords(sevenDaysAgo)
            }

            Timber.i(
                "Upload process completed. Total records uploaded: %d (processed: %d)",
                totalRecordsUploaded, totalRecordsProcessed
            )

            // Report final results
            val outputData = Data.Builder()
                .putBoolean(NsAnalyticsConstants.EXTRA_UPLOAD_SUCCESS, true)
                .putInt(NsAnalyticsConstants.EXTRA_RECORDS_UPLOADED, totalRecordsProcessed)
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
    private fun reportProgress(value: Int, max: Int, message: String) {
        if (isStopped) {
            return
        }

        setProgressAsync(
            Data.Builder()
                .putInt("progress", value)
                .putInt("progressMax", max)
                .putString("progressMessage", message)
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

    companion object {
        /**
         * Schedule periodic uploads
         */
        @Suppress("unused")
        fun schedulePeriodicUpload(context: Context, intervalMinutes: Int) {
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
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ns_analytics_periodic_upload",
                ExistingPeriodicWorkPolicy.REPLACE,
                uploadRequest
            )

            Timber.i("Scheduled NS Analytics uploads every $intervalMinutes minutes")
        }

        /**
         * Cancel periodic uploads
         */
        @Suppress("unused")
        fun cancelPeriodicUpload(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork("ns_analytics_periodic_upload")
            Timber.i("Cancelled NS Analytics periodic uploads")
        }

        /**
         * Trigger immediate upload
         */
        @Suppress("unused")
        fun triggerImmediateUpload(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val uploadRequest = OneTimeWorkRequestBuilder<NsAnalyticsUploadWorker>()
                .setConstraints(constraints)
                .addTag(NsAnalyticsConstants.NS_ANALYTICS_UPLOAD_WORKER_TAG)
                .build()

            WorkManager.getInstance(context).enqueue(uploadRequest)
            Timber.i("Triggered immediate NS Analytics upload")
        }
    }
}