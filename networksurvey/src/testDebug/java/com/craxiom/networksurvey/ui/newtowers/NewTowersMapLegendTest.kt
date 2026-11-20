package com.craxiom.networksurvey.ui.newtowers

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.plural
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The New towers map legend: honest about what a dot is, and the tower entry only when a tower is drawn. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class NewTowersMapLegendTest {

    @get:Rule
    val compose = createComposeRule()

    private val sighting get() = string(R.string.new_towers_map_legend_sighting)
    private val unknownTower get() = string(R.string.new_towers_map_legend_unknown_tower)

    @Test
    fun noTowerDrawn_saysWhereYouWere_andThatTheTowerIsNotKnown() {
        show(NewTowersMapLegend(showsUnknownTowerNote = true, showsTower = false, unplacedCount = 0))

        compose.onNodeWithText("$sighting $unknownTower").assertExists()
        compose.onNodeWithText(string(R.string.new_towers_tower_location)).assertDoesNotExist()
    }

    @Test
    fun aDrawnTower_addsTheTowerEntry() {
        show(NewTowersMapLegend(showsUnknownTowerNote = true, showsTower = true, unplacedCount = 0))

        compose.onNodeWithText("$sighting $unknownTower").assertExists()
        compose.onNodeWithText(string(R.string.new_towers_tower_location)).assertExists()
    }

    @Test
    fun everyTowerPlaced_dropsTheUnknownNote() {
        show(NewTowersMapLegend(showsUnknownTowerNote = false, showsTower = true, unplacedCount = 0))

        compose.onNodeWithText(sighting).assertExists()
        compose.onNodeWithText(unknownTower, substring = true).assertDoesNotExist()
    }

    @Test
    fun findsWithoutALocation_areCountedInANote() {
        show(NewTowersMapLegend(showsUnknownTowerNote = true, showsTower = false, unplacedCount = 2))

        compose.onNodeWithText(plural(R.plurals.new_towers_map_unplaced, 2, 2)).assertExists()
    }

    private fun show(legend: NewTowersMapLegend) =
        compose.showPage { NewTowersMapLegendStrip(legend = legend) }
}
