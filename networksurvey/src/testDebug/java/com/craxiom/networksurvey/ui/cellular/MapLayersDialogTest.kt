package com.craxiom.networksurvey.ui.cellular

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.model.MapTileSource
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointColorMode
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointKind
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Map's Layers sheet: its titles come from resources and every row calls back at once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class MapLayersDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(showTowers: Boolean) = compose.showPage {
        MapLayersDialog(
            currentTileSource = MapTileSource.OPENFREEMAP,
            showBeaconDbCoverage = false,
            showTowersLayer = showTowers,
            showOnlyServingCell = false,
            showSurveyPoints = false,
            surveyPointsCount = 0,
            surveyedMode = SurveyedPointColorMode.SIGNAL,
            surveyedKind = SurveyedPointKind.CELLULAR,
            onChangeSurveyPoints = { calls += "change" },
            onAboutSurveyPoints = { calls += "about" },
            onSetShowSurveyPoints = { calls += "points:$it" },
            onSetTileSource = { calls += "tiles:${it.name}" },
            onSetShowBeaconDbCoverage = { calls += "beacondb:$it" },
            onSetShowTowersLayer = { calls += "towers:$it" },
            onSetShowOnlyServingCell = { calls += "serving:$it" },
            onDismiss = { calls += "dismiss" },
        )
    }

    @Test
    fun theSheet_showsItsSections_andEachRowCallsBack() {
        show(showTowers = true)

        compose.onNodeWithText(string(R.string.map_layers_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.map_layers_tiles)).assertIsDisplayed()
        compose.onNodeWithText(MapTileSource.OPENSTREETMAP.displayName).performClick()
        compose.onNodeWithText(string(R.string.map_layers_serving_only)).performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.map_layers_beacondb)).performScrollTo().performClick()

        assertEquals(listOf("tiles:OPENSTREETMAP", "serving:true", "beacondb:true"), calls)
    }

    @Test
    fun withTowersOff_theTowerModesAreHidden() {
        show(showTowers = false)

        compose.onNodeWithText(string(R.string.map_layers_towers)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.map_layers_all_towers)).assertDoesNotExist()
    }
}
