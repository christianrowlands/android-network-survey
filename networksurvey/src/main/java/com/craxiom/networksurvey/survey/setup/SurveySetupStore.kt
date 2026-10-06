package com.craxiom.networksurvey.survey.setup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveyPresets
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
     * @return The working setup. A phone that has never stored one gets the "NS Analytics survey"
     * preset, which is the fresh install default.
     */
    fun getCurrentSetup(): SurveySetup {
        val json = preferences.getString(SurveyPreferenceKeys.CURRENT_SETUP, null)
            ?: return SurveyPresets.nsAnalyticsSurvey()
        return decode(json) ?: SurveyPresets.nsAnalyticsSurvey()
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

    /**
     * @return The saved setups in display order, with the built-in presets first. A phone that has
     * never stored any gets the default presets.
     */
    fun getSavedSetups(): List<SurveySetup> {
        val json = preferences.getString(SurveyPreferenceKeys.SAVED_SETUPS, null) ?: return SurveyPresets.all()
        return try {
            val type = object : TypeToken<List<StoredSetup>>() {}.type
            val stored: List<StoredSetup>? = gson.fromJson(json, type)
            stored?.mapNotNull { it.toSetup() } ?: SurveyPresets.all()
        } catch (e: JsonParseException) {
            Timber.e(e, "The saved survey setups could not be read; using the defaults")
            SurveyPresets.all()
        }
    }

    /** Replaces the whole list of saved setups. */
    fun setSavedSetups(setups: List<SurveySetup>) {
        preferences.edit {
            putString(SurveyPreferenceKeys.SAVED_SETUPS, gson.toJson(setups.map { StoredSetup.from(it) }))
        }
    }

    /**
     * Saves [setup] under [name] as a new user setup and makes it the working setup.
     *
     * @return The saved setup with its generated id.
     */
    fun saveAs(setup: SurveySetup, name: String): SurveySetup {
        val saved = setup.renamed(UUID.randomUUID().toString(), name)
        setSavedSetups(getSavedSetups() + saved)
        setCurrentSetup(saved)
        return saved
    }

    /** Deletes the user setup with [id]. Built-in presets cannot be deleted. */
    fun delete(id: String) {
        setSavedSetups(getSavedSetups().filterNot { it.id == id && !it.builtIn })
    }

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
        @SerializedName("builtIn") val builtIn: Boolean?,
    ) {
        fun toSetup(): SurveySetup? {
            val setupId = id ?: return null
            return SurveySetup(
                id = setupId,
                name = name.orEmpty(),
                recordTypes = recordTypes.orEmpty().mapNotNull { RecordType.fromWireName(it) }.toSet(),
                destinations = destinations.orEmpty().mapNotNull { Destination.fromWireName(it) }.toSet(),
                builtIn = builtIn ?: false,
            )
        }

        companion object {
            fun from(setup: SurveySetup) = StoredSetup(
                id = setup.id,
                name = setup.name,
                recordTypes = RecordType.entries.filter { it in setup.recordTypes }.map { it.wireName },
                destinations = Destination.entries.filter { it in setup.destinations }.map { it.wireName },
                builtIn = setup.builtIn,
            )
        }
    }

    companion object {
        /** The current stored format version, written by the migration. */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
