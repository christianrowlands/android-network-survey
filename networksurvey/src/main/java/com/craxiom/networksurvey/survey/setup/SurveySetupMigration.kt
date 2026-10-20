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
import com.craxiom.networksurvey.survey.model.SurveyDefaults
import com.craxiom.networksurvey.survey.model.SurveySetup
import timber.log.Timber

/**
 * Runs once, on the first launch of the version that introduced survey setups, and turns the old
 * per-destination preferences into a [SurveySetup] so nobody's configuration appears reset.
 *
 * The rules:
 * 1. An upgrading phone keeps the survey it started on its own: the record types it auto started,
 *    plus the old MQTT stream types when MQTT was set to start at boot, sent to Log files (plus
 *    MQTT and the community databases when those were set to start). A phone registered with NS
 *    Analytics that auto started nothing gets NS Analytics turned on instead, with the record
 *    types its old NS Analytics switches sent, so the survey picks up where the user left off.
 *    NS Analytics never auto started before, so it is not added to a setup that auto starts:
 *    starting it at boot would spend workspace quota on record types it never received.
 * 2. A fresh install gets the fresh install default (Cellular, Phone state and Wi-Fi sent to NS
 *    Analytics) and is marked for first run. An install that was opened before but never touched
 *    the survey settings is still an upgrade, and follows rule 1 (the default record types sent to
 *    Log files, as earlier versions logged).
 * Every upgrade is marked for the one-time welcome that says what changed and shows its survey.
 * 3. On a phone where the organization shapes the survey (MDM), each dimension the organization
 *    manages (record types, destinations) is replaced by the organization's seed, so the effective
 *    setup is what the organization asked for.
 *
 * A phone registered with NS Analytics also gets a saved setup named "NS Analytics" seeded from its
 * old NS Analytics record type switches, so choosing it sends exactly what it sent before.
 *
 * Version 2 removes the built-in presets that development builds of version 1 stored, keeping the
 * NS Analytics one as the user's own setup on a registered phone. Version 3 deletes the flag of the
 * one-time upgrade notice the Survey screen no longer shows.
 *
 * Values the app had copied from MDM into the user preferences are ignored: any old key that is
 * also present in the managed configuration is treated as an organization value, not the user's.
 */
