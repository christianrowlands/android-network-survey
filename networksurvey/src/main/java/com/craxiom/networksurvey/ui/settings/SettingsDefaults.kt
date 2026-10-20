package com.craxiom.networksurvey.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys

/**
 * The defaults the old preference screen seeded into the shared preferences on first launch, now
 * written from code with the same stored types: switches as booleans, typed numbers and lists as
 * strings (the readers parse them). Like androidx's `setDefaultValues` it replaces, it runs once per
 * install, marked with the same flag, so an install that already had its defaults is never written
 * to (a write would reach the running survey service's preference listener).
 */
object SettingsDefaults {

    private val booleans = mapOf(
        NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY to false,
        SurveyMdmKeys.AUTO_START_SURVEY to false,
        NetworkSurveyConstants.PROPERTY_AUTO_INCLUDE_PHONE_STATE_WITH_CELLULAR to true,
        NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED to NetworkSurveyConstants.DEFAULT_WATCHLIST_ENABLED,
        NetworkSurveyConstants.PROPERTY_MQTT_WATCHLIST_STREAM_ENABLED to NetworkSurveyConstants.DEFAULT_MQTT_WATCHLIST_STREAM_SETTING,
        NetworkSurveyConstants.PROPERTY_INCLUDE_NEIGHBOR_CELLS to NetworkSurveyConstants.DEFAULT_INCLUDE_NEIGHBOR_CELLS,
        NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL to true,
        NetworkSurveyConstants.PROPERTY_OUI_LOOKUP_ENABLED to NetworkSurveyConstants.DEFAULT_OUI_LOOKUP_ENABLED,
        NetworkSurveyConstants.PROPERTY_KEEP_SCREEN_ON_WHILE_RECORDING to NetworkSurveyConstants.DEFAULT_KEEP_SCREEN_ON_WHILE_RECORDING,
    )

    private val strings = mapOf(
        NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS to NetworkSurveyConstants.DEFAULT_CELLULAR_SCAN_INTERVAL_SECONDS.toString(),
        NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS to NetworkSurveyConstants.DEFAULT_WIFI_SCAN_INTERVAL_SECONDS.toString(),
        NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS to NetworkSurveyConstants.DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS.toString(),
        NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS to NetworkSurveyConstants.DEFAULT_GNSS_SCAN_INTERVAL_SECONDS.toString(),
        NetworkSurveyConstants.PROPERTY_DEVICE_STATUS_SCAN_INTERVAL_SECONDS to NetworkSurveyConstants.DEFAULT_DEVICE_STATUS_SCAN_INTERVAL_SECONDS.toString(),
        NetworkSurveyConstants.PROPERTY_LOCATION_PROVIDER to NetworkSurveyConstants.DEFAULT_LOCATION_PROVIDER.toString(),
        NetworkSurveyConstants.PROPERTY_MEASUREMENT_UNITS to NetworkSurveyConstants.DEFAULT_MEASUREMENT_UNITS,
        NetworkSurveyConstants.PROPERTY_APP_THEME to NetworkSurveyConstants.DEFAULT_APP_THEME,
        NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT to NetworkSurveyConstants.DEFAULT_STREAMING_QUEUE_LIMIT.toString(),
    )

    /** Writes the defaults on the first launch of an install; does nothing after that. */
    @JvmStatic
    fun applyOnce(context: Context) {
        if (wereApplied(context)) return
        applyMissing(PreferenceManager.getDefaultSharedPreferences(context))
        flag(context).edit { putBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, true) }
    }

    /**
     * @return True once the defaults were written, which happens on the first launch of the app.
     * Earlier versions wrote the same flag with androidx `PreferenceManager.setDefaultValues`, so
     * it also tells whether an older version was ever opened on this phone.
     */
    @JvmStatic
    fun wereApplied(context: Context): Boolean = flag(context).getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false)

    /** androidx keeps the flag in its own preferences file, not in the default preferences. */
    private fun flag(context: Context) = context.getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, Context.MODE_PRIVATE)

    /** Writes the default of every setting that has no stored value yet. */
    @JvmStatic
    fun applyMissing(preferences: SharedPreferences) {
        val missingBooleans = booleans.filterKeys { !preferences.contains(it) }
        val missingStrings = strings.filterKeys { !preferences.contains(it) }
        if (missingBooleans.isEmpty() && missingStrings.isEmpty()) return
        preferences.edit {
            missingBooleans.forEach { (key, value) -> putBoolean(key, value) }
            missingStrings.forEach { (key, value) -> putString(key, value) }
        }
    }
}
