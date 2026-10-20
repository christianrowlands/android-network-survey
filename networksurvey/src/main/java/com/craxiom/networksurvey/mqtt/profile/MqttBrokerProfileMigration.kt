package com.craxiom.networksurvey.mqtt.profile

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.mqtt.OrganizationBroker
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource
import com.craxiom.networksurvey.survey.mdm.RestrictionsSource
import com.craxiom.networksurvey.util.CredentialSecureStorage
import timber.log.Timber

/**
 * Turns the single MQTT broker that earlier versions stored in flat preferences into a broker
 * profile, once. It runs from `Application.onCreate`, after the credentials have been moved into
 * encrypted storage and before the survey setup migration reads whether a broker exists.
 *
 * If anything fails, nothing is deleted and it runs again on the next launch. The flat keys are only
 * removed once the profile and its login are stored. The client ID and device status keys are kept:
 * they belong to the phone.
 */
class MqttBrokerProfileMigration(
    private val preferences: SharedPreferences,
    private val store: MqttBrokerProfileStore,
    private val restrictions: RestrictionsSource,
    private val legacyLogin: LegacyLogin,
    private val organizationCopyName: String,
) {
    /** Reads and deletes the single-broker login that earlier versions stored. */
    interface LegacyLogin {
        /** @return True once the plain-text credentials have been moved to encrypted storage. */
        fun isReady(): Boolean

        /** @return The single broker's login, with empty strings for missing values. */
        fun read(): MqttBrokerLogin

        /** Deletes the single broker's login. */
        fun clear()
    }

    /** Runs the migration unless it already ran. Never throws. */
    fun migrateIfNeeded() {
        if (preferences.contains(NetworkSurveyConstants.PROPERTY_MQTT_BROKER_PROFILES_SCHEMA_VERSION)) return
        if (!legacyLogin.isReady()) {
            Timber.w("The credentials are not in secure storage yet; the MQTT broker migration will run on a later launch")
            return
        }

        try {
            migrate()
        } catch (e: Exception) {
            Timber.e(e, "Could not migrate the MQTT broker to a profile; it will be retried on the next launch")
        }
    }

    private fun migrate() {
        val host = preferences.getString(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST, null)?.trim()
        if (!host.isNullOrEmpty()) {
            val organizationBroker = OrganizationBroker.from(restrictions.snapshot())
            val fromOrganization = organizationBroker != null &&
                organizationBroker.mqttBrokerHost.equals(host, ignoreCase = true)
            val login = legacyLogin.read()
            val profile = MqttBrokerProfile(
                id = MIGRATED_PROFILE_ID,
                name = if (fromOrganization) organizationCopyName else host,
                host = host,
                port = preferences.getInt(MqttConstants.PROPERTY_MQTT_CONNECTION_PORT, MqttConstants.DEFAULT_MQTT_PORT)
                    .takeIf { MqttBrokerProfile.isValidPort(it) } ?: MqttConstants.DEFAULT_MQTT_PORT,
                tlsEnabled = preferences.getBoolean(
                    MqttConstants.PROPERTY_MQTT_CONNECTION_TLS_ENABLED, MqttConstants.DEFAULT_MQTT_TLS_SETTING,
                ),
                topicPrefix = preferences.getString(
                    MqttConstants.PROPERTY_MQTT_TOPIC_PREFIX, MqttConstants.DEFAULT_MQTT_TOPIC_PREFIX,
                ) ?: MqttConstants.DEFAULT_MQTT_TOPIC_PREFIX,
                qos = OrganizationBroker.qosFrom(
                    preferences.getInt(MqttConstants.PROPERTY_MQTT_QOS, MqttConstants.DEFAULT_MQTT_QOS.value),
                ).value,
                origin = if (fromOrganization) MqttBrokerProfile.Origin.ORGANIZATION_COPY else MqttBrokerProfile.Origin.USER,
            )
            store.save(profile, login.username, login.password, select = true)
            Timber.i("Migrated the MQTT broker %s to a profile", host)
        }

        commit {
            FLAT_KEYS.forEach { remove(it) }
            putInt(NetworkSurveyConstants.PROPERTY_MQTT_BROKER_PROFILES_SCHEMA_VERSION, SCHEMA_VERSION)
        }
        legacyLogin.clear()
    }

    @SuppressLint("ApplySharedPref")
    private fun commit(block: SharedPreferences.Editor.() -> Unit) {
        preferences.edit().apply(block).commit()
    }

    companion object {
        /** The storage version written once the migration has run. */
        const val SCHEMA_VERSION = 1

        /**
         * The migrated broker always gets this id, so a migration that stopped after saving the
         * profile replaces it on the retry instead of adding a second one.
         */
        const val MIGRATED_PROFILE_ID = "migrated-broker"

        /** The flat keys that held the single broker. The client ID and device status stay. */
        @JvmField
        val FLAT_KEYS = listOf(
            MqttConstants.PROPERTY_MQTT_CONNECTION_HOST,
            MqttConstants.PROPERTY_MQTT_CONNECTION_PORT,
            MqttConstants.PROPERTY_MQTT_CONNECTION_TLS_ENABLED,
            MqttConstants.PROPERTY_MQTT_TOPIC_PREFIX,
            MqttConstants.PROPERTY_MQTT_QOS,
        )

        /** Runs the migration for the app, with the production stores. */
        @JvmStatic
        fun migrateIfNeeded(context: Context) {
            val appContext = context.applicationContext
            MqttBrokerProfileMigration(
                PreferenceManager.getDefaultSharedPreferences(appContext),
                MqttBrokerProfileStore.create(appContext),
                AndroidRestrictionsSource(appContext),
                SecureLegacyLogin(appContext),
                appContext.getString(R.string.mqtt_broker_organization_copy_name),
            ).migrateIfNeeded()
        }
    }

    /** [LegacyLogin] over [CredentialSecureStorage]. */
    private class SecureLegacyLogin(private val context: Context) : LegacyLogin {
        override fun isReady() = CredentialSecureStorage.isMigrationComplete(context)

        override fun read() = MqttBrokerLogin(
            CredentialSecureStorage.getMqttUsername(context).orEmpty(),
            CredentialSecureStorage.getMqttPassword(context).orEmpty(),
        )

        override fun clear() = CredentialSecureStorage.removeLegacyMqttCredentials(context)
    }
}
