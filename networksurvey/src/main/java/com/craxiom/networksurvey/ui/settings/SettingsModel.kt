package com.craxiom.networksurvey.ui.settings

import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.ManagedState
import com.craxiom.networksurvey.survey.mdm.MdmOverlapKeys
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys

/** A setting's value, and whether the organization sets it (its row then explains instead of changing it). */
data class SettingValue<T>(val value: T, val locked: Boolean = false)

/** The five scan intervals, stored as strings of seconds (the service parses them). */
enum class ScanRate(
    val key: String,
    val defaultSeconds: Int,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    @param:StringRes val shortName: Int?,
) {
    CELLULAR(
        NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS, NetworkSurveyConstants.DEFAULT_CELLULAR_SCAN_INTERVAL_SECONDS,
        R.string.cellular_scan_interval_title, R.string.cellular_scan_interval_description, R.string.survey_type_cellular,
    ),
    WIFI(
        NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS, NetworkSurveyConstants.DEFAULT_WIFI_SCAN_INTERVAL_SECONDS,
        R.string.wifi_scan_interval_title, R.string.wifi_scan_interval_description, R.string.survey_type_wifi,
    ),
    BLUETOOTH(
        NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS, NetworkSurveyConstants.DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS,
        R.string.bluetooth_scan_interval_title, R.string.bluetooth_scan_interval_description, R.string.survey_type_bluetooth,
    ),
    GNSS(
        NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS, NetworkSurveyConstants.DEFAULT_GNSS_SCAN_INTERVAL_SECONDS,
        R.string.gnss_scan_interval_title, R.string.gnss_scan_interval_description, R.string.survey_type_gnss,
    ),

    /** Not a scan the user sees, so it is left out of the one-line summary. */
    DEVICE_STATUS(
        NetworkSurveyConstants.PROPERTY_DEVICE_STATUS_SCAN_INTERVAL_SECONDS, NetworkSurveyConstants.DEFAULT_DEVICE_STATUS_SCAN_INTERVAL_SECONDS,
        R.string.device_status_scan_interval_title, R.string.device_status_scan_interval_description, null,
    ),
}

/** Everything the Settings page shows, read from the shared preferences and the managed configuration. */
data class SettingsUiState(
    val managed: Boolean = false,
    val overrideOn: Boolean = false,
    val autoStart: SettingValue<Boolean> = SettingValue(false),
    val phoneStateWithCellular: SettingValue<Boolean> = SettingValue(true),
    val neighborCells: SettingValue<Boolean> = SettingValue(true),
    val scanRates: Map<ScanRate, SettingValue<Int>> = ScanRate.entries.associateWith { SettingValue(it.defaultSeconds) },
    val locationProvider: SettingValue<Int> = SettingValue(NetworkSurveyConstants.DEFAULT_LOCATION_PROVIDER),
    val batteryThreshold: SettingValue<Int> = SettingValue(NetworkSurveyConstants.DEFAULT_BATTERY_THRESHOLD_PERCENT),
    val batteryOptimizationOff: Boolean = false,
    val keepScreenOn: Boolean = NetworkSurveyConstants.DEFAULT_KEEP_SCREEN_ON_WHILE_RECORDING,
    val theme: String = NetworkSurveyConstants.DEFAULT_APP_THEME,
    val units: String = NetworkSurveyConstants.DEFAULT_MEASUREMENT_UNITS,
    val watchlistEnabled: Boolean = NetworkSurveyConstants.DEFAULT_WATCHLIST_ENABLED,
    val watchlistStream: SettingValue<Boolean> = SettingValue(NetworkSurveyConstants.DEFAULT_MQTT_WATCHLIST_STREAM_SETTING),
    val queueLimit: SettingValue<Int> = SettingValue(NetworkSurveyConstants.DEFAULT_STREAMING_QUEUE_LIMIT),
    val intentControl: SettingValue<Boolean> = SettingValue(true),
    val ouiLookup: SettingValue<Boolean> = SettingValue(NetworkSurveyConstants.DEFAULT_OUI_LOOKUP_ENABLED),
    val uploadsBlocked: Boolean = false,
    val nsAnalyticsBlocked: Boolean = false,
    val deviceSerial: String? = null,
    val appInstanceId: String? = null,
)

/**
 * Reads [SettingsUiState]. On a managed phone without the override, a setting the organization set
 * (one of [MdmOverlapKeys]) shows the organization's value, locked; everything else shows the
 * stored value. The upload and NS Analytics blocks apply even with the override, as before.
 */
object SettingsReader {

    /** What the phone, not the preferences, knows: the managed configuration and live statuses. */
    data class Environment(
        val restrictions: RestrictionsSnapshot,
        val autoStartPolicy: ManagedState,
        val uploadsAllowed: Boolean,
        val nsAnalyticsAllowed: Boolean,
        val batteryOptimizationOff: Boolean,
        val deviceSerial: String?,
        val appInstanceId: String?,
    )

