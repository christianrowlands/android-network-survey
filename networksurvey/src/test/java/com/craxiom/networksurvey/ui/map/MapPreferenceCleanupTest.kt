package com.craxiom.networksurvey.ui.map

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.setup.SurveyPreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The retired survey map's settings go, and nothing else does: the survey setup, the survey points
 * layer and the managed configuration share the `survey_` prefix.
 */
@RunWith(RobolectricTestRunner::class)
class MapPreferenceCleanupTest {

    private val preferences: SharedPreferences = RuntimeEnvironment.getApplication()
        .getSharedPreferences("map_preference_cleanup_test", Context.MODE_PRIVATE)

    /** The keys that must survive, with the values they hold. */
    private val kept = mapOf(
        NetworkSurveyConstants.PROPERTY_SHOW_SURVEY_POINTS to false,
        NetworkSurveyConstants.PROPERTY_SURVEY_POINTS_COLOR_MODE to "TECHNOLOGY",
        NetworkSurveyConstants.PROPERTY_SURVEY_POINTS_KIND to "WIFI",
        SurveyPreferenceKeys.CURRENT_SETUP to "{}",
        SurveyPreferenceKeys.SAVED_SETUPS to "[]",
        SurveyMdmKeys.SURVEY_CELLULAR to "on",
        NetworkSurveyConstants.PROPERTY_SELECTED_MAP_TILE_SOURCE to "OPENSTREETMAP",
        NetworkSurveyConstants.PROPERTY_SHOW_TOWERS_LAYER to false,
        NetworkSurveyConstants.PROPERTY_MAP_KEEP_SCREEN_ON to false,
        NetworkSurveyConstants.PROPERTY_MAP_SHOW_ONLY_SERVING_CELL to true,
    )

    @Before
    fun seed() {
        preferences.edit(commit = true) {
            clear()
            MapPreferenceCleanup.RETIRED_KEYS.forEach { putBoolean(it, true) }
            kept.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    else -> putString(key, value.toString())
                }
            }
        }
    }

    @Test
    fun exactlyTheEightSurveyMapKeys_areRemoved() {
        // Spelled out so a typo or an added key fails here, not on users' phones
        assertEquals(
            listOf(
                "survey_map_tile_source",
                "survey_show_beacondb_coverage",
                "survey_show_towers_layer",
                "survey_map_show_survey_points",
                "survey_map_survey_points_color_mode",
                "survey_map_survey_points_kind",
                "survey_map_show_only_serving_cell",
                "survey_map_keep_screen_on",
            ),
            MapPreferenceCleanup.RETIRED_KEYS,
        )

        MapPreferenceCleanup.removeRetiredKeys(preferences)

        MapPreferenceCleanup.RETIRED_KEYS.forEach { assertFalse(it, preferences.contains(it)) }
        assertEquals(kept, preferences.all)
    }

    @Test
    fun runningItAgain_changesNothing() {
        MapPreferenceCleanup.removeRetiredKeys(preferences)
        MapPreferenceCleanup.removeRetiredKeys(preferences)

        assertEquals(kept, preferences.all)
        assertTrue(preferences.contains(SurveyPreferenceKeys.CURRENT_SETUP))
    }
}
