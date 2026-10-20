package com.craxiom.networksurvey.mqtt.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.mqttlibrary.MqttQos
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings
import com.craxiom.networksurvey.mqtt.MqttConnectionInfo
import com.craxiom.networksurvey.mqtt.OrganizationBroker
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The MQTT broker profiles: storing, selecting and removing brokers with their logins, adding one
 * from a QR code, copying the organization's broker for the override, the one-time migration from
 * the single broker of earlier versions, and reading the organization's broker.
 */
@RunWith(RobolectricTestRunner::class)
class MqttBrokerProfileStoreTest {

    private lateinit var preferences: SharedPreferences
    private lateinit var credentials: FakeCredentials
    private lateinit var store: MqttBrokerProfileStore

    @Before
    fun setUp() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("broker_profiles_test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        credentials = FakeCredentials()
        store = MqttBrokerProfileStore(preferences, credentials)
    }

    // Storing and selecting

    @Test
    fun `a new store has no profiles and nothing selected`() {
        assertTrue(store.profiles().isEmpty())
        assertNull(store.active())
        assertFalse(store.hasUserProfiles())
        assertNull(store.activeConnectionInfo())
    }

    @Test
    fun `saving adds a profile with its login, and select makes it active`() {
        val saved = store.save(profile("a", "Lab"), "user", "secret", select = true)

        assertEquals(listOf("Lab"), store.profiles().map { it.name })
        assertEquals(saved, store.active())
        assertEquals(MqttBrokerLogin("user", "secret"), store.login("a"))
        assertEquals(1L, saved.revision)
    }

    @Test
    fun `saving again updates in place, bumps the revision, and a null password keeps the old one`() {
        store.save(profile("a", "Lab"), "user", "secret", select = true)
        val before = preferences.getString(NetworkSurveyConstants.PROPERTY_MQTT_BROKER_PROFILES, null)

        val updated = store.save(profile("a", "Lab").copy(port = 1884), "user2", null, select = false)

        assertEquals(1, store.profiles().size)
        assertEquals(2L, updated.revision)
        assertEquals(1884, store.active()!!.port)
        assertEquals(MqttBrokerLogin("user2", "secret"), store.login("a"))
        // A login-only change still changes the stored list, so the service's listener hears it.
        val passwordOnly = store.save(store.profile("a")!!, "user2", "other", select = false)
        assertEquals(3L, passwordOnly.revision)
        assertFalse(before == preferences.getString(NetworkSurveyConstants.PROPERTY_MQTT_BROKER_PROFILES, null))
    }

    @Test
    fun `selecting switches the active profile`() {
        store.save(profile("a", "Lab"), "", "", select = true)
        store.save(profile("b", "Field"), "", "", select = false)

        store.setActive("b")

        assertEquals("b", store.activeId())
    }

    @Test
    fun `removing the active profile leaves nothing selected and deletes its login`() {
        store.save(profile("a", "Lab"), "user", "secret", select = true)
        store.save(profile("b", "Field"), "", "", select = false)

        store.remove("a")

        assertEquals(listOf("b"), store.profiles().map { it.id })
        assertNull(store.activeId())
        assertFalse(credentials.logins.containsKey("a"))
    }

    @Test
    fun `removing another profile keeps the selection`() {
        store.save(profile("a", "Lab"), "", "", select = true)
        store.save(profile("b", "Field"), "", "", select = false)

        store.remove("b")

        assertEquals("a", store.activeId())
    }

    @Test
    fun `a selected id that no longer exists reads as nothing selected`() {
        preferences.edit(commit = true) { putString(NetworkSurveyConstants.PROPERTY_MQTT_ACTIVE_BROKER_PROFILE, "gone") }
        assertNull(store.activeId())
    }

