package com.craxiom.networksurvey.ui.wifi

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.wifi.details.WifiDetailsActions
import com.craxiom.networksurvey.ui.wifi.details.WifiDetailsPageState
import com.craxiom.networksurvey.ui.wifi.details.WifiDetailsScreen
import com.craxiom.networksurvey.ui.wifi.model.WifiScanState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Wi-Fi details page: its cards, the notice when the phone cannot scan, and its actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class WifiDetailsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()
    private val network = WifiNetwork("02:00:A4:11:F0:21", -52f, "Acme-Corp", 5180, 36, null, "WPA2", null, "[WPA2-PSK-CCMP][ESS]", null)
    private val ready = WifiDetailsPageState(currentRssi = -52, scanRateSeconds = 15, scanState = WifiScanState.READY, excluded = false, watched = false)

    @Test
    fun whileScanning_thereIsNoNotice() {
        show(ready)

        compose.onNodeWithText(string(R.string.wifi_details_title)).assertIsDisplayed()
        compose.onNodeWithText("Acme-Corp", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("so no new scans arrive", substring = true).assertCountEquals(0)
    }

    @Test
    fun locationOff_saysNoNewScansArrive_andOffersToTurnItOn() {
        show(ready.copy(scanState = WifiScanState.LOCATION_OFF))

        compose.onNodeWithText("Location is off, so no new scans arrive.").assertIsDisplayed()
        compose.onNodeWithText("Turn on").performClick()

        assertEquals(listOf("location"), actions.calls)
    }

    @Test
    fun wifiOff_offersToTurnItOn() {
        show(ready.copy(scanState = WifiScanState.WIFI_OFF))

        compose.onNodeWithText("Wi-Fi and Wi-Fi scanning are off, so no new scans arrive.").assertIsDisplayed()
        compose.onNodeWithText("Turn on").performClick()

        assertEquals(listOf("wifi"), actions.calls)
    }

    @Test
    fun aPermissionDeniedForGood_opensTheAppSettings() {
        show(ready.copy(scanState = WifiScanState.PERMISSION_NEEDED), PermissionAccess.BLOCKED)

        compose.onNodeWithText("Allow precise location to get new scans.").assertIsDisplayed()
        compose.onNodeWithText("Open Settings").performClick()

        assertEquals(listOf("settings"), actions.calls)
    }

    @Test
    fun theSurveyCard_excludesAndWatches() {
        show(ready)

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Watch"))
        compose.onNodeWithText("Exclude").performClick()
        compose.onNodeWithText("Watch").performClick()

        assertEquals(listOf("exclude", "watchlist"), actions.calls)
    }

    @Test
    fun back_leaves() {
        show(ready)

        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("back"), actions.calls)
    }

    private fun show(page: WifiDetailsPageState, access: PermissionAccess = PermissionAccess.AVAILABLE) {
        compose.showPage { WifiDetailsScreen(network, page, access, chart = null, actions = actions) }
    }

    private class RecordingActions : WifiDetailsActions {
        val calls = mutableListOf<String>()
        override fun onBack() { calls += "back" }
        override fun onToggleExcluded() { calls += "exclude" }
        override fun onWatchlistClick() { calls += "watchlist" }
        override fun onOpenSettings() { calls += "scan settings" }
        override fun onAllow() { calls += "allow" }
        override fun onOpenAppSettings() { calls += "settings" }
        override fun onTurnOnLocation() { calls += "location" }
        override fun onTurnOnWifi() { calls += "wifi" }
    }
}