    /** Every stored key the page shows; a change to any other leaves the page as it is. */
    val KEYS: Set<String> = setOf(
        NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY,
        SurveyMdmKeys.AUTO_START_SURVEY,
        NetworkSurveyConstants.PROPERTY_AUTO_INCLUDE_PHONE_STATE_WITH_CELLULAR,
        NetworkSurveyConstants.PROPERTY_INCLUDE_NEIGHBOR_CELLS,
        NetworkSurveyConstants.PROPERTY_LOCATION_PROVIDER,
        NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT,
        NetworkSurveyConstants.PROPERTY_KEEP_SCREEN_ON_WHILE_RECORDING,
        NetworkSurveyConstants.PROPERTY_APP_THEME,
        NetworkSurveyConstants.PROPERTY_MEASUREMENT_UNITS,
        NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED,
        NetworkSurveyConstants.PROPERTY_MQTT_WATCHLIST_STREAM_ENABLED,
        NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT,
        NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL,
        NetworkSurveyConstants.PROPERTY_OUI_LOOKUP_ENABLED,
    ) + ScanRate.entries.map { it.key }

    fun read(preferences: SharedPreferences, environment: Environment): SettingsUiState {
        val restrictions = environment.restrictions
        val managed = restrictions.isManaged
        val overrideOn = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false)
        val locks = Locks(restrictions, managed && !overrideOn)

        return SettingsUiState(
            managed = managed,
            overrideOn = overrideOn,
            autoStart = when {
                !locks.active || environment.autoStartPolicy == ManagedState.USER ->
                    SettingValue(preferences.getBoolean(SurveyMdmKeys.AUTO_START_SURVEY, false))

                else -> SettingValue(environment.autoStartPolicy == ManagedState.ON, locked = true)
            },
            phoneStateWithCellular = locks.boolean(preferences, NetworkSurveyConstants.PROPERTY_AUTO_INCLUDE_PHONE_STATE_WITH_CELLULAR, true),
            neighborCells = locks.boolean(preferences, NetworkSurveyConstants.PROPERTY_INCLUDE_NEIGHBOR_CELLS, NetworkSurveyConstants.DEFAULT_INCLUDE_NEIGHBOR_CELLS),
            scanRates = ScanRate.entries.associateWith { locks.int(preferences, it.key, it.defaultSeconds) },
            locationProvider = locks.int(preferences, NetworkSurveyConstants.PROPERTY_LOCATION_PROVIDER, NetworkSurveyConstants.DEFAULT_LOCATION_PROVIDER),
            batteryThreshold = locks.int(preferences, NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, NetworkSurveyConstants.DEFAULT_BATTERY_THRESHOLD_PERCENT),
            batteryOptimizationOff = environment.batteryOptimizationOff,
            keepScreenOn = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_KEEP_SCREEN_ON_WHILE_RECORDING, NetworkSurveyConstants.DEFAULT_KEEP_SCREEN_ON_WHILE_RECORDING),
            theme = preferences.stringOr(NetworkSurveyConstants.PROPERTY_APP_THEME, NetworkSurveyConstants.DEFAULT_APP_THEME),
            units = preferences.stringOr(NetworkSurveyConstants.PROPERTY_MEASUREMENT_UNITS, NetworkSurveyConstants.DEFAULT_MEASUREMENT_UNITS),
            watchlistEnabled = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED, NetworkSurveyConstants.DEFAULT_WATCHLIST_ENABLED),
            watchlistStream = locks.boolean(preferences, NetworkSurveyConstants.PROPERTY_MQTT_WATCHLIST_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_WATCHLIST_STREAM_SETTING),
            queueLimit = locks.int(preferences, NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT, NetworkSurveyConstants.DEFAULT_STREAMING_QUEUE_LIMIT),
            intentControl = locks.boolean(preferences, NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL, true),
            ouiLookup = locks.boolean(preferences, NetworkSurveyConstants.PROPERTY_OUI_LOOKUP_ENABLED, NetworkSurveyConstants.DEFAULT_OUI_LOOKUP_ENABLED),
            uploadsBlocked = managed && !environment.uploadsAllowed,
            nsAnalyticsBlocked = managed && !environment.nsAnalyticsAllowed,
            deviceSerial = environment.deviceSerial,
            appInstanceId = environment.appInstanceId,
        )
    }

    /** Which values the organization holds right now. */
    private class Locks(private val restrictions: RestrictionsSnapshot, val active: Boolean) {

        fun boolean(preferences: SharedPreferences, key: String, default: Boolean): SettingValue<Boolean> {
            val managedValue = if (active && key in MdmOverlapKeys.ALL) restrictions.getBoolean(key) else null
            return managedValue?.let { SettingValue(it, locked = true) } ?: SettingValue(preferences.getBoolean(key, default))
        }

        fun int(preferences: SharedPreferences, key: String, default: Int): SettingValue<Int> {
            val managedValue = if (active && key in MdmOverlapKeys.ALL) restrictions.getInt(key)?.takeIf { it != MISSING } else null
            return managedValue?.let { SettingValue(it, locked = true) } ?: SettingValue(preferences.intOr(key, default))
        }
    }

    private const val MISSING = -1
}

/** A string setting, or [default] when it is missing or stored as another type. */
fun SharedPreferences.stringOr(key: String, default: String): String = all[key] as? String ?: default

/**
 * A number setting stored either as a string (typed into a field) or as an int (the battery
 * threshold), or [default] when it is missing or does not parse.
 */
fun SharedPreferences.intOr(key: String, default: Int): Int = when (val value = all[key]) {
    is Int -> value
    is String -> value.trim().toIntOrNull() ?: default
    else -> default
}
