package com.craxiom.networksurvey.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.util.PlmnColorMapper
import org.json.JSONObject
import timber.log.Timber

/**
 * Manages user-assigned color overrides for cellular providers identified by MCC/MNC.
 *
 * Overrides are stored as a JSON string in a dedicated SharedPreferences file. Keys are
 * "mcc-mnc" strings and values are palette indices (0–15) referencing [PlmnColorMapper.PALETTE].
 * A key is matched by [PlmnColorMapper.overrideKey], so "440-10" and "440-010" are one provider;
 * the stored key keeps the spelling it was last set with.
 * On every mutation the in-memory cache is updated and [PlmnColorMapper.refreshOverrides] is
 * called so the map renders the new color immediately.
 */
class PlmnColorOverrideManager(context: Context) {

    companion object {
        private const val PREF_NAME = "network_survey_plmn_color_overrides"
        private const val KEY_OVERRIDES = "overrides_json"
        const val MAX_OVERRIDES = 50
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private var cache: Map<String, Int>

    init {
        cache = loadFromPrefs()
        PlmnColorMapper.refreshOverrides(cache)
    }

    /**
     * Returns all current overrides as a map of "mcc-mnc" to palette index.
     */
    fun getOverrides(): Map<String, Int> = cache

    /**
     * Returns the override palette index for the given provider, or null if none is set.
     */
    fun getOverride(mcc: String, mnc: String): Int? {
        return storedKeyFor(mcc, mnc)?.let { cache[it] }
    }

    /**
     * Sets or updates the color override for a provider. Returns true if the override was saved.
     */
    fun setOverride(mcc: String, mnc: String, paletteIndex: Int): Boolean {
        if (paletteIndex !in 0 until PlmnColorMapper.PALETTE_SIZE) return false

        val existingKey = storedKeyFor(mcc, mnc)
        val current = cache.toMutableMap()

        if (existingKey == null && current.size >= MAX_OVERRIDES) {
            Timber.w("Cannot add color override - maximum of %d reached", MAX_OVERRIDES)
            return false
        }

        existingKey?.let { current.remove(it) }
        current["$mcc-$mnc"] = paletteIndex
        cache = current
        persist(current)
        PlmnColorMapper.refreshOverrides(cache)
        return true
    }

    /**
     * Removes the color override for a provider. Returns true if an override was removed.
     */
    fun removeOverride(mcc: String, mnc: String): Boolean {
        val key = storedKeyFor(mcc, mnc) ?: return false
        val current = cache.toMutableMap()
        val removed = current.remove(key) != null
        if (removed) {
            cache = current
            persist(current)
            PlmnColorMapper.refreshOverrides(cache)
        }
        return removed
    }

    /**
     * Removes all color overrides.
     */
    fun clearAll() {
        cache = emptyMap()
        persist(emptyMap())
        PlmnColorMapper.refreshOverrides(cache)
    }

    /**
     * Returns true if the override list is at maximum capacity.
     */
    fun isAtMaxCapacity(): Boolean = cache.size >= MAX_OVERRIDES

    /** The stored key that matches this provider at any MNC width, or null when none is set. */
    private fun storedKeyFor(mcc: String, mnc: String): String? {
        val key = PlmnColorMapper.overrideKey(mcc, mnc)
        return cache.keys.firstOrNull { PlmnColorMapper.overrideKey(it) == key }
    }

    /**
     * Reads the stored overrides. Older versions keyed the same provider at two widths (the
     * tower sheet saved "440-010" while survey points looked up "440-10"); only the first of
     * such duplicates is kept, and the cleaned map is saved back.
     */
    private fun loadFromPrefs(): Map<String, Int> {
        val json = sharedPreferences.getString(KEY_OVERRIDES, null) ?: return emptyMap()
        return try {
            val jsonObject = JSONObject(json)
            val map = mutableMapOf<String, Int>()
            for (key in jsonObject.keys()) {
                val index = jsonObject.getInt(key)
                if (index in 0 until PlmnColorMapper.PALETTE_SIZE) {
                    val matchKey = PlmnColorMapper.overrideKey(key)
                    if (map.keys.none { PlmnColorMapper.overrideKey(it) == matchKey }) {
                        map[key] = index
                    }
                } else {
                    Timber.w(
                        "Ignoring PLMN color override with invalid palette index: %s=%d",
                        key,
                        index
                    )
                }
            }
            if (map.size < jsonObject.length()) persist(map)
            map
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse PLMN color overrides JSON")
            emptyMap()
        }
    }

    private fun persist(overrides: Map<String, Int>) {
        val jsonObject = JSONObject()
        for ((key, value) in overrides) {
            jsonObject.put(key, value)
        }
        sharedPreferences.edit {
            putString(KEY_OVERRIDES, jsonObject.toString())
        }
    }
}
