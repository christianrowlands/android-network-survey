package com.craxiom.networksurvey.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import timber.log.Timber

/**
 * Secure storage utility for app credentials such as MQTT username/password and OpenCelliD API key.
 * Uses Android Keystore for encryption and regular SharedPreferences for storage.
 */
object CredentialSecureStorage {

    private const val PREFS_NAME = "credential_secure_prefs"
    private const val KEY_MQTT_USERNAME = "mqtt_username"
    private const val KEY_MQTT_PASSWORD = "mqtt_password"
    private const val KEY_OCID_API_KEY = "ocid_api_key"
    private const val KEY_MIGRATION_COMPLETE = "credentials_migrated_to_secure_storage"
    private const val PROFILE_KEY_SEPARATOR = "_"

    private var sharedPrefs: SharedPreferences? = null
    private val cryptoManager = CryptoManager()

    /**
     * Get the shared preferences instance
     */
    private fun getPrefs(context: Context): SharedPreferences {
        if (sharedPrefs == null) {
            sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return sharedPrefs!!
    }

    /**
     * Encrypt and store a string value. Uses commit() for synchronous write to ensure
     * credential data is persisted before returning.
     */
    private fun putEncryptedString(prefs: SharedPreferences, key: String, value: String?) {
        prefs.edit(commit = true) { putString(key, cryptoManager.encrypt(value)) }
    }

    /**
     * Retrieve and decrypt a string value. Logs a warning if encrypted data exists but
     * decryption fails, which may indicate Keystore key invalidation.
     */
    private fun getDecryptedString(prefs: SharedPreferences, key: String): String? {
        val encrypted = prefs.getString(key, null) ?: return null
        val decrypted = cryptoManager.decrypt(encrypted)
        if (decrypted == null) {
            Timber.w("Decryption failed for key '%s' - Keystore key may be invalid", key)
        }
        return decrypted
    }

    /**
     * Retrieve the decrypted username of the single MQTT broker that versions before broker
     * profiles stored. Only the broker profile migration reads it.
     */
    fun getMqttUsername(context: Context): String? {
        return getDecryptedString(getPrefs(context), KEY_MQTT_USERNAME)
    }

    /**
     * Retrieve the decrypted password of the single MQTT broker that versions before broker
     * profiles stored. Only the broker profile migration reads it.
     */
    fun getMqttPassword(context: Context): String? {
        return getDecryptedString(getPrefs(context), KEY_MQTT_PASSWORD)
    }

    /**
     * Delete the single-broker MQTT credentials, once the broker profile migration has copied them
     * to a profile.
     */
    fun removeLegacyMqttCredentials(context: Context) {
        getPrefs(context).edit(commit = true) {
            remove(KEY_MQTT_USERNAME)
            remove(KEY_MQTT_PASSWORD)
        }
    }

    /**
     * Store the username and password of the MQTT broker profile [profileId].
     */
    fun storeBrokerCredentials(context: Context, profileId: String, username: String?, password: String?) {
        val prefs = getPrefs(context)
        putEncryptedString(prefs, brokerUsernameKey(profileId), username)
        putEncryptedString(prefs, brokerPasswordKey(profileId), password)
    }

    /**
     * Retrieve the decrypted username of the MQTT broker profile [profileId].
     */
    fun getBrokerUsername(context: Context, profileId: String): String? =
        getDecryptedString(getPrefs(context), brokerUsernameKey(profileId))

    /**
     * Retrieve the decrypted password of the MQTT broker profile [profileId].
     */
    fun getBrokerPassword(context: Context, profileId: String): String? =
        getDecryptedString(getPrefs(context), brokerPasswordKey(profileId))

    /**
     * Delete the username and password of the MQTT broker profile [profileId].
     */
    fun removeBrokerCredentials(context: Context, profileId: String) {
        getPrefs(context).edit(commit = true) {
            remove(brokerUsernameKey(profileId))
            remove(brokerPasswordKey(profileId))
        }
    }

    private fun brokerUsernameKey(profileId: String) = KEY_MQTT_USERNAME + PROFILE_KEY_SEPARATOR + profileId

    private fun brokerPasswordKey(profileId: String) = KEY_MQTT_PASSWORD + PROFILE_KEY_SEPARATOR + profileId

    /**
     * @return True once the one-time move of plain-text credentials into secure storage has
     * finished. The broker profile migration waits for it, so it never reads a half-moved login.
     */
    fun isMigrationComplete(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_MIGRATION_COMPLETE, false)

    /**
     * Store the OpenCelliD API key
     */
    fun storeOcidApiKey(context: Context, apiKey: String?) {
        val prefs = getPrefs(context)
        putEncryptedString(prefs, KEY_OCID_API_KEY, apiKey)
    }

    /**
     * Retrieve the decrypted OpenCelliD API key
     */
    fun getOcidApiKey(context: Context): String? {
        return getDecryptedString(getPrefs(context), KEY_OCID_API_KEY)
    }

    /**
     * One-time migration from plain-text SharedPreferences to encrypted secure storage.
     *
     * The migration order ensures no data loss if the app crashes mid-migration:
     * 1. Write encrypted values to secure storage
     * 2. Remove plain-text values from default preferences
     * 3. Set the migration-complete flag
     *
     * All writes commit synchronously for crash safety.
     */
    fun migrateFromPlainTextIfNeeded(context: Context) {
        val securePrefs = getPrefs(context)

        if (securePrefs.getBoolean(KEY_MIGRATION_COMPLETE, false)) {
            return
        }

        Timber.i("Starting credential migration from plain-text to secure storage")

        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        val mqttUsername = defaultPrefs.getString(MqttConstants.PROPERTY_MQTT_USERNAME, null)
        val mqttPassword = defaultPrefs.getString(MqttConstants.PROPERTY_MQTT_PASSWORD, null)
        val ocidApiKey = defaultPrefs.getString(NetworkSurveyConstants.PROPERTY_OCID_API_KEY, null)

        try {
            // Pre-encrypt all values and verify encryption succeeded before committing.
            // If any encryption fails (e.g. Keystore unavailable), abort the entire migration
            // to preserve the plain-text credentials. Migration will retry on next app launch.
            val encryptedUsername =
                if (!mqttUsername.isNullOrEmpty()) cryptoManager.encrypt(mqttUsername) else null
            val encryptedPassword =
                if (!mqttPassword.isNullOrEmpty()) cryptoManager.encrypt(mqttPassword) else null
            val encryptedOcid =
                if (!ocidApiKey.isNullOrEmpty()) cryptoManager.encrypt(ocidApiKey) else null

            if ((!mqttUsername.isNullOrEmpty() && encryptedUsername == null) ||
                (!mqttPassword.isNullOrEmpty() && encryptedPassword == null) ||
                (!ocidApiKey.isNullOrEmpty() && encryptedOcid == null)
            ) {
                Timber.e("Encryption failed during migration, aborting to preserve plain-text credentials")
                return
            }

            // Step 1: Write encrypted values to secure storage first
            securePrefs.edit(commit = true) {
                if (encryptedUsername != null) {
                    putString(KEY_MQTT_USERNAME, encryptedUsername)
                }
                if (encryptedPassword != null) {
                    putString(KEY_MQTT_PASSWORD, encryptedPassword)
                }
                if (encryptedOcid != null) {
                    putString(KEY_OCID_API_KEY, encryptedOcid)
                }
            }

            // Step 2: Remove plain-text values from default preferences
            defaultPrefs.edit(commit = true) {
                remove(MqttConstants.PROPERTY_MQTT_USERNAME)
                remove(MqttConstants.PROPERTY_MQTT_PASSWORD)
                remove(NetworkSurveyConstants.PROPERTY_OCID_API_KEY)
            }

            // Step 3: Set the migration-complete flag
            securePrefs.edit(commit = true) { putBoolean(KEY_MIGRATION_COMPLETE, true) }

            Timber.i("Credential migration to secure storage completed successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to migrate credentials to secure storage")
        }
    }
}
