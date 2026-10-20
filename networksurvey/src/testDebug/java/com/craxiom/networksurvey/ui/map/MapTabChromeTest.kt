package com.craxiom.networksurvey.ui.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.TowerKey
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Map tab around a stand-in map: the serving-cell card, the banner and the buttons. */
@RunWith(RobolectricTestRunner::class)
// A small phone's width, so the card's lines are not flattered by a wide screen.
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
// Real text measurement, so the large-font check measures the actual font.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapTabChromeTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()
    private val sims = listOf(SimChoice(1, "SIM 1 · Mint"), SimChoice(2, "SIM 2 · T-Mobile"))
    private var mapPadding = PaddingValues(0.dp)

    private fun card(isNew: Boolean = false, sims: List<SimChoice> = this.sims): ServingCellCardState {
        val record = CellRecords.lte()
        val newTowers = if (isNew) setOf(TowerKey.from(record)!!) else emptySet()
        return ServingCellCardState(ServingCellSummary.from(ServingCellInfo(record, 1), 410.0, newTowers)!!, sims)
    }

    private fun show(state: MapChromeState, largeFont: Boolean = false) {
        compose.showPage(largeFont = largeFont) {
            MapTabChrome(state = state, actions = actions, surveyPoints = { Text(SURVEY_POINTS_SLOT) }) { padding ->
                mapPadding = padding
                Text(MAP_SLOT)
            }
        }
    }

    @Test
    fun theMap_hasNoTitleBar() {
        show(MapChromeState())

        // The map runs up under the status bar; a title bar would only cover it
        compose.onNode(isHeading() and hasText("Map")).assertDoesNotExist()
        compose.onNodeWithText(MAP_SLOT).assertIsDisplayed()
        compose.onNodeWithText(SURVEY_POINTS_SLOT).assertIsDisplayed()
    }

    @Test
    fun theActionRow_opensEachSheet() {
        show(MapChromeState())

        val buttons = listOf("About the map", "Map settings", "Filters", "Search for a tower", "Layers")
        // Left to right in that order, as on the tower map before the redesign
        val lefts = buttons.map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot.left }
        assertEquals(lefts.sorted(), lefts)
        buttons.forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed().performClick()
        }
        assertEquals(listOf("about", "settings", "filters", "search", "layers"), actions.calls)
    }

    @Test
    fun searchAndFilters_onlyShowWhenALayerCanUseThem() {
        show(MapChromeState(searchAvailable = false, filtersAvailable = false))

        compose.onNodeWithContentDescription("Search for a tower").assertDoesNotExist()
        compose.onNodeWithContentDescription("Filters").assertDoesNotExist()
        compose.onNodeWithContentDescription("Layers").assertIsDisplayed()
    }

    @Test
    fun theCollapsedCard_readsAsTwoLines() {
        show(MapChromeState(card = card()))

        compose.onNodeWithText("Mint · LTE Band 66 · -79 dBm", substring = true).assertIsDisplayed()
        compose.onNodeWithText("away · SIM 1 · Mint", substring = true).assertIsDisplayed()
        compose.onNodeWithText("eNB", substring = true).assertIsDisplayed()
        compose.onNodeWithText("RSRP").assertDoesNotExist()
        compose.onNodeWithContentDescription("New tower", useUnmergedTree = true).assertDoesNotExist()
        // The map is told how much of it the chrome covers
        assertTrue(mapPadding.calculateTopPadding() > 0.dp)
        assertTrue(mapPadding.calculateBottomPadding() > 0.dp)
    }

    @Test
    fun aNewTower_isTagged() {
        show(MapChromeState(card = card(isNew = true)))

        // TalkBack says what the tag means rather than spelling out "NEW"
        compose.onNodeWithContentDescription("New tower", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun expanding_showsTheDetailsAndTheSimChoice() {
        show(MapChromeState(card = card()))

        compose.onNodeWithText("Mint · LTE Band 66", substring = true).performClick()
        compose.onNodeWithText("RSRP").assertIsDisplayed()
        compose.onNodeWithText("EARFCN").assertIsDisplayed()

        compose.onNodeWithText("SIM 2 · T-Mobile").assertIsDisplayed().performClick()
        assertEquals(listOf("sim 2"), actions.calls)
        // Picking a SIM leaves the card open
        compose.onNodeWithText("RSRP").assertIsDisplayed()
    }

    @Test
    fun withOneSim_thereIsNoSimChoice() {
        show(MapChromeState(card = card(sims = listOf(SimChoice(1, "SIM 1 · Mint")))))

        compose.onNodeWithText("SIM 1 · Mint", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Mint · LTE Band 66", substring = true).performClick()
        compose.onNodeWithText("SIM 1 · Mint").assertDoesNotExist()
    }

    @Test
    fun noServingCell_meansNoCard() {
        show(MapChromeState(card = null))

        compose.onNodeWithTag(SERVING_CELL_CARD_TAG).assertDoesNotExist()
    }

    @Test
    fun locationOff_showsABannerThatTurnsItOn() {
        show(MapChromeState(locationOff = true))

        compose.onNodeWithText(string(R.string.location_services_disabled_message), substring = true).assertIsDisplayed()
        compose.onNodeWithText("Turn on").performClick()
        assertEquals(listOf("location"), actions.calls)
    }

    @Test
    fun whileASurveyPointSheetIsOpen_theControlsHide() {
        show(MapChromeState(card = card(), controlsVisible = false))

        compose.onNodeWithContentDescription("Layers").assertDoesNotExist()
        compose.onNodeWithText(SURVEY_POINTS_SLOT).assertDoesNotExist()
        compose.onNodeWithTag(SERVING_CELL_CARD_TAG).assertIsDisplayed()
        assertEquals(0.dp, mapPadding.calculateBottomPadding())
    }

    @Test
    fun theCard_fitsAtLargeFont() {
        show(MapChromeState(card = card(isNew = true)), largeFont = true)

        val layout = compose.textLayout("Mint · LTE Band 66 · -79 dBm", substring = true)
        assertFalse(layout.isLineEllipsized(layout.lineCount - 1))
    }


    /** Records which button was used. */
    private class RecordingActions : MapTabActions {
        val calls = mutableListOf<String>()
        override fun onSearch() { calls += "search" }
        override fun onFilters() { calls += "filters" }
        override fun onLayers() { calls += "layers" }
        override fun onSettings() { calls += "settings" }
        override fun onAbout() { calls += "about" }
        override fun onZoomIn() { calls += "zoom in" }
        override fun onZoomOut() { calls += "zoom out" }
        override fun onToggleFollow() { calls += "follow" }
        override fun onTurnOnLocation() { calls += "location" }
        override fun onSelectSim(subscriptionId: Int) { calls += "sim $subscriptionId" }
    }

    private companion object {
        const val MAP_SLOT = "map-slot"
        const val SURVEY_POINTS_SLOT = "survey-points-slot"
    }
}
