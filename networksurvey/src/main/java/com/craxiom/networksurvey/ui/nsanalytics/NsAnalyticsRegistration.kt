package com.craxiom.networksurvey.ui.nsanalytics

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.api.DeviceRegistrationRequest
import com.craxiom.networksurvey.data.api.NsAnalyticsApi
import com.craxiom.networksurvey.data.api.NsAnalyticsApiFactory
import com.craxiom.networksurvey.data.api.NsAnalyticsQrData
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import com.craxiom.networksurvey.util.NsAnalyticsUtils
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import timber.log.Timber
import java.io.IOException

/** Why connecting this phone to a workspace failed, so the page can say what to do about it. */
sealed interface RegistrationError {
    /** The phone could not reach NS Analytics. */
    data object Network : RegistrationError

    /** The code is invalid, expired, or was already used. */
    data object CodeRejected : RegistrationError

    /** The workspace has no room for another phone. */
    data object DeviceLimit : RegistrationError

    /** The workspace's license has expired. */
    data object License : RegistrationError

    /** NS Analytics had a problem of its own. */
    data object Server : RegistrationError

    /** Anything else, with the server's own words when it gave any. */
    data class Other(val message: String?) : RegistrationError
}

/** The outcome of connecting this phone to a workspace. */
sealed interface RegistrationResult {
    data class Success(val workspaceName: String) : RegistrationResult
    data class Failure(val error: RegistrationError) : RegistrationResult
}

/** The message shown for a [RegistrationError]. */
@get:StringRes
val RegistrationError.messageRes: Int
    get() = when (this) {
        RegistrationError.Network -> R.string.ns_analytics_error_network
        RegistrationError.CodeRejected -> R.string.ns_analytics_error_code_rejected
        RegistrationError.DeviceLimit -> R.string.ns_analytics_error_device_limit
        RegistrationError.License -> R.string.ns_analytics_error_license
        RegistrationError.Server -> R.string.ns_analytics_error_server
        is RegistrationError.Other -> R.string.ns_analytics_error_other
    }

/**
 * Connects this phone to an NS Analytics workspace with the token from its QR code or link, stores
 * the registration, and turns a failed request into a [RegistrationError] the page can explain.
 *
 * @param createApi Builds the API client for the code's server; a test passes a fake server's.
 */
