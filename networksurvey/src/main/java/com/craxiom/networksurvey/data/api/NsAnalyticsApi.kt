package com.craxiom.networksurvey.data.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * API interface for NS Analytics backend integration.
 * Handles device registration and survey data uploads.
 */
interface NsAnalyticsApi {
    @POST("api/v1/device/register")
    suspend fun registerDevice(
        @Body request: DeviceRegistrationRequest
    ): Response<DeviceRegistrationResponse>

    @POST("api/v1/device/upload")
    suspend fun uploadBatch(
        @Header("Authorization") token: String,
        @Body batch: UploadBatchRequest
    ): Response<UploadResponse>

    @DELETE("api/v1/device/deregister")
    suspend fun deregisterDevice(
        @Header("Authorization") token: String
    ): Response<DeregisterResponse>

    @GET("api/v1/device/status")
    suspend fun getDeviceStatus(
        @Header("Authorization") token: String
    ): Response<DeviceStatusResponse>
}

// Request/Response Data Classes

/**
 * Request payload for device registration
 */
data class DeviceRegistrationRequest(
    @SerializedName("token") val token: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_model") val deviceModel: String,
    @SerializedName("os_version") val osVersion: String,
    @SerializedName("app_version") val appVersion: String
)

/**
 * Response from device registration
 */
data class DeviceRegistrationResponse(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_token") val deviceToken: String,
    @SerializedName("workspace_id") val workspaceId: String,
    @SerializedName("message") val message: String
)

/**
 * QR code data structure
 */
data class NsAnalyticsQrData(
    @SerializedName("token") val token: String,
    @SerializedName("workspace_id") val workspaceId: String,
    @SerializedName("api_url") val apiUrl: String
)

/**
 * Upload batch request containing survey records grouped by type
 */
data class UploadBatchRequest(
    @SerializedName("device_token") val deviceToken: String,
    @SerializedName("batch_id") val batchId: String,
    @SerializedName("records") val records: List<RecordBatch>
)

/**
 * A batch of records of a specific type
 */
data class RecordBatch(
    @SerializedName("type") val type: String, // "lte", "wifi", "bluetooth", "gnss", etc.
    @SerializedName("messages") val messages: List<com.google.gson.JsonElement> // Protobuf messages as JSON
)

/**
 * Response from batch upload
 */
data class UploadResponse(
    @SerializedName("batch_id") val batchId: String,
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("status") val status: String,  // "completed", "failed", etc.
    @SerializedName("message") val message: String?,
    @SerializedName("processed") val processed: Int,
    @SerializedName("failed") val failed: Int,
    @SerializedName("total_records") val totalRecords: Int
) {
    /**
     * Helper property to check if the upload was successful
     */
    val isSuccess: Boolean
        get() = status == "completed" && failed == 0
}

/**
 * Response from device deregistration
 */
data class DeregisterResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String
)

/**
 * Device status response
 */
data class DeviceStatusResponse(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("workspace_id") val workspaceId: String,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("last_seen") val lastSeen: String?,
    @SerializedName("total_records") val totalRecords: Long?
)

/**
 * Factory object for creating NS Analytics API client instances
 */
object NsAnalyticsApiFactory {
    private const val DEFAULT_TIMEOUT_SECONDS = 30L

    fun createClient(baseUrl: String): NsAnalyticsApi {
        // Add logging interceptor for debugging
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Timber.d("NS Analytics HTTP: $message")
        }.apply {
            // Log headers and body for debugging
            level = HttpLoggingInterceptor.Level.BODY
        }

        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NsAnalyticsApi::class.java)
    }
}