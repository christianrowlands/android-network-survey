package com.craxiom.networksurvey.ui.privacy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.ui.plural
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Privacy zones page on the stateless [PrivacyZonesContent]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class PrivacyZonesContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : PrivacyZonesActions {
        override fun onBack() { calls += "back" }
        override fun onAdd() { calls += "add" }
        override fun onEdit(zoneId: String) { calls += "edit $zoneId" }
        override fun onDelete(zoneId: String) { calls += "delete $zoneId" }
        override fun onOpenSsidExclusions() { calls += "ssids" }
    }

    private val home = PrivacyZone("a", "Home", 38.9, -77.0, 250)
    private val office = PrivacyZone("b", "Office", 39.0, -77.1, 1000)

    private fun show(state: PrivacyZonesUiState) = compose.showPage { PrivacyZonesContent(state, actions) }

    @Test
    fun noZones_showsTheEmptyState_andAdds() {
        show(PrivacyZonesUiState())

        compose.onNodeWithText(string(R.string.privacy_zones_empty_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.privacy_zones_add)).performScrollTo().performClick()

        assertEquals(listOf("add"), calls)
    }

    @Test
    fun theInfoCard_namesTheExceptions_andLeadsToTheSsidList() {
        show(PrivacyZonesUiState())

        compose.onNodeWithText("watchlist alerts still go off", substring = true).assertIsDisplayed()
        compose.onNodeWithText("device status is still saved and sent, without a location", substring = true).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.ssid_exclusion_list_title)).performScrollTo().performClick()

        assertEquals(listOf("ssids"), calls)
    }

    @Test
    fun theZones_listWithTheirRadius_andATapEdits() {
        show(PrivacyZonesUiState(zones = listOf(home, office)))

        compose.onNodeWithText(plural(R.plurals.privacy_zones_hero_count, 2, 2)).assertIsDisplayed()
        compose.onNodeWithText("Zones · 2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.privacy_zone_row_radius, "1.0km")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Office").performScrollTo().performClick()

        assertEquals(listOf("edit b"), calls)
    }

    @Test
    fun theZoneTheSurveyIsPausedIn_isMarkedInTheCardAndTheRow() {
        show(PrivacyZonesUiState(zones = listOf(home, office), insideZoneId = "a"))

        compose.onNodeWithText(string(R.string.privacy_zones_hero_inside, "Home")).assertIsDisplayed()
        compose.onNodeWithText("250m radius · inside now").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun deleting_asksFirst() {
        show(PrivacyZonesUiState(zones = listOf(home)))

        compose.onNodeWithContentDescription(string(R.string.remove_format, "Home")).performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.privacy_zone_delete_title, "Home")).assertIsDisplayed()
        assertEquals(emptyList<String>(), calls)

        compose.onNodeWithText(string(R.string.privacy_zone_delete_confirm)).performClick()
        assertEquals(listOf("delete a"), calls)
    }

    @Test
    fun turnedOffByTheOrganization_saysSo_andTheListIsReadOnly() {
        show(PrivacyZonesUiState(zones = listOf(home), allowed = false))

        compose.onNodeWithText(string(R.string.privacy_zones_hero_off)).assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.privacy_zones_add)).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.remove_format, "Home")).assertDoesNotExist()
        compose.onNodeWithText("Home").performScrollTo().performClick()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun howZonesWork_namesTheRules() {
        show(PrivacyZonesUiState(zones = listOf(home)))

        compose.onNodeWithText(string(R.string.privacy_zones_how_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("accurate to 500m or better", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("recording stays paused until it gets one", substring = true).assertIsDisplayed()
        compose.onNodeWithText("a little wider than the circle", substring = true).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.privacy_zones_info_queues)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aFullList_saysSo_andOffersNoAdd() {
        val zones = (0 until PrivacyZone.MAX_ZONES).map { PrivacyZone("z$it", "Zone $it", 38.9, -77.0) }
        show(PrivacyZonesUiState(zones = zones))

        compose.onNodeWithText(string(R.string.privacy_zones_hero_full)).assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.privacy_zones_add)).assertDoesNotExist()
    }
}