class NsAnalyticsRegistration(
    context: Context,
    private val createApi: (String) -> NsAnalyticsApi = NsAnalyticsApiFactory::createClient,
) {
    private val context = context.applicationContext

    /** Registers this phone. Makes a network call, so call it off the main thread. */
    suspend fun register(qrData: NsAnalyticsQrData): RegistrationResult {
        return try {
            val deviceId = NsAnalyticsSecureStorage.getDeviceId(context)
                ?: Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val response = createApi(qrData.apiUrl).registerDevice(
                DeviceRegistrationRequest(
                    token = qrData.token,
                    deviceId = deviceId,
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                    deviceModel = Build.MODEL,
                    osVersion = "Android ${Build.VERSION.RELEASE}",
                    appVersion = BuildConfig.VERSION_NAME
                )
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                val error = classify(response.code(), response.errorBody()?.string())
                Timber.w("Registration failed: %d %s", response.code(), error)
                return RegistrationResult.Failure(error)
            }

            // Use server-provided workspace name, fall back to the code's name, then a default
            val workspaceName = body.workspaceName
                ?: qrData.workspaceName
                ?: context.getString(R.string.ns_analytics_unknown_workspace)
            NsAnalyticsSecureStorage.storeRegistrationData(
                context = context,
                deviceToken = body.deviceToken,
                workspaceId = body.workspaceId,
                apiUrl = qrData.apiUrl,
                deviceId = body.deviceId,
                workspaceName = workspaceName
            )
            NsAnalyticsSecureStorage.clearQrData(context)
            Timber.i("Device registered successfully for workspace: %s", workspaceName)
            // A running survey that asked for NS Analytics starts sending to it now
            NsAnalyticsUtils.notifyRegistrationChanged(NsAnalyticsUtils.RegistrationChange.CONNECTED)
            RegistrationResult.Success(workspaceName)
        } catch (e: IOException) {
            Timber.w(e, "Could not reach NS Analytics to register")
            RegistrationResult.Failure(RegistrationError.Network)
        } catch (e: Exception) {
            Timber.e(e, "Failed to register device")
            RegistrationResult.Failure(RegistrationError.Other(e.message))
        }
    }

    /**
     * Disconnects this phone from its workspace. The local registration is cleared on success, and
     * also when the server says the phone is already disconnected; queued records are kept so they
     * upload if the phone connects again. Makes a network call, so call it off the main thread.
     *
     * @return Null on success, or why it failed.
     */
    suspend fun unregister(): String? {
        val deviceToken = NsAnalyticsSecureStorage.getDeviceToken(context)
        val apiUrl = NsAnalyticsSecureStorage.getApiUrl(context)
        if (deviceToken == null || apiUrl == null) {
            Timber.w("Missing credentials for unregister")
            return context.getString(R.string.ns_analytics_message_load_failed)
        }
        return try {
            val response = createApi(apiUrl).unregisterDevice("$BEARER$deviceToken")
            when {
                response.isSuccessful && response.body()?.success == true -> {
                    NsAnalyticsUtils.cleanupAfterDeregistration(context, NsAnalyticsUtils.RegistrationChange.DISCONNECTED_BY_USER)
                    Timber.i("Device unregistered successfully")
                    null
                }

                // Already unregistered or invalid token - clear credentials anyway
                response.code() == HTTP_UNAUTHORIZED || response.code() == HTTP_FORBIDDEN -> {
                    NsAnalyticsUtils.cleanupAfterDeregistration(context, NsAnalyticsUtils.RegistrationChange.DISCONNECTED_BY_USER)
                    Timber.w("Device already unregistered or invalid token")
                    null
                }

                else -> {
                    Timber.e("Unregister failed: %d %s", response.code(), response.message())
                    response.message()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Unregister API call failed")
            e.message.orEmpty()
        }
    }

    /**
     * @return The workspace name the server has for this phone now (it can be renamed on the web),
     * or null if it could not be read. Makes a network call, so call it off the main thread.
     */
    suspend fun fetchWorkspaceName(): String? = try {
        val deviceToken = NsAnalyticsSecureStorage.getDeviceToken(context)
        val apiUrl = NsAnalyticsSecureStorage.getApiUrl(context)
        if (deviceToken == null || apiUrl == null) {
            null
        } else {
            createApi(apiUrl).getDeviceStatus("$BEARER$deviceToken").takeIf { it.isSuccessful }?.body()?.workspaceName
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to fetch device status in background, continuing with cached data")
        null
    }

    companion object {
        private const val BEARER = "Bearer "

        /** The 400 messages the server sends for a token that cannot be used. */
        private val TOKEN_ERRORS = setOf("Invalid or expired token", "Token already used", "Token expired")
        private const val DEVICE_LIMIT_PREFIX = "Device limit reached"
        private const val LICENSE_EXPIRED = "LICENSE_EXPIRED"
        private const val LICENSE_DEVICE_LIMIT = "LICENSE_DEVICE_LIMIT"
        private const val ERROR_FIELD = "error"
        private const val ERROR_CODE_FIELD = "error_code"
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_SERVER_ERROR = 500

        /**
         * @return What a failed registration response means. The server answers with
         * `{"error": ..., "error_code": ...}`.
         */
        @JvmStatic
        fun classify(httpCode: Int, errorBody: String?): RegistrationError {
            val json = parse(errorBody)
            val message = json?.stringField(ERROR_FIELD)
            val code = json?.stringField(ERROR_CODE_FIELD)
            return when {
                httpCode >= HTTP_SERVER_ERROR -> RegistrationError.Server
                code == LICENSE_EXPIRED -> RegistrationError.License
                code == LICENSE_DEVICE_LIMIT -> RegistrationError.DeviceLimit
                httpCode == HTTP_BAD_REQUEST && message in TOKEN_ERRORS -> RegistrationError.CodeRejected
                httpCode == HTTP_BAD_REQUEST && message?.startsWith(DEVICE_LIMIT_PREFIX) == true -> RegistrationError.DeviceLimit
                httpCode == HTTP_UNAUTHORIZED -> RegistrationError.CodeRejected
                else -> RegistrationError.Other(message)
            }
        }

        private fun parse(body: String?): JsonObject? = try {
            body?.takeIf { it.isNotBlank() }?.let { JsonParser.parseString(it) }?.takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        }

        private fun JsonObject.stringField(name: String): String? =
            get(name)?.takeIf { it.isJsonPrimitive }?.asString
    }
}