    @Test
    fun `corrupt or partial JSON never throws`() {
        preferences.edit(commit = true) { putString(NetworkSurveyConstants.PROPERTY_MQTT_BROKER_PROFILES, "{not json") }
        assertTrue(store.profiles().isEmpty())

        preferences.edit(commit = true) {
            putString(NetworkSurveyConstants.PROPERTY_MQTT_BROKER_PROFILES, """[{"id":"a","host":"h","port":99999},{"name":"no id"},null]""")
        }
        val profiles = store.profiles()
        assertEquals(1, profiles.size)
        // An invalid port falls back to the default, and a missing name to the host.
        assertEquals(MqttConstants.DEFAULT_MQTT_PORT, profiles[0].port)
        assertEquals("h", profiles[0].name)
    }

    @Test
    fun `the active connection uses the phone's client ID and device status setting`() {
        preferences.edit(commit = true) {
            putString(MqttConstants.PROPERTY_MQTT_CLIENT_ID, "phone-1")
            putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, false)
        }
        store.save(profile("a", "Lab").copy(topicPrefix = "site/", qos = 2), "user", "secret", select = true)

        val info = store.activeConnectionInfo()!!

        assertEquals("broker.example.com", info.mqttBrokerHost)
        assertEquals("phone-1", info.mqttClientId)
        assertEquals("user", info.mqttUsername)
        assertEquals("secret", info.mqttPassword)
        assertEquals("site/", info.topicPrefix)
        assertEquals(MqttQos.EXACTLY_ONCE, info.mqttQos)
        assertFalse(info.isDeviceStatusStreamEnabled)
    }

    @Test
    fun `without a client ID there is no connection`() {
        store.save(profile("a", "Lab"), "", "", select = true)
        assertNull(store.activeConnectionInfo())
    }

    // QR codes

    @Test
    fun `a QR code adds a broker, selects it, and applies its client ID and device status`() {
        val imported = store.addFromQr(qr(host = "qr.example.com", user = "u", deviceName = "phone-9", deviceStatus = false))!!

        assertFalse(imported.updatedExisting)
        assertEquals("qr.example.com", imported.profile.name)
        assertEquals(imported.profile.id, store.activeId())
        assertEquals(MqttBrokerLogin("u", "p"), store.login(imported.profile.id))
        assertEquals("phone-9", store.clientId())
        assertFalse(store.isDeviceStatusEnabled())
    }

    @Test
    fun `a QR code for a saved broker updates it instead of adding another`() {
        val first = store.addFromQr(qr(host = "qr.example.com", user = "u"))!!
        store.setActive(null)

        val second = store.addFromQr(qr(host = "QR.example.com", user = "u", topicPrefix = "new/"))!!

        assertTrue(second.updatedExisting)
        assertEquals(first.profile.id, second.profile.id)
        assertEquals(1, store.profiles().size)
        assertEquals("new/", store.active()!!.topicPrefix)
    }

    @Test
    fun `a QR code without a host is not a broker`() {
        assertNull(store.addFromQr(qr(host = " ", user = "")))
        assertTrue(store.profiles().isEmpty())
    }

    // The organization's broker

    @Test
    fun `copying the organization's broker twice keeps one copy and the organization's client ID`() {
        val organization = organizationInfo()
        store.addCopyOfOrganization(organization, "Copy")
        store.addCopyOfOrganization(organization, "Copy")

        val profiles = store.profiles()
        assertEquals(1, profiles.size)
        assertEquals(MqttBrokerProfileStore.ORGANIZATION_COPY_ID, store.activeId())
        assertEquals(MqttBrokerProfile.Origin.ORGANIZATION_COPY, profiles[0].origin)
        assertEquals("org-client", store.clientId())
        assertEquals(MqttBrokerLogin("org-user", "org-pass"), store.login(MqttBrokerProfileStore.ORGANIZATION_COPY_ID))
    }

    @Test
    fun `the organization's broker is read from the managed configuration`() {
        val snapshot = RestrictionsSnapshot(
            mapOf(
                MqttConstants.PROPERTY_MQTT_CONNECTION_HOST to "mqtt.example.org",
                MqttConstants.PROPERTY_MQTT_CONNECTION_PORT to 8883,
                MqttConstants.PROPERTY_MQTT_CLIENT_ID to "device-1",
                MqttConstants.PROPERTY_MQTT_PASSWORD to "pw",
                MqttConstants.PROPERTY_MQTT_QOS to 7,
            )
        )
        val info = OrganizationBroker.from(snapshot)!!
        assertEquals("mqtt.example.org", info.mqttBrokerHost)
        assertEquals(8883, info.portNumber)
        assertEquals(MqttConstants.DEFAULT_MQTT_QOS, info.mqttQos)
        assertTrue(OrganizationBroker.setsPassword(snapshot))
    }

    @Test
    fun `there is no organization broker without both a host and a client ID`() {
        assertNull(OrganizationBroker.from(RestrictionsSnapshot(mapOf(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST to "h"))))
        assertNull(OrganizationBroker.from(RestrictionsSnapshot(mapOf(MqttConstants.PROPERTY_MQTT_CLIENT_ID to "c"))))
        assertNull(OrganizationBroker.from(RestrictionsSnapshot.EMPTY))
    }

    // Migration from the single broker of earlier versions

    @Test
    fun `the single broker becomes one selected profile and the flat keys go`() {
        flatBroker(host = "old.example.com")
        val legacy = FakeLegacyLogin(MqttBrokerLogin("old-user", "old-pass"))

        migration(legacy).migrateIfNeeded()

        val profile = store.active()!!
        assertEquals(MqttBrokerProfileMigration.MIGRATED_PROFILE_ID, profile.id)
        assertEquals("old.example.com", profile.name)
        assertEquals(1884, profile.port)
        assertFalse(profile.tlsEnabled)
        assertEquals("pre/", profile.topicPrefix)
        assertEquals(2, profile.qos)
        assertEquals(MqttBrokerProfile.Origin.USER, profile.origin)
        assertEquals(MqttBrokerLogin("old-user", "old-pass"), store.login(profile.id))
        MqttBrokerProfileMigration.FLAT_KEYS.forEach { assertFalse(it, preferences.contains(it)) }
        assertTrue(legacy.cleared)
        // The phone's own settings stay.
        assertEquals("phone-1", store.clientId())
        assertFalse(store.isDeviceStatusEnabled())
    }

    @Test
    fun `running the migration twice changes nothing`() {
        flatBroker(host = "old.example.com")
        migration(FakeLegacyLogin()).migrateIfNeeded()
        val first = store.profiles()

        flatBroker(host = "again.example.com")
        migration(FakeLegacyLogin()).migrateIfNeeded()

        assertEquals(first, store.profiles())
    }

    @Test
    fun `no saved broker means no profile, and the migration is done`() {
        migration(FakeLegacyLogin()).migrateIfNeeded()
        assertTrue(store.profiles().isEmpty())
        assertTrue(preferences.contains(NetworkSurveyConstants.PROPERTY_MQTT_BROKER_PROFILES_SCHEMA_VERSION))
    }

    @Test
    fun `the organization's broker left in the flat keys becomes a copy of it`() {
        flatBroker(host = "mqtt.example.org")
        val snapshot = RestrictionsSnapshot(
            mapOf(
                MqttConstants.PROPERTY_MQTT_CONNECTION_HOST to "mqtt.example.org",
                MqttConstants.PROPERTY_MQTT_CLIENT_ID to "device-1",
            )
        )

        MqttBrokerProfileMigration(preferences, store, { snapshot }, FakeLegacyLogin(), "Copy").migrateIfNeeded()

        assertEquals(MqttBrokerProfile.Origin.ORGANIZATION_COPY, store.active()!!.origin)
        assertEquals("Copy", store.active()!!.name)
    }

    @Test
    fun `an unfinished credentials migration waits and keeps the flat keys`() {
        flatBroker(host = "old.example.com")
        migration(FakeLegacyLogin(ready = false)).migrateIfNeeded()

        assertTrue(store.profiles().isEmpty())
        assertTrue(preferences.contains(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST))
        assertFalse(preferences.contains(NetworkSurveyConstants.PROPERTY_MQTT_BROKER_PROFILES_SCHEMA_VERSION))
    }

    @Test
    fun `a failing credential store keeps the flat keys for the next launch`() {
        flatBroker(host = "old.example.com")
        val failing = MqttBrokerProfileStore(preferences, object : MqttBrokerCredentials {
            override fun get(id: String) = MqttBrokerLogin("", "")
            override fun store(id: String, login: MqttBrokerLogin) = throw IllegalStateException("keystore")
            override fun remove(id: String) = Unit
        })

        MqttBrokerProfileMigration(preferences, failing, { RestrictionsSnapshot.EMPTY }, FakeLegacyLogin(), "Copy").migrateIfNeeded()

        assertTrue(preferences.contains(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST))
        assertFalse(preferences.contains(NetworkSurveyConstants.PROPERTY_MQTT_BROKER_PROFILES_SCHEMA_VERSION))
        // The next launch, with a working store, migrates.
        migration(FakeLegacyLogin()).migrateIfNeeded()
        assertNotNull(store.active())
    }

    private fun migration(legacy: FakeLegacyLogin) =
        MqttBrokerProfileMigration(preferences, store, { RestrictionsSnapshot.EMPTY }, legacy, "Copy")

    private fun flatBroker(host: String) = preferences.edit(commit = true) {
        putString(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST, host)
        putInt(MqttConstants.PROPERTY_MQTT_CONNECTION_PORT, 1884)
        putBoolean(MqttConstants.PROPERTY_MQTT_CONNECTION_TLS_ENABLED, false)
        putString(MqttConstants.PROPERTY_MQTT_TOPIC_PREFIX, "pre/")
        putInt(MqttConstants.PROPERTY_MQTT_QOS, 2)
        putString(MqttConstants.PROPERTY_MQTT_CLIENT_ID, "phone-1")
        putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, false)
    }

    private fun profile(id: String, name: String) = MqttBrokerProfile(id = id, name = name, host = "broker.example.com")

    private fun qr(host: String, user: String, deviceName: String? = null, deviceStatus: Boolean? = null, topicPrefix: String? = null) =
        MqttConnectionSettings.Builder()
            .host(host)
            .port(8883)
            .tlsEnabled(true)
            .mqttUsername(user)
            .mqttPassword("p")
            .deviceName(deviceName)
            .deviceStatusStreamEnabled(deviceStatus)
            .mqttTopicPrefix(topicPrefix)
            .build()

    private fun organizationInfo() = MqttConnectionInfo(
        "mqtt.example.org", 8883, true, "org-client", "org-user", "org-pass",
        false, false, false, false, true, false, "", null, MqttQos.AT_LEAST_ONCE,
    )

    /** In-memory logins: Robolectric has no Android Keystore. */
    private class FakeCredentials : MqttBrokerCredentials {
        val logins = mutableMapOf<String, MqttBrokerLogin>()
        override fun get(id: String) = logins[id] ?: MqttBrokerLogin("", "")
        override fun store(id: String, login: MqttBrokerLogin) {
            logins[id] = login
        }

        override fun remove(id: String) {
            logins.remove(id)
        }
    }

    private class FakeLegacyLogin(
        private val login: MqttBrokerLogin = MqttBrokerLogin("", ""),
        private val ready: Boolean = true,
    ) : MqttBrokerProfileMigration.LegacyLogin {
        var cleared = false
        override fun isReady() = ready
        override fun read() = login
        override fun clear() {
            cleared = true
        }
    }
}
