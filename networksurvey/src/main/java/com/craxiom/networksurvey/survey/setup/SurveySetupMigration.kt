package com.craxiom.networksurvey.survey.setup

import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.RestrictionsSource
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveyPresets
import com.craxiom.networksurvey.survey.model.SurveySetup
import timber.log.Timber

/**
 * Runs once, on the first launch of the version that introduced survey setups, and turns the old
 * per-destination preferences into a [SurveySetup] so nobody's configuration appears reset.
 *
 * The rules:
 * 1. An upgrading phone keeps the survey it started on its own: the record types it auto started,
 *    plus the old MQTT stream types when MQTT was set to start at boot, sent to Log files (plus
 *    MQTT and the community databases when those were set to start). NS Analytics is never added:
 *    it never auto started before, and adding it could spend a paid quota.
 * 2. A fresh install gets the "NS Analytics survey" preset.
 * 3. On a phone where the organization shapes the survey (MDM), each dimension the organization
 *    manages (record types, destinations) is replaced by the organization's seed, so the effective
 *    setup is what the organization asked for.
 *
 * A phone registered with NS Analytics also gets the "NS Analytics survey" preset seeded from its
 * old NS Analytics record type switches, so choosing that preset sends exactly what it sent before.
 *
 * Values the app had copied from MDM into the user preferences are ignored: any old key that is
 * also present in the managed configuration is treated as an organization value, not the user's.
 */
