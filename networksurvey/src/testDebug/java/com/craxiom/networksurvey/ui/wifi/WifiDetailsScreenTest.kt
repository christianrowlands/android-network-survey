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
import com.craxiom.messaging.wifi.AkmSuite
import com.craxiom.messaging.wifi.CipherSuite
import com.craxiom.networksurvey.model.ScoutWifiDetails
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.model.WifiNetworkScout
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
    private val scoutNetwork = WifiNetwork(
        "E0:63:DA:41:7C:22", -48f, "Toast", 5180, 36, null, "WPA2/WPA3", null, "", null,
        WifiNetworkScout(
            details = ScoutWifiDetails("NS-A1B2", 7, 59, 7, -87, pairwiseCiphers = listOf(CipherSuite.CCMP.number), groupCipher = CipherSuite.CCMP.number),
            akmSuites = listOf(AkmSuite.SAE.number, AkmSuite.PSK.number),
            cipherSuites = listOf(CipherSuite.CCMP.number),
            wps = false,
            beaconIntervalTu = 100,
            snrDb = 39f,
        ),
    )
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
        show(ready.copy(scanState = WifiScanState.PERMISSION_NEEDED), access = PermissionAccess.BLOCKED)

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
    fun aPhoneRecord_showsAndroidsCapabilities_andNoScoutCards() {
        show(ready)

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("[WPA2-PSK-CCMP][ESS]"))
        compose.onAllNodesWithText("Security").assertCountEquals(0)
        compose.onAllNodesWithText("Heard by", substring = true).assertCountEquals(0)
    }

    @Test
    fun aScoutRecord_saysWhoHeardIt_andShowsSecurityLoadAndRadioExtras() {
        show(ready, scoutNetwork)

        compose.onNodeWithText("Heard by NS-A1B2").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Security"))
        compose.onNodeWithContentDescription("Personal, WPA2/WPA3").assertExists()
        compose.onNodeWithContentDescription("Key management, SAE, PSK").assertExists()
        compose.onNodeWithContentDescription("Pairwise cipher, CCMP-128").assertExists()
        compose.onNodeWithContentDescription("Group cipher, CCMP-128").assertExists()
        compose.onNodeWithContentDescription("WPS, Off").assertExists()
        compose.onAllNodesWithText("[WPA2-PSK-CCMP][ESS]").assertCountEquals(0)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Load"))
        compose.onNodeWithContentDescription("Stations, 7").assertExists()
        compose.onNodeWithContentDescription("Channel use, 23%").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("BEACON INTERVAL"))
        compose.onNodeWithText("102").assertExists()
        compose.onNodeWithText("39").assertExists()
        compose.onNodeWithText("Noise -87 dBm").assertExists()
        compose.onNodeWithText("4, 5, 6").assertExists()
    }

    @Test
    fun aScoutRecordWithoutBssLoadOrNoise_leavesThoseOut() {
        val bare = scoutNetwork.copy(
            scout = scoutNetwork.scout!!.copy(
                details = ScoutWifiDetails("NS-A1B2", null, null, 0, null),
                snrDb = null,
                beaconIntervalTu = null,
                wps = null,
                cipherSuites = listOf(3),
            )
        )
        show(ready, bare)

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Security"))
        // The record's merged list when the Scout did not tell pairwise and group apart
        compose.onNodeWithContentDescription("Ciphers, CCMP-128").assertExists()
        compose.onAllNodesWithText("Load").assertCountEquals(0)
        compose.onAllNodesWithText("BEACON INTERVAL").assertCountEquals(0)
        compose.onAllNodesWithText("SNR").assertCountEquals(0)
        compose.onAllNodesWithText("GENERATIONS").assertCountEquals(0)
        compose.onAllNodesWithText("WPS", substring = true).assertCountEquals(0)
    }

    @Test
    fun back_leaves() {
        show(ready)

        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("back"), actions.calls)
    }

    private fun show(page: WifiDetailsPageState, shown: WifiNetwork = network, access: PermissionAccess = PermissionAccess.AVAILABLE) {
        compose.showPage { WifiDetailsScreen(shown, page, access, chart = null, actions = actions) }
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
