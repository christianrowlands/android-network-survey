package com.craxiom.networksurvey.survey.setup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveyPresets
import com.craxiom.networksurvey.survey.model.SurveySetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Tests the one-time migration from the old per-destination preferences to a survey setup.
 */
@RunWith(RobolectricTestRunner::class)
class SurveySetupMigrationTest {

    private lateinit var preferences: SharedPreferences
    private lateinit var store: SurveySetupStore
    private var restrictions: Map<String, Any?> = emptyMap()
    private var registered = false
    private var registrationThrows = false

    @Before
    fun setUp() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("migration_test", Context.MODE_PRIVATE)
        preferences.edit { clear() }
        store = SurveySetupStore(preferences)
    }

    private fun migrate(): Boolean = SurveySetupMigration(
        preferences,
        store,
        { RestrictionsSnapshot(restrictions) },
        { if (registrationThrows) throw IllegalStateException("keystore") else registered },
    ).migrateIfNeeded()

    @Test
    fun `a fresh install gets the NS Analytics preset and runs only once`() {
        assertTrue(migrate())
        assertEquals(SurveyPresets.nsAnalyticsSurvey(), store.getCurrentSetup())
        assertFalse(store.userAutoStart)
        assertFalse(migrate())
    }

    @Test
    fun `an upgrading user keeps the file survey they auto started`() {
        preferences.edit {
            putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING, true)
            putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING, true)
            putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING, false)
        }
        migrate()
        val setup = store.getCurrentSetup()
        assertEquals(setOf(RecordType.CELLULAR, RecordType.GNSS), setup.recordTypes)
        assertEquals(setOf(Destination.LOG_FILES), setup.destinations)
        assertTrue(store.userAutoStart)
    }

    @Test
    fun `a registered NS Analytics user with auto start stays on log files and gets the preset saved`() {
        registered = true
        preferences.edit {
            putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING, true)
            putBoolean(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_WIFI_ENABLED, false)
            putBoolean(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_GNSS_ENABLED, true)
        }
        migrate()

        val current = store.getCurrentSetup()
        assertFalse(Destination.NS_ANALYTICS in current.destinations)
        assertEquals(setOf(Destination.LOG_FILES), current.destinations)

        val preset = store.getSavedSetups().first { it.id == SurveyPresets.NS_ANALYTICS_SURVEY_ID }
        assertEquals(setOf(RecordType.CELLULAR, RecordType.PHONE_STATE, RecordType.GNSS), preset.recordTypes)
        assertTrue(preferences.getBoolean(SurveyPreferenceKeys.MIGRATION_NOTICE_PENDING, false))
    }

    @Test
    fun `MQTT is kept with its old stream types when a user broker is configured`() {
        preferences.edit {
            putString(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST, "broker.example.com")
            putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT, true)
            putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED, true)
            putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_BLUETOOTH_LOGGING, true)
        }
        migrate()
        val setup = store.getCurrentSetup()
        assertEquals(setOf(Destination.LOG_FILES, Destination.MQTT), setup.destinations)
        // Bluetooth from auto start, plus the MQTT defaults (cellular, phone state, Wi-Fi) and GNSS.
        assertEquals(
            setOf(RecordType.BLUETOOTH, RecordType.CELLULAR, RecordType.PHONE_STATE, RecordType.WIFI, RecordType.GNSS),
            setup.recordTypes,
        )
        assertTrue(store.userAutoStart)
    }

    @Test
    fun `community boot start is kept`() {
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_COMMUNITY_UPLOAD_SURVEY_AT_BOOT, true) }
        migrate()
        assertTrue(Destination.COMMUNITY in store.getCurrentSetup().destinations)
        assertTrue(store.userAutoStart)
    }

    @Test
    fun `values the app copied in from MDM are not read as the user's`() {
        restrictions = mapOf(NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING to false)
        preferences.edit {
            putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING, true)
            putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING, true)
        }
        migrate()
        assertEquals(setOf(RecordType.WIFI), store.getCurrentSetup().recordTypes)
    }

    @Test
    fun `a managed phone is seeded with the organization's setup`() {
        restrictions = mapOf(SurveyMdmKeys.SURVEY_CELLULAR to "on", SurveyMdmKeys.DESTINATION_MQTT to "on")
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING, true) }
        migrate()
        assertEquals(SurveySetup.empty(), store.getCurrentSetup())
    }

    @Test
    fun `a configured broker that never started on its own is not added to the survey`() {
        preferences.edit {
            putString(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST, "broker.example.com")
            putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING, true)
        }
        migrate()
        assertEquals(setOf(Destination.LOG_FILES), store.getCurrentSetup().destinations)
        assertEquals(setOf(RecordType.WIFI), store.getCurrentSetup().recordTypes)
    }

    @Test
    fun `a failing registration check does not crash the migration`() {
        registrationThrows = true
        assertTrue(migrate())
        assertEquals(SurveyPresets.nsAnalyticsSurvey(), store.getCurrentSetup())
        assertEquals(SurveySetupStore.CURRENT_SCHEMA_VERSION, store.schemaVersion)
    }

    @Test
    fun `obsolete keys are deleted and the kept MQTT switches survive`() {
        preferences.edit {
            putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING, true)
            putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED, true)
            putBoolean(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_GNSS_ENABLED, true)
            putString("log_file_type", "1")
            putBoolean("grpc_wifi_stream_enabled", true)
            putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, false)
            putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_WATCHLIST_STREAM_ENABLED, false)
        }
        migrate()
        for (key in SurveySetupMigration.OBSOLETE_KEYS) assertFalse(key, preferences.contains(key))
        assertTrue(preferences.contains(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED))
        assertTrue(preferences.contains(NetworkSurveyConstants.PROPERTY_MQTT_WATCHLIST_STREAM_ENABLED))
    }
}
