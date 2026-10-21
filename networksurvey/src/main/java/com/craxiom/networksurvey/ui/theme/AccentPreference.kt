package com.craxiom.networksurvey.ui.theme

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.constants.NetworkSurveyConstants

/**
 * @return The accent the user picked in Settings, updated the moment the preference changes, so
 * [NsTheme] recolors the app without recreating the activity (unlike the Theme setting).
 */
@Composable
fun rememberAccent(preferences: SharedPreferences = Application.getPrefs()): NsAccent {
    var accent by remember(preferences) { mutableStateOf(readAccent(preferences)) }
    DisposableEffect(preferences) {
        // Held here for the effect's lifetime: SharedPreferences keeps its listeners weakly.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == NetworkSurveyConstants.PROPERTY_APP_ACCENT) accent = readAccent(prefs)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return accent
}

private fun readAccent(preferences: SharedPreferences): NsAccent =
    NsAccent.fromPrefValue(preferences.getString(NetworkSurveyConstants.PROPERTY_APP_ACCENT, null))
