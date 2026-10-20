package com.craxiom.networksurvey.survey.setup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveyDefaults
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
    private var hasBrokerProfile = false
    private var openedBefore = false

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
        { hasBrokerProfile },
        { openedBefore },
    ).migrateIfNeeded()

    @Test
    fun `a fresh install gets the default, no saved setups, first run, and runs only once`() {
        assertTrue(migrate())
        assertEquals(SurveyDefaults.freshInstallSetup(), store.getCurrentSetup())
        assertTrue(store.getSavedSetups().isEmpty())
        assertTrue(store.onboardingPending)
        assertFalse(store.upgradeWelcomePending)
        assertFalse(store.userAutoStart)
        assertFalse(migrate())
    }

    @Test
    fun `an install that was opened before is an upgrade even with nothing to migrate`() {
        openedBefore = true
        assertTrue(migrate())
        assertFalse(store.onboardingPending)
        assertTrue(store.upgradeWelcomePending)
        // Earlier versions logged to files, so it records the default types to Log files
        assertEquals(SurveySetup.custom(SurveyDefaults.RECORD_TYPES, setOf(Destination.LOG_FILES)), store.getCurrentSetup())
        assertFalse(store.userAutoStart)
    }

    @Test
    fun `an upgrading user gets the welcome instead of first run`() {
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING, true) }
        migrate()
        assertFalse(store.onboardingPending)
        assertTrue(store.upgradeWelcomePending)
    }

    @Test
    fun `version 1 drops the stored built-in presets and keeps the user's setups`() {
        store.schemaVersion = 1
        storeVersionOneSetups()
        assertTrue(migrate())
        assertEquals(listOf("mine"), store.getSavedSetups().map { it.id })
        assertFalse(store.onboardingPending)
        assertEquals(SurveySetupStore.CURRENT_SCHEMA_VERSION, store.schemaVersion)
    }

    @Test
    fun `version 1 on a registered phone keeps the NS Analytics preset as the user's own setup`() {
        registered = true
        store.schemaVersion = 1
        storeVersionOneSetups()
        migrate()
        val setups = store.getSavedSetups()
        assertEquals(listOf("mine", SurveySetupMigration.UPGRADE_NS_ANALYTICS_SETUP_ID), setups.map { it.id })
        val nsAnalytics = setups.last()
        assertEquals(SurveySetupMigration.DEFAULT_NS_ANALYTICS_SETUP_NAME, nsAnalytics.name)
        assertEquals(setOf(RecordType.CELLULAR, RecordType.GNSS), nsAnalytics.recordTypes)
        assertEquals(setOf(Destination.NS_ANALYTICS), nsAnalytics.destinations)
    }

    private fun storeVersionOneSetups() {
        preferences.edit {
            putString(
                SurveyPreferenceKeys.SAVED_SETUPS,
                """[{"id":"builtin_ns_analytics_survey","name":"NS Analytics survey","recordTypes":["cellular","gnss"],""" +
                    """"destinations":["ns_analytics"],"builtIn":true},""" +
                    """{"id":"builtin_drive_test","name":"Drive test","recordTypes":["gnss"],"destinations":["log_files"],"builtIn":true},""" +
                    """{"id":"mine","name":"Mine","recordTypes":["wifi"],"destinations":["mqtt"]}]""",
            )
        }
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
    fun `a registered NS Analytics user with auto start keeps what auto started, without NS Analytics`() {
        registered = true
        preferences.edit {
            putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING, true)
            putBoolean(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_WIFI_ENABLED, false)
            putBoolean(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_GNSS_ENABLED, true)
        }
        migrate()

        // NS Analytics never auto started, so the auto started survey does not gain it
        val current = store.getCurrentSetup()
        assertEquals(setOf(Destination.LOG_FILES), current.destinations)
        assertEquals(setOf(RecordType.CELLULAR), current.recordTypes)
        assertTrue(store.userAutoStart)

        // It stays one tap away as the saved setup, with what the old switches sent

        val saved = store.getSavedSetups().single()
        assertEquals(SurveySetupMigration.UPGRADE_NS_ANALYTICS_SETUP_ID, saved.id)
        assertEquals("NS Analytics", saved.name)
        assertEquals(setOf(Destination.NS_ANALYTICS), saved.destinations)
        assertEquals(setOf(RecordType.CELLULAR, RecordType.PHONE_STATE, RecordType.GNSS), saved.recordTypes)
        assertFalse(store.onboardingPending)
        assertFalse(preferences.contains(LEGACY_MIGRATION_NOTICE_KEY))
    }

    @Test
    fun `a registered user whose community survey started at boot does not auto start NS Analytics`() {
        registered = true
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_COMMUNITY_UPLOAD_SURVEY_AT_BOOT, true) }
        migrate()

        val current = store.getCurrentSetup()
        assertFalse(Destination.NS_ANALYTICS in current.destinations)
        assertTrue(Destination.COMMUNITY in current.destinations)
        assertTrue(store.userAutoStart)
    }

    @Test
    fun `a registered NS Analytics user who never logged files gets NS Analytics only`() {
        registered = true
        preferences.edit { putBoolean(NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_BLUETOOTH_ENABLED, true) }
        migrate()

        val current = store.getCurrentSetup()
        assertEquals(setOf(Destination.NS_ANALYTICS), current.destinations)
        assertEquals(readDefaultNsAnalyticsTypes() + RecordType.BLUETOOTH, current.recordTypes)
        assertFalse(store.userAutoStart)
    }

    @Test
    fun `a managed phone that is registered is still seeded with the organization's setup`() {
        registered = true
        restrictions = mapOf(SurveyMdmKeys.SURVEY_CELLULAR to "on", SurveyMdmKeys.DESTINATION_MQTT to "on")
        migrate()
        assertEquals(SurveySetup.empty(), store.getCurrentSetup())
    }

    @Test
    fun `version 2 only deletes the old upgrade notice flag`() {
        store.schemaVersion = 2
        val working = SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.MQTT))
        store.setCurrentSetup(working)
        preferences.edit { putBoolean(LEGACY_MIGRATION_NOTICE_KEY, true) }

        assertTrue(migrate())
        assertFalse(preferences.contains(LEGACY_MIGRATION_NOTICE_KEY))
        assertEquals(working, store.getCurrentSetup())
        assertTrue(store.getSavedSetups().isEmpty())
        assertEquals(SurveySetupStore.CURRENT_SCHEMA_VERSION, store.schemaVersion)
        assertFalse(migrate())
    }

    /** The record types the old NS Analytics switches sent when the user never changed them. */
    private fun readDefaultNsAnalyticsTypes(): Set<RecordType> = buildSet {
        if (NsAnalyticsConstants.DEFAULT_CELLULAR_ENABLED) add(RecordType.CELLULAR)
        if (NsAnalyticsConstants.DEFAULT_PHONE_STATE_ENABLED) add(RecordType.PHONE_STATE)
        if (NsAnalyticsConstants.DEFAULT_WIFI_ENABLED) add(RecordType.WIFI)
        if (NsAnalyticsConstants.DEFAULT_BLUETOOTH_ENABLED) add(RecordType.BLUETOOTH)
        if (NsAnalyticsConstants.DEFAULT_GNSS_ENABLED) add(RecordType.GNSS)
    }

    @Test
    fun `a broker already moved to a profile still keeps MQTT in the survey`() {
        hasBrokerProfile = true
        preferences.edit {
            putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT, true)
            putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_BLUETOOTH_LOGGING, true)
        }
        assertTrue(migrate())
        assertMigrated(setOf(Destination.LOG_FILES, Destination.MQTT), autoStart = true)
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
        assertMigrated(setOf(Destination.LOG_FILES), autoStart = true)
        assertEquals(setOf(RecordType.WIFI), store.getCurrentSetup().recordTypes)
    }

    @Test
    fun `a failing registration check does not crash the migration`() {
        registrationThrows = true
        assertTrue(migrate())
        assertEquals(SurveyDefaults.freshInstallSetup(), store.getCurrentSetup())
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

    // The upgrade table: each v1 auto start state, with the destinations and auto start it gets.
    // Only MQTT or only Community starting at boot must never add Log files (no files written).

    /** Stores a broker of the user's own, as earlier versions kept it. */
    private fun userBroker() {
        preferences.edit { putString(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST, "broker.example.com") }
    }

    private fun mqttStartsOnBoot() {
        userBroker()
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT, true) }
    }

    private fun communityStartsAtBoot() {
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_COMMUNITY_UPLOAD_SURVEY_AT_BOOT, true) }
    }

    private fun everyStreamFlagOff() {
        preferences.edit {
            putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED, false)
            putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_PHONE_STATE_STREAM_ENABLED, false)
            putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED, false)
            putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED, false)
            putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED, false)
        }
    }

    private fun assertMigrated(destinations: Set<Destination>, autoStart: Boolean) {
        assertEquals(destinations, store.getCurrentSetup().destinations)
        assertEquals(autoStart, store.userAutoStart)
    }

    @Test
    fun `MQTT start on boot only gets MQTT alone, auto started, with its old stream types`() {
        mqttStartsOnBoot()
        migrate()
        assertMigrated(setOf(Destination.MQTT), autoStart = true)
        assertEquals(setOf(RecordType.CELLULAR, RecordType.PHONE_STATE, RecordType.WIFI), store.getCurrentSetup().recordTypes)
    }

    @Test
    fun `MQTT start on boot with every stream flag off is a heartbeat only stream without files`() {
        mqttStartsOnBoot()
        everyStreamFlagOff()
        migrate()
        assertMigrated(setOf(Destination.MQTT), autoStart = true)
        assertTrue(store.getCurrentSetup().recordTypes.isEmpty())
    }

    @Test
    fun `Community start at boot only gets Community alone, auto started`() {
        communityStartsAtBoot()
        migrate()
        assertMigrated(setOf(Destination.COMMUNITY), autoStart = true)
        assertEquals(SurveyDefaults.RECORD_TYPES, store.getCurrentSetup().recordTypes)
    }

    @Test
    fun `MQTT and Community at boot get both, without Log files`() {
        mqttStartsOnBoot()
        communityStartsAtBoot()
        migrate()
        assertMigrated(setOf(Destination.MQTT, Destination.COMMUNITY), autoStart = true)
    }

    @Test
    fun `MQTT and Community at boot with every stream flag off still give Community record types`() {
        mqttStartsOnBoot()
        communityStartsAtBoot()
        everyStreamFlagOff()
        migrate()
        assertMigrated(setOf(Destination.MQTT, Destination.COMMUNITY), autoStart = true)
        assertEquals(SurveyDefaults.RECORD_TYPES, store.getCurrentSetup().recordTypes)
    }

    @Test
    fun `file auto start and MQTT at boot keep both`() {
        mqttStartsOnBoot()
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING, true) }
        migrate()
        assertMigrated(setOf(Destination.LOG_FILES, Destination.MQTT), autoStart = true)
    }

    @Test
    fun `no auto start at all records to Log files and starts nothing`() {
        userBroker()
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING, false) }
        migrate()
        assertMigrated(setOf(Destination.LOG_FILES), autoStart = false)
        assertEquals(SurveyDefaults.RECORD_TYPES, store.getCurrentSetup().recordTypes)
    }

    @Test
    fun `registered without auto start gets NS Analytics and starts nothing`() {
        registered = true
        migrate()
        assertMigrated(setOf(Destination.NS_ANALYTICS), autoStart = false)
    }

    @Test
    fun `a managed phone with the override on uses the user's own preferences`() {
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, true) }
        // Earlier versions copied the organization's values into these preferences; with the
        // override on, the user then changed them, so they are the user's.
        restrictions = mapOf(
            MqttConstants.PROPERTY_MQTT_CONNECTION_HOST to "org.example.com",
            NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT to false,
            NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING to false,
        )
        mqttStartsOnBoot()
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING, true) }
        migrate()
        assertMigrated(setOf(Destination.LOG_FILES, Destination.MQTT), autoStart = true)
        assertTrue(RecordType.WIFI in store.getCurrentSetup().recordTypes)
    }

    @Test
    fun `a managed phone with the override on and only MQTT at boot does not gain Log files`() {
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, true) }
        restrictions = mapOf(NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT to false)
        mqttStartsOnBoot()
        migrate()
        assertMigrated(setOf(Destination.MQTT), autoStart = true)
    }

    @Test
    fun `a managed phone with Community only at boot starts nothing, since uploads are not allowed`() {
        restrictions = mapOf(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_ANALYTICS to true)
        communityStartsAtBoot()
        migrate()
        assertFalse(store.userAutoStart)
        // The user's choice is kept, but the policy keeps it out of what runs
        val effective = SurveyPolicy.evaluate(RestrictionsSnapshot(restrictions), false).effectiveSetup(store.getCurrentSetup())
        assertFalse(Destination.COMMUNITY in effective.destinations)
        assertEquals(setOf(Destination.LOG_FILES), effective.destinations)
    }

    @Test
    fun `a managed phone that allows uploads keeps Community at boot`() {
        restrictions = mapOf(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_EXTERNAL_DATA_UPLOAD to true)
        communityStartsAtBoot()
        migrate()
        assertMigrated(setOf(Destination.COMMUNITY), autoStart = true)
    }

    @Test
    fun `no survey without Log files in the upgrade table gains it`() {
        // Every combination of MQTT and Community at boot, with and without stream flags
        for (mqtt in listOf(false, true)) for (community in listOf(false, true)) for (flagsOff in listOf(false, true)) {
            if (!mqtt && !community) continue
            preferences.edit { clear() }
            if (mqtt) mqttStartsOnBoot()
            if (community) communityStartsAtBoot()
            if (flagsOff) everyStreamFlagOff()
            migrate()
            val case = "mqtt=$mqtt community=$community flagsOff=$flagsOff"
            assertFalse(case, Destination.LOG_FILES in store.getCurrentSetup().destinations)
            assertTrue(case, store.userAutoStart)
        }
    }

    private companion object {
        /** The flag of the removed upgrade notice, which version 3 deletes. */
        const val LEGACY_MIGRATION_NOTICE_KEY = "survey_setup_migration_notice"
    }
}
