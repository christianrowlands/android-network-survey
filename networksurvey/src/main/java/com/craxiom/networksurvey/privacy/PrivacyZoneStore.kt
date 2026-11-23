package com.craxiom.networksurvey.privacy

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.UUID

/**
 * The user's saved privacy zones, as a JSON list in their own preferences file (the same approach
 * as the MQTT broker profiles and provider colors: a short, capped list needs no database or
 * migration). Each entry keeps its id and [PrivacyZoneSource], so an organization's zones can later
 * be merged in without changing the stored format.
 *
 * [zones] is the source of truth in memory and changes as soon as a write is made, so callers on any
 * thread (including the main thread) see the change at once; the file is written in the background.
 */
class PrivacyZoneStore(private val preferences: SharedPreferences) {
    private val gson = Gson()
    private val lock = Any()
    private val _zones = MutableStateFlow(read())

    /** Every saved zone, in the order it was added. Emits on every change. */
    val zones: StateFlow<List<PrivacyZone>> = _zones.asStateFlow()

    /** @return The zone with [id], or null. */
    fun zone(id: String): PrivacyZone? = _zones.value.firstOrNull { it.id == id }

    /** @return True if another zone can be added. */
    fun canAdd(): Boolean = _zones.value.size < PrivacyZone.MAX_ZONES

    /**
     * Adds [zone], or updates the saved zone with the same id. The name is trimmed and the radius
     * limited to the allowed range.
     *
     * @return The zone as saved, or null when the center is not a valid coordinate or a new zone
     * would go over [PrivacyZone.MAX_ZONES].
     */
    fun save(zone: PrivacyZone): PrivacyZone? = synchronized(lock) {
        if (zone.id.isBlank() || !PrivacyZone.isValidCenter(zone.latitude, zone.longitude)) return null
        val current = _zones.value
        val stored = zone.copy(name = zone.name.trim(), radiusMeters = PrivacyZone.clampRadius(zone.radiusMeters))
        val updated = when {
            current.any { it.id == stored.id } -> current.map { if (it.id == stored.id) stored else it }
            current.size >= PrivacyZone.MAX_ZONES -> {
                Timber.w("Cannot add a privacy zone: the maximum of %d is reached", PrivacyZone.MAX_ZONES)
                return null
            }

            else -> current + stored
        }
        write(updated)
        stored
    }

    /** Deletes the zone with [id]. @return True if it was saved. */
    fun remove(id: String): Boolean = synchronized(lock) {
        val current = _zones.value
        val remaining = current.filterNot { it.id == id }
        if (remaining.size == current.size) return false
        write(remaining)
        true
    }

    private fun write(zones: List<PrivacyZone>) {
        _zones.value = zones
        preferences.edit { putString(KEY_ZONES, gson.toJson(zones.map { ZoneJson.from(it) })) }
    }

    private fun read(): List<PrivacyZone> {
        val json = preferences.getString(KEY_ZONES, null) ?: return emptyList()
        return try {
            gson.fromJson<List<ZoneJson?>>(json, ZONE_LIST_TYPE).orEmpty()
                .mapNotNull { it?.toZone() }
                .distinctBy { it.id }
        } catch (e: JsonParseException) {
            Timber.e(e, "The saved privacy zones could not be read")
            emptyList()
        }
    }

    /**
     * The stored form of a zone. Every field is nullable because Gson does not apply Kotlin defaults,
     * so a zone written by an older or newer version is validated before use. An unknown source reads
     * as [PrivacyZoneSource.USER].
     */
    private data class ZoneJson(
        val id: String?,
        val name: String?,
        val lat: Double?,
        val lon: Double?,
        val radiusM: Int?,
        val source: String?,
    ) {
        fun toZone(): PrivacyZone? {
            if (id.isNullOrBlank() || lat == null || lon == null || !PrivacyZone.isValidCenter(lat, lon)) return null
            return PrivacyZone(
                id = id,
                name = name.orEmpty(),
                latitude = lat,
                longitude = lon,
                radiusMeters = PrivacyZone.clampRadius(radiusM ?: PrivacyZone.DEFAULT_RADIUS_METERS),
                source = PrivacyZoneSource.entries.firstOrNull { it.name == source } ?: PrivacyZoneSource.USER,
            )
        }

        companion object {
            fun from(zone: PrivacyZone) = ZoneJson(
                zone.id, zone.name, zone.latitude, zone.longitude, zone.radiusMeters, zone.source.name,
            )
        }
    }

    companion object {
        /** The preferences file, kept apart from the default preferences so it can be handled on its own. */
        const val PREFERENCES_FILE = "network_survey_privacy_zones"

        private const val KEY_ZONES = "zones_json"
        private val ZONE_LIST_TYPE = object : TypeToken<List<ZoneJson?>>() {}.type

        @Volatile
        private var instance: PrivacyZoneStore? = null

        /** @return A new, unique zone id. */
        @JvmStatic
        fun newId(): String = UUID.randomUUID().toString()

        /** @return The process-wide store, so every screen and the service share one [zones] flow. */
        @JvmStatic
        fun getInstance(context: Context): PrivacyZoneStore = instance ?: synchronized(this) {
            instance ?: PrivacyZoneStore(
                context.applicationContext.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
