package com.craxiom.networksurvey.gpstest.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.signals.GnssPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The GNSS view's host: what is in the way, the Status/Sky toggle and the empty states. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class GnssContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()

    private fun show(
        scanState: GnssScanState = GnssScanState.READY,
        access: PermissionAccess = PermissionAccess.AVAILABLE,
        page: GnssPage = GnssPage.STATUS,
        heard: Boolean = true,
        filterHidesAll: Boolean = false,
        filterActive: Boolean = false,
    ) {
        compose.showPage {
            GnssContent(
                scanState = scanState,
                permissionAccess = access,
                page = page,
                heard = heard,
                filterHidesAll = filterHidesAll,
                actions = actions,
                filterActive = filterActive,
                statusPage = { Text("status page") },
                skyPage = { Text("sky page") },
            )
        }
    }

    @Test
    fun aMissingPermission_isAskedForOnlyFromItsButton_andHidesTheToggle() {
        show(scanState = GnssScanState.PERMISSION_NEEDED)

        compose.onNodeWithText("To see the satellites above you, allow precise location.").assertIsDisplayed()
        compose.onAllNodesWithText("Sky").assertCountEquals(0)
        assertEquals(emptyList<String>(), actions.calls)
        compose.onNodeWithText("Allow").performClick()

        assertEquals(listOf("allow"), actions.calls)
    }

    @Test
    fun aPermissionDeniedForGood_opensTheAppSettings() {
        show(scanState = GnssScanState.PERMISSION_NEEDED, access = PermissionAccess.BLOCKED)

        compose.onNodeWithText("Open Settings").performClick()

        assertEquals(listOf("settings"), actions.calls)
    }

    @Test
    fun locationOff_offersToTurnItOn() {
        show(scanState = GnssScanState.LOCATION_OFF)

        compose.onNodeWithText("Android only reports satellites while location is on.").assertIsDisplayed()
        compose.onNodeWithText("Turn on").performClick()

        assertEquals(listOf("location"), actions.calls)
    }

    @Test
    fun beforeTheFirstSatellite_theStatusPageSaysItIsLooking_withTheToggle() {
        show(heard = false)

        compose.onNodeWithText("Looking for satellites", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Status").assertIsSelected()
        compose.onAllNodesWithText("status page").assertCountEquals(0)
    }

    @Test
    fun beforeTheFirstSatellite_theSkyPageSaysItIsLookingToo() {
        show(heard = false, page = GnssPage.SKY)

        compose.onNodeWithText("Looking for satellites", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Sky").assertIsSelected()
        compose.onAllNodesWithText("sky page").assertCountEquals(0)
    }

    @Test
    fun aFilterHidingEverySatellite_offersShowAll() {
        show(filterHidesAll = true)

        compose.onNodeWithText("No satellites match the filter").assertIsDisplayed()
        compose.onNodeWithText("Show all").performClick()

        assertEquals(listOf("showAll"), actions.calls)
    }

    @Test
    fun theToggle_showsTheChosenPage_andAsksForTheOther() {
        show(page = GnssPage.SKY)

        compose.onNodeWithText("sky page").assertIsDisplayed()
        compose.onNodeWithText("Sky").assertIsSelected()
        compose.onNodeWithText("Status").assertIsNotSelected().performClick()

        assertEquals(listOf("page STATUS"), actions.calls)
    }

    @Test
    fun theStatusPage_showsWhenChosen() {
        show(page = GnssPage.STATUS)

        compose.onNodeWithText("status page").assertIsDisplayed()
    }

    @Test
    fun theStatusPage_hasSortAndFilterBesideTheToggle() {
        show(page = GnssPage.STATUS)

        compose.onNodeWithContentDescription("Sort by").performClick()
        compose.onNodeWithContentDescription("Filter signals").performClick()

        assertEquals(listOf("sort", "filter"), actions.calls)
    }

    @Test
    fun theSkyPage_hasOnlyTheFilter() {
        show(page = GnssPage.SKY)

        compose.onAllNodesWithContentDescription("Sort by").assertCountEquals(0)
        compose.onNodeWithContentDescription("Filter signals").assertIsDisplayed()
    }

    @Test
    fun anActiveFilter_saysSo() {
        show(filterActive = true)

        compose.onNodeWithContentDescription("Filter signals. Filter active.").assertIsDisplayed()
    }

    @Test
    fun aMissingPermission_hidesSortAndFilter() {
        show(scanState = GnssScanState.PERMISSION_NEEDED)

        compose.onAllNodesWithContentDescription("Filter signals").assertCountEquals(0)
    }

    private class RecordingActions : GnssActions {
        val calls = mutableListOf<String>()
        override fun onSelectPage(page: GnssPage) {
            calls += "page $page"
        }

        override fun onAllow() {
            calls += "allow"
        }

        override fun onOpenAppSettings() {
            calls += "settings"
        }

        override fun onTurnOnLocation() {
            calls += "location"
        }

        override fun onShowAll() {
            calls += "showAll"
        }

        override fun onOpenSort() {
            calls += "sort"
        }

        override fun onOpenFilter() {
            calls += "filter"
        }
    }
}