class SurveySetupMigration(
    private val preferences: SharedPreferences,
    private val store: SurveySetupStore,
    private val restrictionsSource: RestrictionsSource,
    private val isNsAnalyticsRegistered: () -> Boolean,
    private val hasUserBrokerProfile: () -> Boolean = { false },
    private val wasOpenedBefore: () -> Boolean,
    private val nsAnalyticsSetupName: String = DEFAULT_NS_ANALYTICS_SETUP_NAME,
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
        val from = store.schemaVersion
        if (from >= SurveySetupStore.CURRENT_SCHEMA_VERSION) return false

        return try {
            if (from < 1) migrate() else if (from < 2) removeLegacyBuiltIns()
            if (from < 3) preferences.edit { remove(LEGACY_MIGRATION_NOTICE_KEY) }
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
        val nsAnalyticsRegistered = isRegistered()

        if (nsAnalyticsRegistered) store.setSavedSetups(listOf(nsAnalyticsSetup(readNsAnalyticsTypes())))

        // The settings defaults are written on the first launch, so an install that has them was
        // opened before: an upgrade, which gets the welcome instead of first run.
        if (isUpgrade(snapshot, nsAnalyticsRegistered) || wasOpenedBefore()) {
            migrateUpgradingUser(snapshot, nsAnalyticsRegistered)
            store.upgradeWelcomePending = true
        } else {
            store.setCurrentSetup(SurveyDefaults.freshInstallSetup())
            store.onboardingPending = true
        }

        // On a managed phone the organization's dimensions replace the user's, so the
        // organization's stream does not inherit picks the organization did not make.
        policy.managedSeedSetup(store.getCurrentSetup())?.let {
            Timber.i("Seeding the survey setup from the organization's configuration")
            store.setCurrentSetup(it)
        }

        deleteObsoletePreferences()
    }

    /**
     * Version 1 to 2: drops the built-in presets. On a registered phone the NS Analytics preset,
     * which version 1 seeded from the old NS Analytics switches, becomes the user's own setup.
     */
    private fun removeLegacyBuiltIns() {
        val removed = store.removeLegacyBuiltIns()
        val nsAnalytics = removed.firstOrNull { it.id == LEGACY_NS_ANALYTICS_PRESET_ID }
        if (nsAnalytics != null && isRegistered()) {
            store.setSavedSetups(store.getSavedSetups() + nsAnalyticsSetup(nsAnalytics.recordTypes))
        }
    }

    private fun nsAnalyticsSetup(recordTypes: Set<RecordType>) = SurveySetup(
        id = UPGRADE_NS_ANALYTICS_SETUP_ID,
        name = nsAnalyticsSetupName,
        recordTypes = recordTypes,
        destinations = setOf(Destination.NS_ANALYTICS),
    )

    private fun isRegistered(): Boolean = try {
        isNsAnalyticsRegistered()
    } catch (e: Exception) {
        Timber.e(e, "Could not read the NS Analytics registration during the setup migration")
        false
    }

    private fun migrateUpgradingUser(snapshot: RestrictionsSnapshot, nsAnalyticsRegistered: Boolean) {
        val autoStartTypes = AUTO_START_KEYS.filter { (_, key) -> userBoolean(snapshot, key, false) }.keys
        // MQTT joins the setup only if it used to start on its own. A broker the user configured but
        // connected by hand is left out, so upgrading never starts streaming data that did not
        // stream before; the user adds MQTT to the survey when they want it.
        val mqttIncluded = hasUserBroker(snapshot) &&
            userBoolean(snapshot, NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT, false)
        val communityIncluded = userBoolean(snapshot, NetworkSurveyConstants.PROPERTY_AUTO_START_COMMUNITY_UPLOAD_SURVEY_AT_BOOT, false)

        val autoStarts = autoStartTypes.isNotEmpty() || communityIncluded || mqttIncluded
        // The saved "NS Analytics" setup still lets a registered user who auto starts pick it
        val nsAnalyticsIncluded = nsAnalyticsRegistered && !autoStarts

        val types = autoStartTypes.toMutableSet()
        if (mqttIncluded) {
            types += STREAM_KEYS.filter { (_, keyAndDefault) ->
                userBoolean(snapshot, keyAndDefault.first, keyAndDefault.second)
            }.keys
        }
        if (nsAnalyticsIncluded) types += readNsAnalyticsTypes()
        if (types.isEmpty()) types += SurveyDefaults.RECORD_TYPES

        val destinations = buildSet {
            // NS Analytics is the survey's home when it joins; otherwise Log files is, as it was
            // for every survey of earlier versions that auto started.
            if (nsAnalyticsIncluded) add(Destination.NS_ANALYTICS) else add(Destination.LOG_FILES)
            if (mqttIncluded) add(Destination.MQTT)
            if (communityIncluded) add(Destination.COMMUNITY)
        }

        store.setCurrentSetup(SurveySetup.custom(types, destinations))

        store.userAutoStart = autoStarts

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

    /**
     * @return True if the user set up a broker of their own: a saved broker profile, or the single
     * broker of earlier versions when the broker profile migration has not run yet.
     */
    private fun hasUserBroker(snapshot: RestrictionsSnapshot): Boolean {
        if (hasUserBrokerProfile()) return true
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
        /** The id of the "NS Analytics" setup an upgrade saves on a registered phone. */
        const val UPGRADE_NS_ANALYTICS_SETUP_ID = "upgrade_ns_analytics"

        /** The name of that setup when no translated name is passed in (tests). */
        const val DEFAULT_NS_ANALYTICS_SETUP_NAME = "NS Analytics"

        /** The id version 1 gave its built-in NS Analytics preset. */
        private const val LEGACY_NS_ANALYTICS_PRESET_ID = "builtin_ns_analytics_survey"

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

        /** The flag of the removed "Your switches moved here" upgrade notice, deleted by version 3. */
        private const val LEGACY_MIGRATION_NOTICE_KEY = "survey_setup_migration_notice"

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
