package com.craxiom.networksurvey.ui.theme

import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The Theme setting: stored values map to the right AppCompat night mode, anything unexpected
 * follows the system, and the XML preference values stay in step with [ThemeMode].
 */
@RunWith(RobolectricTestRunner::class)
class ThemeModeTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun resetNightMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    @Test
    fun preferenceValuesMapToNightModes() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreferenceValue(NetworkSurveyConstants.APP_THEME_SYSTEM))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPreferenceValue(NetworkSurveyConstants.APP_THEME_LIGHT))
        assertEquals(ThemeMode.DARK, ThemeMode.fromPreferenceValue(NetworkSurveyConstants.APP_THEME_DARK))

        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.SYSTEM.nightMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeMode.LIGHT.nightMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeMode.DARK.nightMode)
    }

    @Test
    fun missingOrUnknownValuesFollowTheSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreferenceValue(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreferenceValue(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreferenceValue("purple"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreferenceValue("DARK"))
    }

    @Test
    fun defaultIsSystem() {
        assertEquals(NetworkSurveyConstants.APP_THEME_SYSTEM, NetworkSurveyConstants.DEFAULT_APP_THEME)

        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        ThemeMode.applyFromPreferences(context)

        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun applyFromPreferencesUsesTheStoredValue() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(NetworkSurveyConstants.PROPERTY_APP_THEME, NetworkSurveyConstants.APP_THEME_DARK)
            .commit()

        ThemeMode.applyFromPreferences(context)

        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun xmlPreferenceValuesMatchThemeModes() {
        val values = context.resources.getStringArray(R.array.app_theme_values).toList()
        val entries = context.resources.getStringArray(R.array.app_theme_entries)

        assertEquals(ThemeMode.entries.map { it.preferenceValue }, values)
        assertEquals(values.size, entries.size)
    }
}
