package com.craxiom.networksurvey.ui.newtowers

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.ui.plural
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The New towers page on the stateless [NewTowersContent]: its empty states, list, clear and detail sheet. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class NewTowersContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : NewTowersActions {
        override fun onEnableAlerts() { calls += "enable" }
        override fun onOpenDetail(findId: Long) { calls += "open:$findId" }
        override fun onShowOnMap(findId: Long) { calls += "map:$findId" }
        override fun onClear() { calls += "clear" }
    }

    @Test
    fun alertsOff_saysNothingIsChecked_andTheButtonTurnsThemOn() {
        show(NewTowersUiState(loaded = true, alertsEnabled = false))

        compose.onNodeWithText(string(R.string.new_towers_none_found)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.new_towers_what_is_checked)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.new_towers_alerts_off)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.new_towers_turn_on_alerts)).performClick()
        assertEquals(listOf("enable"), calls)
    }

    @Test
    fun alertsOnAndNothingFound_saysWhatHappensNext_withNoListActions() {
        show(NewTowersUiState(loaded = true, alertsEnabled = true))

        compose.onNodeWithText(string(R.string.new_towers_empty)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.new_towers_alerts_off)).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.new_towers_clear)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.watchlist_history_view_map)).assertDoesNotExist()
    }

    @Test
    fun beforeTheFindsAreRead_noEmptyStateFlashes() {
        show(NewTowersUiState(loaded = false, alertsEnabled = true))

        compose.onNodeWithText(string(R.string.new_towers_empty)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.new_towers_alerts_off)).assertDoesNotExist()
    }

    @Test
    fun finds_showTheCount_theirSession_andOpenTheirDetail() {
        show(stateWith(find(1, operator = "Mint"), find(2, operator = "Spruce").apply { inDatabase = true }))

        compose.onNodeWithText(plural(R.plurals.new_towers_found_count, 2, 2)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.new_towers_empty)).assertDoesNotExist()
        compose.onNodeWithText("Spruce", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(string(R.string.new_towers_in_database), useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Mint", substring = true).performScrollTo().performClick()
        assertEquals(listOf("open:1"), calls)
    }

    @Test
    fun theRow_readsAsOneSentence() {
        show(stateWith(find(1, operator = "Mint").apply {
            band = "66"
            signalDbm = -95
        }))

        compose.onNodeWithContentDescription("LTE, Mint, Band 66", substring = true).assertExists()
        compose.onNodeWithContentDescription("-95 dBm, Found", substring = true).assertExists()
    }

    @Test
    fun clear_asksFirst() {
        show(stateWith(find(1)))

        compose.onNodeWithContentDescription(string(R.string.new_towers_clear)).performClick()
        compose.onNodeWithText(string(R.string.new_towers_clear_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.new_towers_clear)).performClick()
        assertEquals(listOf("clear"), calls)
    }

    @Test
    fun theDetailSheet_showsTheStatusAndIdentifiers_andShowOnMap() {
        val find = find(4, operator = "Mint").apply {
            latitude = 38.9
            longitude = -77.0
            pci = 301
        }
        show(stateWith(find).copy(detailFindId = 4))

        compose.onNodeWithText(string(R.string.new_towers_not_in_database)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.new_towers_identifiers)).assertExists()
        compose.onNodeWithText("301").assertExists()
        compose.onNodeWithText(string(R.string.see_on_map)).performScrollTo().performClick()
        assertEquals(listOf("map:4"), calls)
    }

    @Test
    fun theMap_withNoFindLocated_saysSoInsteadOfShowingAnEmptyMap() {
        show(stateWith(find(1), find(2)).copy(mode = NewTowersViewMode.MAP))

        compose.onNodeWithText(string(R.string.new_towers_map_no_locations)).assertIsDisplayed()
    }

    private fun show(state: NewTowersUiState) = compose.showPage { NewTowersContent(state, actions, nowMs = NOW) }

    private fun stateWith(vararg finds: NewTowerFindEntity): NewTowersUiState {
        val list = finds.toList()
        return NewTowersUiState(loaded = true, alertsEnabled = true, finds = list, sessions = groupBySession(list))
    }

    /** An MCC the PLMN lookup rejects, so the row falls back to the record's carrier name without a request. */
    private fun find(id: Long, operator: String? = null) = NewTowerFindEntity().apply {
        this.id = id
        mcc = "31"
        mnc = "26"
        area = 12345
        cellId = id
        radio = "LTE"
        firstSeenAt = NOW - 60_000 * id
        lastSeenAt = firstSeenAt
        sessionStartedAt = NOW - 3_600_000
        lastCheckedAt = firstSeenAt
        operatorName = operator
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
