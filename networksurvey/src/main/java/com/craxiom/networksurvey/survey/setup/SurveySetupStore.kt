package com.craxiom.networksurvey.survey.setup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.survey.engine.LastSession
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveyDefaults
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.util.UUID

/**
 * Stores the user's survey setups in the default shared preferences.
 *
 * Setups are stored as JSON using the stable wire names of [RecordType] and [Destination], so an
 * enum constant can be renamed in code without breaking stored data. Unknown wire names (for
 * example from a newer app version) are dropped when reading.
 */
class SurveySetupStore(private val preferences: SharedPreferences) {

    constructor(context: Context) : this(PreferenceManager.getDefaultSharedPreferences(context))

    private val gson = Gson()

    /**
     * @return The working setup. A phone that has never stored one gets the fresh install default
     * (Cellular, Phone state and Wi-Fi sent to NS Analytics).
     */
    fun getCurrentSetup(): SurveySetup {
        val json = preferences.getString(SurveyPreferenceKeys.CURRENT_SETUP, null)
            ?: return SurveyDefaults.freshInstallSetup()
        return decode(json) ?: SurveyDefaults.freshInstallSetup()
    }

    /** Replaces the working setup. */
    fun setCurrentSetup(setup: SurveySetup) {
        preferences.edit {
            putString(SurveyPreferenceKeys.CURRENT_SETUP, gson.toJson(StoredSetup.from(setup)))
            if (setup.id == SurveySetup.CUSTOM_ID) {
                remove(SurveyPreferenceKeys.SELECTED_SETUP_ID)
            } else {
                putString(SurveyPreferenceKeys.SELECTED_SETUP_ID, setup.id)
            }
        }
    }

    /** @return The id of the saved setup the working setup was picked from, or null if it is custom. */
    fun getSelectedSetupId(): String? = preferences.getString(SurveyPreferenceKeys.SELECTED_SETUP_ID, null)

    /** @return The setups the user saved, in the order they were saved. Empty until the first save. */
    fun getSavedSetups(): List<SurveySetup> = readStoredSetups().mapNotNull { it.toSetup() }

    /** @return The saved setups in display order: starred first, then the rest, each in saved order. */
    fun orderedSavedSetups(): List<SurveySetup> = getSavedSetups().sortedByDescending { it.starred }

    /** Replaces the whole list of saved setups. */
    fun setSavedSetups(setups: List<SurveySetup>) {
        preferences.edit {
            putString(SurveyPreferenceKeys.SAVED_SETUPS, gson.toJson(setups.map { StoredSetup.from(it) }))
        }
    }

    /** @return The saved setup named [name] (trimmed, ignoring case), or null. */
    fun findByName(name: String): SurveySetup? {
        val key = nameKey(name)
        return getSavedSetups().firstOrNull { nameKey(it.name) == key }
    }

    /**
     * Saves [setup]'s record types and destinations under [name] and makes the saved setup the
     * working setup. A saved setup that already has this name is replaced in place, keeping its id
     * and its star; otherwise a new one is added at the end.
     *
     * @return The saved setup.
     */
    fun save(setup: SurveySetup, name: String): SurveySetup = synchronized(SAVED_SETUPS_LOCK) {
        val trimmed = name.trim()
        val setups = getSavedSetups()
        val existing = findByName(trimmed)
        val saved = SurveySetup(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = trimmed,
            recordTypes = setup.recordTypes,
            destinations = setup.destinations,
            starred = existing?.starred ?: false,
        )
        setSavedSetups(if (existing == null) setups + saved else setups.map { if (it.id == existing.id) saved else it })
        setCurrentSetup(saved)
        saved
    }

    /** Renames the saved setup with [id]. A blank name is ignored. */
    fun rename(id: String, name: String) {
        if (name.isBlank()) return
        update(id) { it.copy(name = name.trim()) }
    }

    /** Stars or unstars the saved setup with [id]. */
    fun setStarred(id: String, starred: Boolean) = update(id) { it.copy(starred = starred) }

    /** Deletes the saved setup with [id]. The working setup is left as it is. */
    fun delete(id: String) = synchronized(SAVED_SETUPS_LOCK) {
        setSavedSetups(getSavedSetups().filterNot { it.id == id })
    }

    /**
     * Removes the built-in presets that earlier development builds stored with the saved setups.
     * Used by the migration only.
     *
     * @return The removed presets.
     */
    internal fun removeLegacyBuiltIns(): List<SurveySetup> = synchronized(SAVED_SETUPS_LOCK) {
        val (builtIns, kept) = readStoredSetups().partition { it.builtIn == true }
        if (builtIns.isNotEmpty()) setSavedSetups(kept.mapNotNull { it.toSetup() })
        builtIns.mapNotNull { it.toSetup() }
    }

    /** Each change reads, edits and writes the whole list, so changes are made one at a time. */
    private fun update(id: String, change: (SurveySetup) -> SurveySetup) = synchronized(SAVED_SETUPS_LOCK) {
        setSavedSetups(getSavedSetups().map { if (it.id == id) change(it) else it })
    }

    private fun readStoredSetups(): List<StoredSetup> {
        val json = preferences.getString(SurveyPreferenceKeys.SAVED_SETUPS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<StoredSetup>>() {}.type
            gson.fromJson<List<StoredSetup>>(json, type).orEmpty()
        } catch (e: JsonParseException) {
            Timber.e(e, "The saved survey setups could not be read")
            emptyList()
        }
    }

