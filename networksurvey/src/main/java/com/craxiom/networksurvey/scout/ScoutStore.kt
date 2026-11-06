package com.craxiom.networksurvey.scout

import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutId
import com.craxiom.networksurvey.scout.model.WifiSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber

/**
 * Persists the paired Scouts and the Wi-Fi source in the app's default preferences. The Scouts are
 * a JSON list keyed by [ScoutId], so more than one can be stored later; today pairing a Scout
 * replaces the list (one Scout at a time).
 */
class ScoutStore(private val preferences: SharedPreferences) {
    private val gson = Gson()

    /** @return Every stored Scout, most recently paired first. */
    fun pairedScouts(): List<PairedScout> {
        val json = preferences.getString(ScoutConstants.PREFERENCE_PAIRED_SCOUTS, null) ?: return emptyList()
        return try {
            gson.fromJson<List<PairedScout>>(json, LIST_TYPE).orEmpty()
                .filter { it.id.startsWith(ScoutId.PREFIX) && it.address.isNotEmpty() }
                .sortedByDescending { it.pairedAtMs }
        } catch (e: RuntimeException) {
            // A JsonParseException, or a null field (Gson skips Kotlin's null checks) caught by the filter.
            Timber.e(e, "The stored Scouts did not parse; treating as none")
            emptyList()
        }
    }

    /** @return The Scout in use, null when none is paired. */
    fun pairedScout(): PairedScout? = pairedScouts().firstOrNull()

    /** Stores [scout] as the only paired Scout. */
    fun replacePairedScout(scout: PairedScout) {
        save(listOf(scout))
    }

    /** Updates the stored details of [scout] (same ID), keeping any others. */
    fun update(scout: PairedScout) {
        val others = pairedScouts().filterNot { it.id == scout.id }
        save(listOf(scout) + others)
    }

    /** Forgets the Scout with [id]. */
    fun remove(id: ScoutId) {
        save(pairedScouts().filterNot { it.id == id.value })
    }

    /** @return Where Wi-Fi comes from; the phone unless a Scout was chosen. */
    fun wifiSource(): WifiSource =
        WifiSource.fromPreference(preferences.getString(ScoutConstants.PREFERENCE_WIFI_SOURCE, null))

    /** Saves where Wi-Fi comes from. */
    fun setWifiSource(source: WifiSource) {
        preferences.edit { putString(ScoutConstants.PREFERENCE_WIFI_SOURCE, source.preferenceValue) }
    }

    private fun save(scouts: List<PairedScout>) {
        preferences.edit {
            if (scouts.isEmpty()) remove(ScoutConstants.PREFERENCE_PAIRED_SCOUTS)
            else putString(ScoutConstants.PREFERENCE_PAIRED_SCOUTS, gson.toJson(scouts))
        }
    }

    private companion object {
        val LIST_TYPE = object : TypeToken<List<PairedScout>>() {}.type
    }
}