class SurveySetupMigration(
    private val preferences: SharedPreferences,
    private val store: SurveySetupStore,
    private val restrictionsSource: RestrictionsSource,
    private val isNsAnalyticsRegistered: () -> Boolean,
) {

    /**
     * Runs the migration if it has not run yet. Never throws, because this runs during application
     * start and a crash here would happen on every launch and every boot. A failure is logged and
     * the migration is not marked done, so it runs again on the next launch instead of replacing
     * the user's configuration with defaults; until then the store's defaults apply.
     *
     * @return True if the migration ran and finished, false if it had already run or failed.
     */
    fun migrateIfNeeded(): Boolean {
        if (store.schemaVersion >= SurveySetupStore.CURRENT_SCHEMA_VERSION) return false

        return try {
            migrate()
            store.schemaVersion = SurveySetupStore.CURRENT_SCHEMA_VERSION
            true
        } catch (e: Exception) {
            Timber.e(e, "The survey setup migration failed; it will run again on the next launch")
            false
        }
    }

    private fun migrate() {
        val snapshot = restrictionsSource.snapshot()
        val overrideOn = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false)
        val policy = SurveyPolicy.evaluate(snapshot, overrideOn)
        val nsAnalyticsRegistered = try {
            isNsAnalyticsRegistered()
        } catch (e: Exception) {
            Timber.e(e, "Could not read the NS Analytics registration during the setup migration")
            false
        }

        store.setSavedSetups(
            listOf(
                if (nsAnalyticsRegistered) SurveyPresets.nsAnalyticsSurvey(readNsAnalyticsTypes()) else SurveyPresets.nsAnalyticsSurvey(),
                SurveyPresets.driveTest(),
            )
        )

        if (isUpgrade(snapshot, nsAnalyticsRegistered)) {
            migrateUpgradingUser(snapshot, nsAnalyticsRegistered)
        } else {
            store.setCurrentSetup(SurveyPresets.nsAnalyticsSurvey())
        }

        // On a managed phone the organization's dimensions replace the user's, so the
        // organization's stream does not inherit picks the organization did not make.
        policy.managedSeedSetup(store.getCurrentSetup())?.let {
            Timber.i("Seeding the survey setup from the organization's configuration")
            store.setCurrentSetup(it)
        }

        deleteObsoletePreferences()
    }

    private fun migrateUpgradingUser(snapshot: RestrictionsSnapshot, nsAnalyticsRegistered: Boolean) {
        val autoStartTypes = AUTO_START_KEYS.filter { (_, key) -> userBoolean(snapshot, key, false) }.keys
        // MQTT joins the setup only if it used to start on its own. A broker the user configured but
        // connected by hand is left out, so upgrading never starts streaming data that did not
        // stream before; the user adds MQTT to the survey when they want it.
        val mqttIncluded = hasUserBroker(snapshot) &&
            userBoolean(snapshot, NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT, false)
        val communityIncluded = userBoolean(snapshot, NetworkSurveyConstants.PROPERTY_AUTO_START_COMMUNITY_UPLOAD_SURVEY_AT_BOOT, false)

        val types = autoStartTypes.toMutableSet()
        if (mqttIncluded) {
            types += STREAM_KEYS.filter { (_, keyAndDefault) ->
                userBoolean(snapshot, keyAndDefault.first, keyAndDefault.second)
            }.keys
        }
        if (types.isEmpty()) types += DEFAULT_TYPES

        val destinations = buildSet {
            add(Destination.LOG_FILES)
            if (mqttIncluded) add(Destination.MQTT)
            if (communityIncluded) add(Destination.COMMUNITY)
        }

        store.setCurrentSetup(SurveySetup.custom(types, destinations))

        store.userAutoStart = autoStartTypes.isNotEmpty() || communityIncluded || mqttIncluded

        if (nsAnalyticsRegistered) {
            preferences.edit { putBoolean(SurveyPreferenceKeys.MIGRATION_NOTICE_PENDING, true) }
        }

        Timber.i("Migrated the survey setup: types=%s, destinations=%s, autoStart=%b", types, destinations, store.userAutoStart)
    }

    /**
     * @return True if this phone ran an earlier version: any old survey preference is stored, or
     * the phone is registered with NS Analytics.
     */
    private fun isUpgrade(snapshot: RestrictionsSnapshot, nsAnalyticsRegistered: Boolean): Boolean {
        if (nsAnalyticsRegistered) return true
        return OBSOLETE_KEYS.any { preferences.contains(it) && !snapshot.contains(it) } ||
            hasUserBroker(snapshot)
    }

    private fun hasUserBroker(snapshot: RestrictionsSnapshot): Boolean {
        if (snapshot.contains(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST)) return false
        val host = preferences.getString(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST, null)
        return !host.isNullOrBlank()
    }

    /** Reads a user boolean, ignoring a value the app had copied in from the managed configuration. */
    private fun userBoolean(snapshot: RestrictionsSnapshot, key: String, default: Boolean): Boolean {
        if (snapshot.contains(key)) return default
        return preferences.getBoolean(key, default)
    }

    private fun readNsAnalyticsTypes(): Set<RecordType> = NS_ANALYTICS_KEYS.filter { (_, keyAndDefault) ->
        preferences.getBoolean(keyAndDefault.first, keyAndDefault.second)
    }.keys

    private fun deleteObsoletePreferences() {
        preferences.edit {
            OBSOLETE_KEYS.forEach { remove(it) }
        }
    }

    companion object {
        private val DEFAULT_TYPES = setOf(RecordType.CELLULAR, RecordType.PHONE_STATE, RecordType.WIFI)

        private val AUTO_START_KEYS: Map<RecordType, String> = linkedMapOf(
            RecordType.CELLULAR to NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING,
            RecordType.PHONE_STATE to NetworkSurveyConstants.PROPERTY_AUTO_START_PHONE_STATE_LOGGING,
            RecordType.WIFI to NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING,
            RecordType.BLUETOOTH to NetworkSurveyConstants.PROPERTY_AUTO_START_BLUETOOTH_LOGGING,
            RecordType.GNSS to NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING,
            RecordType.CDR to NetworkSurveyConstants.PROPERTY_AUTO_START_CDR_LOGGING,
        )

        private val STREAM_KEYS: Map<RecordType, Pair<String, Boolean>> = linkedMapOf(
            RecordType.CELLULAR to (NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED to NetworkSurveyConstants.DEFAULT_MQTT_CELLULAR_STREAM_SETTING),
            RecordType.PHONE_STATE to (NetworkSurveyConstants.PROPERTY_MQTT_PHONE_STATE_STREAM_ENABLED to NetworkSurveyConstants.DEFAULT_MQTT_PHONE_STATE_STREAM_SETTING),
            RecordType.WIFI to (NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED to NetworkSurveyConstants.DEFAULT_MQTT_WIFI_STREAM_SETTING),
            RecordType.BLUETOOTH to (NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED to NetworkSurveyConstants.DEFAULT_MQTT_BLUETOOTH_STREAM_SETTING),
            RecordType.GNSS to (NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED to NetworkSurveyConstants.DEFAULT_MQTT_GNSS_STREAM_SETTING),
        )

        private val NS_ANALYTICS_KEYS: Map<RecordType, Pair<String, Boolean>> = linkedMapOf(
            RecordType.CELLULAR to (NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_CELLULAR_ENABLED to NsAnalyticsConstants.DEFAULT_CELLULAR_ENABLED),
            RecordType.PHONE_STATE to (NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_PHONE_STATE_ENABLED to NsAnalyticsConstants.DEFAULT_PHONE_STATE_ENABLED),
            RecordType.WIFI to (NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_WIFI_ENABLED to NsAnalyticsConstants.DEFAULT_WIFI_ENABLED),
            RecordType.BLUETOOTH to (NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_BLUETOOTH_ENABLED to NsAnalyticsConstants.DEFAULT_BLUETOOTH_ENABLED),
            RecordType.GNSS to (NsAnalyticsConstants.PROPERTY_NS_ANALYTICS_GNSS_ENABLED to NsAnalyticsConstants.DEFAULT_GNSS_ENABLED),
        )

        /** The legacy gRPC stream switches. Their constants are removed from the app with this migration. */
        private val GRPC_STREAM_KEYS = listOf(
            "grpc_cellular_stream_enabled",
            "grpc_phone_state_stream_enabled",
            "grpc_wifi_stream_enabled",
            "grpc_bluetooth_stream_enabled",
            "grpc_gnss_stream_enabled",
            "grpc_device_status_stream_enabled",
        )

        /** The GeoPackage era log file type preference, removed with GeoPackage. */
        private const val LEGACY_LOG_FILE_TYPE_KEY = "log_file_type"

        /**
         * Old per-destination preferences that the survey setup replaces. They are deleted once
         * migrated. The MQTT device status and watchlist stream switches are not in this list;
         * they are not record type filters and stay.
         */
        @JvmStatic
        val OBSOLETE_KEYS: List<String> =
            AUTO_START_KEYS.values +
                STREAM_KEYS.values.map { it.first } +
                NS_ANALYTICS_KEYS.values.map { it.first } +
                GRPC_STREAM_KEYS +
                listOf(
                    NetworkSurveyConstants.PROPERTY_AUTO_START_COMMUNITY_UPLOAD_SURVEY_AT_BOOT,
                    NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT,
                    LEGACY_LOG_FILE_TYPE_KEY,
                )
    }
}
