package com.craxiom.networksurvey.mqtt.profile

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings
import com.craxiom.networksurvey.mqtt.MqttConnectionInfo
import com.craxiom.networksurvey.mqtt.OrganizationBroker
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.util.UUID

/**
 * The saved MQTT broker profiles and which one is selected. Profiles are JSON in the default
 * preferences; each profile's login is in encrypted storage ([MqttBrokerCredentials]). The client
 * ID and the device status setting stay per phone, in their original preference keys.
 *
 * Every write commits synchronously and stores the login before the profile list, so the
 * service's preference listener never sees a profile whose login is not there yet. Call the
 * writing methods off the main thread.
 */
class MqttBrokerProfileStore(
    private val preferences: SharedPreferences,
    private val credentials: MqttBrokerCredentials,
) {
    private val gson = Gson()
    private val lock = Any()

    /** @return Every saved profile, in the order they were added. */
    fun profiles(): List<MqttBrokerProfile> = synchronized(lock) { readProfiles() }

    /** @return The profile with [id], or null. */
    fun profile(id: String): MqttBrokerProfile? = profiles().firstOrNull { it.id == id }

    /** @return The id of the selected profile, or null when none is selected. */
    fun activeId(): String? = synchronized(lock) {
        val id = preferences.getString(NetworkSurveyConstants.PROPERTY_MQTT_ACTIVE_BROKER_PROFILE, null)
        id?.takeIf { candidate -> readProfiles().any { it.id == candidate } }
    }

    /** @return The selected profile, or null when none is selected. */
    fun active(): MqttBrokerProfile? = activeId()?.let { profile(it) }

    /** @return True if any profile is saved. */
    fun hasUserProfiles(): Boolean = profiles().isNotEmpty()

    /** @return The stored login for [id]. */
    fun login(id: String): MqttBrokerLogin = credentials.get(id)

    /** @return The client ID this phone uses with every broker (empty if none is set). */
    fun clientId(): String = preferences.getString(MqttConstants.PROPERTY_MQTT_CLIENT_ID, "").orEmpty()

    /** @return True if this phone also streams its device status over MQTT. */
    fun isDeviceStatusEnabled(): Boolean = preferences.getBoolean(
        NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED,
        NetworkSurveyConstants.DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING,
    )

    /** Selects [id], or no profile when it is null. */
    fun setActive(id: String?) = synchronized(lock) {
        commit { if (id == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, id) }
    }

    /**
     * Inserts or updates [profile] and its login, and bumps its revision.
     *
     * @param password The new password, or null to keep the stored one.
     * @param select True to also make it the selected profile.
     * @return The profile as stored.
     */
    fun save(profile: MqttBrokerProfile, username: String, password: String?, select: Boolean): MqttBrokerProfile =
        synchronized(lock) {
            val current = readProfiles()
            val previous = current.firstOrNull { it.id == profile.id }
            val stored = profile.copy(revision = (previous?.revision ?: profile.revision) + 1)
            credentials.store(stored.id, MqttBrokerLogin(username, password ?: credentials.get(stored.id).password))
            val updated = if (previous == null) current + stored else current.map { if (it.id == stored.id) stored else it }
            writeProfiles(updated, if (select) stored.id else null)
            stored
        }

    /** Deletes the profile with [id] and its login. If it was selected, no profile is selected. */
    fun remove(id: String) = synchronized(lock) {
        val remaining = readProfiles().filterNot { it.id == id }
        val wasActive = preferences.getString(KEY_ACTIVE, null) == id
        commit {
            putString(KEY_PROFILES, gson.toJson(remaining.map { ProfileJson.from(it) }))
            if (wasActive) remove(KEY_ACTIVE)
        }
        credentials.remove(id)
    }

    /**
     * Adds the broker in a scanned QR code and selects it. A saved broker with the same host, port
     * and username is updated instead, since the code is the fresher source. As before profiles, a
     * client ID or device status setting in the code is applied to the phone.
     *
     * @return The saved profile, or null if the code has no usable host.
     */
    fun addFromQr(settings: MqttConnectionSettings, defaultName: String? = null): QrImport? = synchronized(lock) {
        val host = settings.host()?.trim().orEmpty()
        if (host.isEmpty()) return null
        val port = settings.port().takeIf { MqttBrokerProfile.isValidPort(it) } ?: MqttConstants.DEFAULT_MQTT_PORT
        val username = settings.mqttUsername().orEmpty()

        val existing = readProfiles().firstOrNull {
            it.host.equals(host, ignoreCase = true) && it.port == port && credentials.get(it.id).username == username
        }
        val base = existing ?: MqttBrokerProfile(id = newId(), name = defaultName ?: host, host = host)
        val profile = base.copy(
            port = port,
            tlsEnabled = settings.tlsEnabled() ?: base.tlsEnabled,
            topicPrefix = settings.mqttTopicPrefix() ?: base.topicPrefix,
            qos = settings.mqttQos()?.takeIf { OrganizationBroker.qosFrom(it).value == it } ?: base.qos,
        )
        val saved = save(profile, username, settings.mqttPassword(), select = true)

        commit {
            settings.deviceName()?.trim()?.takeIf { it.isNotEmpty() }?.let { putString(MqttConstants.PROPERTY_MQTT_CLIENT_ID, it) }
            settings.deviceStatusStreamEnabled()?.let {
                putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, it)
            }
        }
        QrImport(saved, updatedExisting = existing != null)
    }

    /**
     * Saves a copy of the organization's broker and selects it, so a stream keeps going to the same
     * broker when the user chooses to use their own settings. The copy always has the same id, so
     * doing this twice replaces it. The organization's client ID and device status setting are
     * written to the phone too, so nothing about a running stream changes.
     */
    fun addCopyOfOrganization(info: MqttConnectionInfo, name: String) = synchronized(lock) {
        val profile = MqttBrokerProfile(
            id = ORGANIZATION_COPY_ID,
            name = name,
            host = info.mqttBrokerHost,
            port = info.portNumber,
            tlsEnabled = info.isTlsEnabled,
            topicPrefix = info.topicPrefix ?: MqttConstants.DEFAULT_MQTT_TOPIC_PREFIX,
            qos = info.mqttQos.value,
            origin = MqttBrokerProfile.Origin.ORGANIZATION_COPY,
        )
        commit {
            putString(MqttConstants.PROPERTY_MQTT_CLIENT_ID, info.mqttClientId)
            putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, info.isDeviceStatusStreamEnabled)
        }
        save(profile, info.mqttUsername.orEmpty(), info.mqttPassword.orEmpty(), select = true)
    }

    /**
     * @return The connection to make for the selected profile with this phone's client ID, or null
     * when no profile is selected or the phone has no client ID.
     */
    fun activeConnectionInfo(): MqttConnectionInfo? {
        val profile = active() ?: return null
        val clientId = clientId()
        if (clientId.isBlank()) return null
        val login = credentials.get(profile.id)
        return MqttConnectionInfo(
            profile.host, profile.port, profile.tlsEnabled, clientId, login.username, login.password,
            false, false, false, false, isDeviceStatusEnabled(), false,
            profile.topicPrefix, null, OrganizationBroker.qosFrom(profile.qos),
        )
    }

    /** The result of [addFromQr]. */
    data class QrImport(val profile: MqttBrokerProfile, val updatedExisting: Boolean)

    private fun readProfiles(): List<MqttBrokerProfile> {
        val json = preferences.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            gson.fromJson<List<ProfileJson?>>(json, PROFILE_LIST_TYPE).orEmpty().mapNotNull { it?.toProfile() }
        } catch (e: JsonParseException) {
            Timber.e(e, "The saved MQTT broker profiles could not be read")
            emptyList()
        }
    }

    private fun writeProfiles(profiles: List<MqttBrokerProfile>, select: String?) = commit {
        putString(KEY_PROFILES, gson.toJson(profiles.map { ProfileJson.from(it) }))
        if (select != null) putString(KEY_ACTIVE, select)
    }

    @SuppressLint("ApplySharedPref")
    private fun commit(block: SharedPreferences.Editor.() -> Unit) {
        preferences.edit().apply(block).commit()
    }

    /**
     * The stored form of a profile. Every field is nullable because Gson does not apply Kotlin
     * defaults, so a profile written by an older or newer version is validated before use.
     */
    private data class ProfileJson(
        val id: String?,
        val name: String?,
        val host: String?,
        val port: Int?,
        val tls: Boolean?,
        val topicPrefix: String?,
        val qos: Int?,
        val origin: String?,
        val revision: Long?,
    ) {
        fun toProfile(): MqttBrokerProfile? {
            if (id.isNullOrBlank() || host.isNullOrBlank()) return null
            return MqttBrokerProfile(
                id = id,
                name = name?.takeIf { it.isNotBlank() } ?: host,
                host = host,
                port = port?.takeIf { MqttBrokerProfile.isValidPort(it) } ?: MqttConstants.DEFAULT_MQTT_PORT,
                tlsEnabled = tls ?: MqttConstants.DEFAULT_MQTT_TLS_SETTING,
                topicPrefix = topicPrefix ?: MqttConstants.DEFAULT_MQTT_TOPIC_PREFIX,
                qos = qos ?: MqttConstants.DEFAULT_MQTT_QOS.value,
                origin = MqttBrokerProfile.Origin.entries.firstOrNull { it.name == origin } ?: MqttBrokerProfile.Origin.USER,
                revision = revision ?: 0,
            )
        }

        companion object {
            fun from(profile: MqttBrokerProfile) = ProfileJson(
                profile.id, profile.name, profile.host, profile.port, profile.tlsEnabled,
                profile.topicPrefix, profile.qos, profile.origin.name, profile.revision,
            )
        }
    }

    companion object {
        /** The id of the copy of the organization's broker, so there is never more than one. */
        const val ORGANIZATION_COPY_ID = "organization-copy"

        private const val KEY_PROFILES = NetworkSurveyConstants.PROPERTY_MQTT_BROKER_PROFILES
        private const val KEY_ACTIVE = NetworkSurveyConstants.PROPERTY_MQTT_ACTIVE_BROKER_PROFILE
        private val PROFILE_LIST_TYPE = object : TypeToken<List<ProfileJson?>>() {}.type

        /** @return A new, unique profile id. */
        @JvmStatic
        fun newId(): String = UUID.randomUUID().toString()

        /** @return The store over the default preferences and encrypted credentials. */
        @JvmStatic
        fun create(context: Context): MqttBrokerProfileStore = MqttBrokerProfileStore(
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext),
            SecureMqttBrokerCredentials(context),
        )
    }
}
