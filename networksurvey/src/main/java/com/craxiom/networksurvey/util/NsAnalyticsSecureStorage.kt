package com.craxiom.networksurvey.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.data.api.NsAnalyticsQrData
import com.google.gson.Gson
import timber.log.Timber

/**
 * Secure storage utility for NS Analytics sensitive data.
 * Uses Android Keystore for encryption and regular SharedPreferences for storage.
 */
object NsAnalyticsSecureStorage {

    private const val PREFS_NAME = "ns_analytics_secure_prefs"
    private var sharedPrefs: SharedPreferences? = null
    private val cryptoManager = NsAnalyticsCryptoManager()
    private val gson = Gson()

    /**
     * Get the shared preferences instance with migration support
     */
    private fun getPrefs(context: Context): SharedPreferences {
        if (sharedPrefs == null) {
            sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return sharedPrefs!!
    }

    /**
     * Encrypt and store a string value
     */
    private fun putEncryptedString(prefs: SharedPreferences, key: String, value: String?) {
        prefs.edit {
            putString(key, cryptoManager.encrypt(value))
        }
    }

    /**
     * Retrieve and decrypt a string value
     */
    private fun getDecryptedString(prefs: SharedPreferences, key: String): String? {
        val encrypted = prefs.getString(key, null)
        return if (encrypted != null) cryptoManager.decrypt(encrypted) else null
    }

    /**
     * Encrypt and store a long value
     */
    private fun putEncryptedLong(prefs: SharedPreferences, key: String, value: Long) {
        prefs.edit {
            putString(key, cryptoManager.encrypt(value.toString()))
        }
    }

    /**
     * Retrieve and decrypt a long value
     */
    private fun getDecryptedLong(prefs: SharedPreferences, key: String, defaultValue: Long): Long {
        val encrypted = prefs.getString(key, null)
        return if (encrypted != null) {
            cryptoManager.decrypt(encrypted)?.toLongOrNull() ?: defaultValue
        } else {
            defaultValue
        }
    }

    /**
     * Encrypt and store an int value
     */
    private fun putEncryptedInt(prefs: SharedPreferences, key: String, value: Int) {
        prefs.edit {
            putString(key, cryptoManager.encrypt(value.toString()))
        }
    }

    /**
     * Retrieve and decrypt an int value
     */
    private fun getDecryptedInt(prefs: SharedPreferences, key: String, defaultValue: Int): Int {
        val encrypted = prefs.getString(key, null)
        return if (encrypted != null) {
            cryptoManager.decrypt(encrypted)?.toIntOrNull() ?: defaultValue
        } else {
            defaultValue
        }
    }

    /**
     * Encrypt and store a boolean value
     */
    private fun putEncryptedBoolean(prefs: SharedPreferences, key: String, value: Boolean) {
        prefs.edit {
            putString(key, cryptoManager.encrypt(value.toString()))
        }
    }

    /**
     * Retrieve and decrypt a boolean value
     */
    private fun getDecryptedBoolean(
        prefs: SharedPreferences,
        key: String,
        defaultValue: Boolean
    ): Boolean {
        val encrypted = prefs.getString(key, null)
        return if (encrypted != null) {
            cryptoManager.decrypt(encrypted)?.toBoolean() ?: defaultValue
        } else {
            defaultValue
        }
    }

    /**
     * Retrieve the device token
     */
    fun getDeviceToken(context: Context): String? {
        return getDecryptedString(
            getPrefs(context),
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_DEVICE_TOKEN
        )
    }

    /**
     * Get workspace ID
     */
    fun getWorkspaceId(context: Context): String? {
        return getDecryptedString(
            getPrefs(context),
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_WORKSPACE_ID
        )
    }

    /**
     * Get workspace name
     */
    fun getWorkspaceName(context: Context): String? {
        return getDecryptedString(
            getPrefs(context),
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_WORKSPACE_NAME
        )
    }

    /**
     * Store workspace name
     */
    fun storeWorkspaceName(context: Context, workspaceName: String?) {
        val prefs = getPrefs(context)
        putEncryptedString(
            prefs,
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_WORKSPACE_NAME,
            workspaceName
        )
    }

    /**
     * Get API URL
     */
    fun getApiUrl(context: Context): String? {
        return getDecryptedString(
            getPrefs(context),
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_API_URL
        )
    }

    /**
     * Store complete registration data
     */
    fun storeRegistrationData(
        context: Context,
        deviceToken: String,
        workspaceId: String,
        apiUrl: String,
        deviceId: String,
        workspaceName: String? = null
    ) {
        val prefs = getPrefs(context)
        putEncryptedString(
            prefs,
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_DEVICE_TOKEN,
            deviceToken
        )
        putEncryptedString(
            prefs,
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_WORKSPACE_ID,
            workspaceId
        )
        putEncryptedString(prefs, NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_API_URL, apiUrl)
        putEncryptedString(prefs, NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_DEVICE_ID, deviceId)
        if (workspaceName != null) {
            putEncryptedString(
                prefs,
                NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_WORKSPACE_NAME,
                workspaceName
            )
        }
        putEncryptedBoolean(prefs, NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_REGISTERED, true)
        putEncryptedLong(prefs, "ns_analytics_registered_at", System.currentTimeMillis())

        // A fresh registration starts with a clean slate; any prior quota pause no longer applies.
        clearQuotaPause(context)
    }

    /**
     * Check if device is registered
     */
    fun isRegistered(context: Context): Boolean {
        return getDecryptedBoolean(
            getPrefs(context),
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_REGISTERED,
            false
        )
    }

    /**
     * Get the registered device ID
     */
    fun getDeviceId(context: Context): String? {
        return getDecryptedString(
            getPrefs(context),
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_DEVICE_ID
        )
    }

    /**
     * Store QR code data temporarily before registration
     */
    fun storeQrData(context: Context, qrData: NsAnalyticsQrData) {
        val prefs = getPrefs(context)
        putEncryptedString(prefs, "ns_analytics_qr_data", gson.toJson(qrData))
    }

    /**
     * Retrieve stored QR data
     */
    fun getQrData(context: Context): NsAnalyticsQrData? {
        val json = getDecryptedString(getPrefs(context), "ns_analytics_qr_data")
        return if (json != null) {
            try {
                gson.fromJson(json, NsAnalyticsQrData::class.java)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse stored QR data")
                null
            }
        } else {
            null
        }
    }

    /**
     * Clear stored QR data
     */
    fun clearQrData(context: Context) {
        getPrefs(context).edit {
            remove("ns_analytics_qr_data")
        }
    }

    /**
     * Store upload frequency setting
     */
    fun setUploadFrequency(context: Context, minutes: Int) {
        val prefs = getPrefs(context)
        putEncryptedInt(prefs, NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_UPLOAD_FREQUENCY, minutes)
    }

    /**
     * Get upload frequency setting
     */
    fun getUploadFrequency(context: Context): Int {
        return getDecryptedInt(
            getPrefs(context),
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_UPLOAD_FREQUENCY,
            NsAnalyticsConstants.DEFAULT_UPLOAD_FREQUENCY
        )
    }

    /**
     * Update last upload timestamp
     */
    fun updateLastUploadTime(context: Context) {
        val prefs = getPrefs(context)
        putEncryptedLong(
            prefs,
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD,
            System.currentTimeMillis()
        )
    }

    /**
     * Get last upload timestamp
     */
    fun getLastUploadTime(context: Context): Long {
        return getDecryptedLong(
            getPrefs(context),
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD,
            0
        )
    }

    /**
     * Save upload completion data for tracking by the ViewModel.
     * This is called by the worker before returning Result.success() or Result.failure()
     * to provide explicit completion data that persists even when WorkManager clears progress data.
     *
     * IMPORTANT: Uses synchronous commit() to ensure data is written before WorkManager
     * transitions the work state. This prevents a race condition where the ViewModel
     * observes the state transition before the completion data is persisted.
     *
     * @param context The application context
     * @param workId The WorkManager work ID that completed
     * @param success Whether the upload succeeded
     * @param recordsCount The number of records uploaded
     * @param errorType The error type/code if upload failed (e.g., QUOTA_EXCEEDED), null for success
     */
    @SuppressLint("ApplySharedPref")
    fun saveUploadCompletion(
        context: Context,
        workId: String,
        success: Boolean,
        recordsCount: Int,
        errorType: String? = null
    ) {
        val prefs = getPrefs(context)
        // Use commit() for synchronous write to ensure data is persisted before
        // WorkManager transitions the work state
        val editor = prefs.edit()
            .putString(
                NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD_WORK_ID,
                cryptoManager.encrypt(workId)
            )
            .putString(
                NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD_SUCCESS,
                cryptoManager.encrypt(success.toString())
            )
            .putString(
                NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD_RECORDS,
                cryptoManager.encrypt(recordsCount.toString())
            )
            .putString(
                NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD_ERROR_TYPE,
                if (errorType != null) cryptoManager.encrypt(errorType) else null
            )

        // Only update the last upload timestamp on successful uploads
        if (success) {
            editor.putString(
                NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD,
                cryptoManager.encrypt(System.currentTimeMillis().toString())
            )
        }

        editor.commit()
    }

    /**
     * Get the work ID of the last completed upload.
     */
    fun getLastUploadWorkId(context: Context): String? {
        return getDecryptedString(
            getPrefs(context),
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD_WORK_ID
        )
    }

    /**
     * Get whether the last upload was successful.
     */
    fun getLastUploadSuccess(context: Context): Boolean {
        return getDecryptedBoolean(
            getPrefs(context),
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD_SUCCESS,
            false
        )
    }

    /**
     * Get the number of records uploaded in the last upload.
     */
    fun getLastUploadRecordsCount(context: Context): Int {
        return getDecryptedInt(
            getPrefs(context),
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD_RECORDS,
            0
        )
    }

    /**
     * Get the error type/code from the last failed upload.
     * Returns null if the last upload succeeded or no error type was recorded.
     */
    fun getLastUploadErrorType(context: Context): String? {
        return getDecryptedString(
            getPrefs(context),
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD_ERROR_TYPE
        )
    }

    /**
     * Clear the upload completion data after it has been processed.
     */
    fun clearLastUploadCompletion(context: Context) {
        getPrefs(context).edit {
            remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD_WORK_ID)
            remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD_SUCCESS)
            remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD_RECORDS)
            remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_LAST_UPLOAD_ERROR_TYPE)
        }
    }

    /**
     * Details describing why automatic uploads are paused after a 402 quota-exceeded response.
     */
    data class QuotaPauseInfo(
        val currentUsage: Int,
        val maxRecords: Int,
        val message: String?,
        val webUrl: String?,
        val retryAfterEpochMillis: Long
    )

    /**
     * Persist the "uploads paused due to quota" state along with the quota details needed to
     * inform the user.
     *
     * Uses synchronous commit() so the worker can immediately read the flag back when deciding
     * whether to reschedule periodic uploads (clear-then-reschedule must be consistent in a
     * single worker pass).
     */
    @SuppressLint("ApplySharedPref")
    fun saveQuotaPause(
        context: Context,
        currentUsage: Int,
        maxRecords: Int,
        message: String?,
        webUrl: String?,
        retryAfterEpochMillis: Long
    ) {
        getPrefs(context).edit()
            .putString(
                NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_UPLOAD_PAUSED_QUOTA,
                cryptoManager.encrypt(true.toString())
            )
            .putString(
                NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_USAGE,
                cryptoManager.encrypt(currentUsage.toString())
            )
            .putString(
                NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_MAX,
                cryptoManager.encrypt(maxRecords.toString())
            )
            .putString(
                NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_MESSAGE,
                if (message != null) cryptoManager.encrypt(message) else null
            )
            .putString(
                NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_WEB_URL,
                if (webUrl != null) cryptoManager.encrypt(webUrl) else null
            )
            .putString(
                NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_RETRY_AFTER,
                cryptoManager.encrypt(retryAfterEpochMillis.toString())
            )
            .commit()
    }

    /**
     * Whether automatic uploads are currently paused because the workspace record quota was exceeded.
     */
    fun isUploadPausedForQuota(context: Context): Boolean {
        return getDecryptedBoolean(
            getPrefs(context),
            NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_UPLOAD_PAUSED_QUOTA,
            false
        )
    }

    /**
     * Retrieve the persisted quota-pause details, or null if uploads are not paused for quota.
     */
    fun getQuotaPauseInfo(context: Context): QuotaPauseInfo? {
        val prefs = getPrefs(context)
        if (!getDecryptedBoolean(
                prefs,
                NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_UPLOAD_PAUSED_QUOTA,
                false
            )
        ) {
            return null
        }
        return QuotaPauseInfo(
            currentUsage = getDecryptedInt(
                prefs, NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_USAGE, 0
            ),
            maxRecords = getDecryptedInt(
                prefs, NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_MAX, 0
            ),
            message = getDecryptedString(
                prefs, NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_MESSAGE
            ),
            webUrl = getDecryptedString(
                prefs, NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_WEB_URL
            ),
            retryAfterEpochMillis = getDecryptedLong(
                prefs, NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_RETRY_AFTER, 0
            )
        )
    }

    /**
     * Clear the "uploads paused due to quota" state.
     *
     * Uses synchronous commit() so a subsequent reschedule check in the same worker pass sees
     * the cleared flag.
     */
    @SuppressLint("ApplySharedPref")
    fun clearQuotaPause(context: Context) {
        getPrefs(context).edit()
            .remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_UPLOAD_PAUSED_QUOTA)
            .remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_USAGE)
            .remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_MAX)
            .remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_MESSAGE)
            .remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_WEB_URL)
            .remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_QUOTA_PAUSE_RETRY_AFTER)
            .commit()
    }

    /**
     * Clear all NS Analytics credentials and settings
     */
    fun clearAllCredentials(context: Context) {
        getPrefs(context).edit {
            remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_DEVICE_TOKEN)
            remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_WORKSPACE_ID)
            remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_WORKSPACE_NAME)
            remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_API_URL)
            remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_REGISTERED)
            remove(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_DEVICE_ID)
            remove("ns_analytics_registered_at") // FIXME we don't ever use the registered at time, delete it or use it
            remove("ns_analytics_qr_data") // FIXME do we store the full QR data and then the part? We only need to store it once
        }

        // A device with no credentials cannot be quota-paused; clear any stale pause state.
        clearQuotaPause(context)

        Timber.i("Cleared all NS Analytics credentials")
    }
}