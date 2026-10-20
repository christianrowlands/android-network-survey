package com.craxiom.networksurvey.ui.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager

/** The app's default shared preferences, where every setting lives. */
@Composable
fun rememberAppPreferences(): SharedPreferences {
    val context = LocalContext.current
    return remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
}

/**
 * [read] of the preferences, kept current while on screen: it is read again whenever any setting
 * changes, wherever the change came from (this page, another page, the managed configuration sync).
 */
@Composable
fun <T> SharedPreferences.observe(read: (SharedPreferences) -> T): State<T> {
    val state = remember(this) { mutableStateOf(read(this)) }
    DisposableEffect(this) {
        // Held here: the preferences keep listeners only weakly
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, _ -> state.value = read(preferences) }
        registerOnSharedPreferenceChangeListener(listener)
        state.value = read(this@observe)
        onDispose { unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