    /** True until first run is finished or skipped on a fresh install. Set only by the migration. */
    var onboardingPending: Boolean
        get() = preferences.getBoolean(SurveyPreferenceKeys.ONBOARDING_PENDING, false)
        set(value) = preferences.edit { putBoolean(SurveyPreferenceKeys.ONBOARDING_PENDING, value) }

    /** True until the one-time welcome to this version is seen on an upgraded phone. Set only by the migration. */
    var upgradeWelcomePending: Boolean
        get() = preferences.getBoolean(SurveyPreferenceKeys.UPGRADE_WELCOME_PENDING, false)
        set(value) = preferences.edit { putBoolean(SurveyPreferenceKeys.UPGRADE_WELCOME_PENDING, value) }

    /** The user's own "start the survey automatically" choice. */
    var userAutoStart: Boolean
        get() = preferences.getBoolean(SurveyPreferenceKeys.AUTO_START_SURVEY, false)
        set(value) = preferences.edit { putBoolean(SurveyPreferenceKeys.AUTO_START_SURVEY, value) }

    /** Set when the user stopped a survey the organization had started. */
    var stoppedByUser: Boolean
        get() = preferences.getBoolean(SurveyPreferenceKeys.STOPPED_BY_USER, false)
        set(value) = preferences.edit { putBoolean(SurveyPreferenceKeys.STOPPED_BY_USER, value) }

    /** Destinations the user hid from the Survey screen list. */
    var hiddenDestinations: Set<Destination>
        get() = preferences.getStringSet(SurveyPreferenceKeys.HIDDEN_DESTINATIONS, emptySet())
            .orEmpty().mapNotNull { Destination.fromWireName(it) }.toSet()
        set(value) = preferences.edit {
            putStringSet(SurveyPreferenceKeys.HIDDEN_DESTINATIONS, value.map { it.wireName }.toSet())
        }

    /** The most recent survey session that ended, or null if there has never been one. */
    var lastSession: LastSession?
        get() {
            val endedAt = preferences.getLong(SurveyPreferenceKeys.LAST_SESSION_ENDED_AT_MS, 0L)
            if (endedAt <= 0L) return null
            return LastSession(
                preferences.getLong(SurveyPreferenceKeys.LAST_SESSION_DURATION_MS, 0L),
                preferences.getLong(SurveyPreferenceKeys.LAST_SESSION_RECORD_COUNT, 0L),
                endedAt,
            )
        }
        set(value) = preferences.edit {
            if (value == null) {
                remove(SurveyPreferenceKeys.LAST_SESSION_DURATION_MS)
                remove(SurveyPreferenceKeys.LAST_SESSION_RECORD_COUNT)
                remove(SurveyPreferenceKeys.LAST_SESSION_ENDED_AT_MS)
            } else {
                putLong(SurveyPreferenceKeys.LAST_SESSION_DURATION_MS, value.durationMs)
                putLong(SurveyPreferenceKeys.LAST_SESSION_RECORD_COUNT, value.totalRecords)
                putLong(SurveyPreferenceKeys.LAST_SESSION_ENDED_AT_MS, value.endedAtMs)
            }
        }

    /** The stored setup format version, or 0 if the setup migration has never run. */
    var schemaVersion: Int
        get() = preferences.getInt(SurveyPreferenceKeys.SCHEMA_VERSION, 0)
        set(value) = preferences.edit { putInt(SurveyPreferenceKeys.SCHEMA_VERSION, value) }

    private fun decode(json: String): SurveySetup? = try {
        gson.fromJson(json, StoredSetup::class.java)?.toSetup()
    } catch (e: JsonParseException) {
        Timber.e(e, "The stored survey setup could not be read")
        null
    }

    /** The JSON shape of a stored setup. Field names are part of the stored format. */
    private data class StoredSetup(
        @SerializedName("id") val id: String?,
        @SerializedName("name") val name: String?,
        @SerializedName("recordTypes") val recordTypes: List<String>?,
        @SerializedName("destinations") val destinations: List<String>?,
        @SerializedName("starred") val starred: Boolean?,
        // Written by versions that shipped built-in presets; read only so the migration can drop them.
        @SerializedName("builtIn") val builtIn: Boolean? = null,
    ) {
        fun toSetup(): SurveySetup? {
            val setupId = id ?: return null
            return SurveySetup(
                id = setupId,
                name = name.orEmpty(),
                recordTypes = recordTypes.orEmpty().mapNotNull { RecordType.fromWireName(it) }.toSet(),
                destinations = destinations.orEmpty().mapNotNull { Destination.fromWireName(it) }.toSet(),
                starred = starred ?: false,
            )
        }

        companion object {
            fun from(setup: SurveySetup) = StoredSetup(
                id = setup.id,
                name = setup.name,
                recordTypes = RecordType.entries.filter { it in setup.recordTypes }.map { it.wireName },
                destinations = Destination.entries.filter { it in setup.destinations }.map { it.wireName },
                starred = setup.starred,
            )
        }
    }

    companion object {
        /** The current stored format version, written by the migration. */
        const val CURRENT_SCHEMA_VERSION = 3

        /** Guards the read, edit, write of the saved setups list across every store instance. */
        private val SAVED_SETUPS_LOCK = Any()

        /** The key two setup names are compared by: trimmed, ignoring case. */
        @JvmStatic
        fun nameKey(name: String): String = name.trim().lowercase()
    }
}
