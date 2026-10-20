package com.craxiom.networksurvey.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants

/**
 * The user's Theme setting (Settings, Display Settings, Theme).
 *
 * AppCompat's default night mode is the single switch for light and dark: XML resources resolve
 * `values-night` from it, and Compose's `isSystemInDarkTheme()` reads the same configuration, so
 * View screens and Compose screens always agree.
 */
enum class ThemeMode(val preferenceValue: String, val nightMode: Int) {
    SYSTEM(NetworkSurveyConstants.APP_THEME_SYSTEM, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(NetworkSurveyConstants.APP_THEME_LIGHT, AppCompatDelegate.MODE_NIGHT_NO),
    DARK(NetworkSurveyConstants.APP_THEME_DARK, AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        /** The mode for a stored preference value; missing or unknown values follow the system. */
        @JvmStatic
        fun fromPreferenceValue(value: String?): ThemeMode =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM

        /** Reads the Theme setting and applies it app-wide. Recreates started activities if needed. */
        @JvmStatic
        fun applyFromPreferences(context: Context) {
            val value = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(NetworkSurveyConstants.PROPERTY_APP_THEME, NetworkSurveyConstants.DEFAULT_APP_THEME)
            AppCompatDelegate.setDefaultNightMode(fromPreferenceValue(value).nightMode)
        }
    }
}
