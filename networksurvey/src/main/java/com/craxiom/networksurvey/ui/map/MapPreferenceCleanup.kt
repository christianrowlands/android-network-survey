package com.craxiom.networksurvey.ui.map

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Removes the settings of the retired survey map. The Survey Monitor had its own map with its own
 * copy of the map settings; the one Map tab uses the tower map's settings, so these are dead.
 *
 * Exactly these keys are removed, never a prefix: the survey setup (`survey_setup_*`), the survey
 * points layer (`survey_points_*`) and the managed configuration (`survey_*`) share the prefix.
 * There is no "done" flag; once the keys are gone a launch finds nothing and writes nothing.
 */
object MapPreferenceCleanup {

    /** The survey map's settings, the only keys this removes. */
    val RETIRED_KEYS = listOf(
        "survey_map_tile_source",
        "survey_show_beacondb_coverage",
        "survey_show_towers_layer",
        "survey_map_show_survey_points",
        "survey_map_survey_points_color_mode",
        "survey_map_survey_points_kind",
        "survey_map_show_only_serving_cell",
        "survey_map_keep_screen_on",
    )

    /** Removes the retired keys that [preferences] still holds. */
    @JvmStatic
    fun removeRetiredKeys(preferences: SharedPreferences) {
        val present = RETIRED_KEYS.filter { preferences.contains(it) }
        if (present.isEmpty()) return
        preferences.edit { present.forEach { remove(it) } }
    }
}
