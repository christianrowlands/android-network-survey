package com.craxiom.networksurvey.util

import android.content.Context
import androidx.work.WorkManager
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.data.api.ApiErrorResponse
import com.craxiom.networksurvey.data.api.NsAnalyticsApiFactory
import com.craxiom.networksurvey.data.api.QuotaErrorResponse
import com.craxiom.networksurvey.logging.db.uploader.NsAnalyticsUploadWorker
import com.craxiom.networksurvey.ui.nsanalytics.NsAnalyticsNotificationHelper
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import timber.log.Timber

/**
 * Utility functions for NS Analytics operations.
 *
 * This class provides shared functionality used across ViewModels, Workers,
 * and Services for NS Analytics integration. It centralizes common operations
 * like error parsing, cleanup, and device status checking.
 */
object NsAnalyticsUtils {

    private val gson = Gson()

    /**
     * Parse error code from Retrofit error response body.
     *
     * Used to detect specific error conditions like DEVICE_DEREGISTERED
     * from backend API responses. Properly closes the response body to
     * prevent resource leaks.
     *
     * @param response The Retrofit response containing an error
     * @return The error code string if present, null otherwise
     */
    fun parseErrorCode(response: Response<*>): String? {
        return try {
            // Use 'use' extension to ensure ResponseBody is properly closed
            response.errorBody()?.use { errorBody ->
                val errorString = errorBody.string()
                if (errorString.isNotEmpty()) {
                    val errorResponse = gson.fromJson(errorString, ApiErrorResponse::class.java)
                    errorResponse.errorCode
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse API error response")
            null
        }
    }

    /**
     * Parse error code from already-read error response body string.
     *
     * Used when the error body has already been consumed and needs to be
     * parsed from a string. This prevents double consumption of the response body.
     *
     * @param errorBodyString The error body as a string
     * @return The error code string if present, null otherwise
     */
    fun parseErrorCodeFromString(errorBodyString: String): String? {
        return try {
            if (errorBodyString.isNotEmpty()) {
                val errorResponse = gson.fromJson(errorBodyString, ApiErrorResponse::class.java)
                errorResponse.errorCode
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse API error response from string")
            null
        }
    }

    /**
     * Parse quota error details from Retrofit error response body.
     *
     * Used to extract detailed quota information when QUOTA_EXCEEDED error
     * is detected. The error body must be read as a string before calling
     * this function since ResponseBody can only be consumed once.
     *
     * @param errorBodyString The error body as a string
     * @return QuotaErrorResponse if parsing succeeds, null otherwise
     */
    fun parseQuotaError(errorBodyString: String): QuotaErrorResponse? {
        return try {
            if (errorBodyString.isNotEmpty()) {
                gson.fromJson(errorBodyString, QuotaErrorResponse::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse quota error response")
            null
        }
    }

    /**
     * Format a number with locale-appropriate thousands separators.
     *
     * Uses the system's default locale to format numbers according to local
     * conventions (e.g., comma in US, period in Europe, space in India).
     *
     * @param number The number to format
     * @return Formatted string with thousands separators (e.g., "1,234,567")
     */
    fun formatNumberWithThousandsSeparator(number: Int): String {
        return java.text.NumberFormat.getNumberInstance().format(number)
    }

    /**
     * Performs complete local cleanup when device is deregistered.
     *
     * This is the canonical cleanup operation for device deregistration,
     * whether initiated from the device or detected from the backend.
     *
     * Operations performed:
     * - Clears all encrypted credentials from secure storage
     * - Cancels periodic upload work
     * - Cancels all pending upload tasks
     *
     * Note: This does NOT clear the local record queue, allowing users
     * to preserve collected data for potential re-registration.
     *
     * @param context Android context
     */
    fun cleanupAfterDeregistration(context: Context) {
        // Clear all stored credentials and registration data
        NsAnalyticsSecureStorage.clearAllCredentials(context)

        // Cancel all upload work to prevent further API calls with invalid credentials
        NsAnalyticsUploadWorker.cancelPeriodicUpload(context)

        WorkManager.getInstance(context).apply {
            cancelAllWorkByTag(NsAnalyticsConstants.NS_ANALYTICS_PERIODIC_WORKER_TAG)
            cancelAllWorkByTag(NsAnalyticsConstants.NS_ANALYTICS_UPLOAD_WORKER_TAG)
        }

        // Dismiss the "uploads paused" notification if it was showing (the pause state itself was
        // already cleared by clearAllCredentials above).
        NsAnalyticsNotificationHelper.clearUploadsPausedNotification(context)

        Timber.i("Completed deregistration cleanup: cleared credentials and cancelled uploads")
    }

    /**
     * Result of checking device registration status with the backend.
     */
    sealed class DeviceStatusResult {
        /** Device is active and registered */
        object Active : DeviceStatusResult()

        /** Device has been deregistered */
        data class Deregistered(
            val deregisteredAt: String?,
            val source: String?,
            val deregisteredBy: String?,
            val reason: String?
        ) : DeviceStatusResult()

        /** Could not check status (network error, missing credentials, etc.) */
        data class CheckFailed(val reason: String) : DeviceStatusResult()
    }

    /**
     * Check device registration status with the NS Analytics backend.
     *
     * This is a pure function that performs the API call and returns the result
     * without side effects (no credential clearing, no UI updates).
     *
     * Callers should handle the result and perform appropriate actions:
     * - On Deregistered: call cleanupAfterDeregistration(), update UI, stop surveys
     * - On Active: optionally update workspace name or other metadata
     * - On CheckFailed: log and continue with cached data
     *
     * @param context Android context
     * @return DeviceStatusResult indicating the registration status
     */
    suspend fun checkDeviceRegistrationStatus(
        context: Context
    ): DeviceStatusResult = withContext(Dispatchers.IO) {
        try {
            // Check if registered locally
            if (!NsAnalyticsSecureStorage.isRegistered(context)) {
                Timber.d("Device not registered locally, skipping backend status check")
                return@withContext DeviceStatusResult.CheckFailed("Not registered locally")
            }

            val deviceToken = NsAnalyticsSecureStorage.getDeviceToken(context)
                ?: return@withContext DeviceStatusResult.CheckFailed("Missing device token")

            val apiUrl = NsAnalyticsSecureStorage.getApiUrl(context)
                ?: return@withContext DeviceStatusResult.CheckFailed("Missing API URL")

            // Call backend API
            val api = NsAnalyticsApiFactory.createClient(apiUrl)
            val statusResponse = api.getDeviceStatus("Bearer $deviceToken")

            // Check for 403/401 - middleware blocks deregistered devices
            if (statusResponse.code() == 403 || statusResponse.code() == 401) {
                val errorCode = parseErrorCode(statusResponse)
                if (errorCode == NsAnalyticsConstants.ERROR_CODE_DEVICE_DEREGISTERED) {
                    Timber.i("Device deregistration detected via ${statusResponse.code()} response")
                    return@withContext DeviceStatusResult.Deregistered(
                        deregisteredAt = null,
                        source = null,
                        deregisteredBy = null,
                        reason = null
                    )
                } else {
                    Timber.w("Auth error during status check: ${statusResponse.code()}, error_code: $errorCode")
                    return@withContext DeviceStatusResult.CheckFailed("Auth error: $errorCode")
                }
            }

            // Check successful responses
            if (statusResponse.isSuccessful && statusResponse.body() != null) {
                val status = statusResponse.body()!!

                return@withContext if (!status.isActive) {
                    Timber.i("Device deregistration detected via 200 response")
                    DeviceStatusResult.Deregistered(
                        deregisteredAt = status.deregisteredAt,
                        source = status.deregistrationSource,
                        deregisteredBy = status.deregisteredByEmail,
                        reason = status.deregistrationReason
                    )
                } else {
                    DeviceStatusResult.Active
                }
            } else {
                Timber.w("Status check failed: ${statusResponse.code()} ${statusResponse.message()}")
                return@withContext DeviceStatusResult.CheckFailed("HTTP ${statusResponse.code()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Device status check failed with exception")
            return@withContext DeviceStatusResult.CheckFailed(e.message ?: "Unknown error")
        }
    }
}
