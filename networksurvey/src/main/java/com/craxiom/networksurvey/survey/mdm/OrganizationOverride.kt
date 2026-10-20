package com.craxiom.networksurvey.survey.mdm

import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.constants.NetworkSurveyConstants

/**
 * Writes the "Override organization settings" switch (key `mqtt_mdm_override`, unchanged since
 * before v2). The survey service reacts to the change: it re-reads the policy and, when turned on,
 * copies the organization's broker so a running stream keeps going.
 */
object OrganizationOverride {

    /** Turns the override on ("Use my own settings") or off (Restore). Call off the main thread. */
    @JvmStatic
    fun set(preferences: SharedPreferences, on: Boolean) {
        preferences.edit(commit = true) { putBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, on) }
    }
}
