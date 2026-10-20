package com.craxiom.networksurvey.ui.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot

/**
 * Copies the organization's values for the settings it manages into the shared preferences when
 * Settings opens, as the old preference screen did, so the app keeps them if the organization stops
 * managing the phone. Nothing is written with the override on or on an unmanaged phone.
 *
 * Every value keeps the type its readers expect: switches as booleans, typed numbers and the
 * location source as strings, the battery threshold as an int (the old screen never wrote it, which
 * left the row editable on a managed phone).
 */
object ManagedSettingsSync {

    private val booleanKeys = listOf(
        NetworkSurveyConstants.PROPERTY_AUTO_INCLUDE_PHONE_STATE_WITH_CELLULAR,
        NetworkSurveyConstants.PROPERTY_INCLUDE_NEIGHBOR_CELLS,
        NetworkSurveyConstants.PROPERTY_MQTT_WATCHLIST_STREAM_ENABLED,
        NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL,
        NetworkSurveyConstants.PROPERTY_OUI_LOOKUP_ENABLED,
        NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED,
        NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_WIFI_ONLY,
    )

    private val stringNumberKeys = ScanRate.entries.map { it.key } + listOf(
        NetworkSurveyConstants.PROPERTY_LOCATION_PROVIDER,
        NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT,
    )

    private const val MISSING = -1

    @JvmStatic
    fun write(preferences: SharedPreferences, restrictions: RestrictionsSnapshot) {
        if (!restrictions.isManaged) return
        if (preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false)) return

        preferences.edit {
            booleanKeys.forEach { key -> restrictions.getBoolean(key)?.let { putBoolean(key, it) } }
            stringNumberKeys.forEach { key -> restrictions.int(key)?.let { putString(key, it.toString()) } }
            restrictions.int(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT)?.let {
                putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, it)
            }
        }
    }

    private fun RestrictionsSnapshot.int(key: String): Int? = getInt(key)?.takeIf { it != MISSING }
}
