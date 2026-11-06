package com.craxiom.networksurvey.ui.wifi.clients

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
import com.craxiom.messaging.wifi.ClientFrameType
import com.craxiom.networksurvey.model.ScoutWifiDetails
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.model.WifiNetworkScout
import com.craxiom.networksurvey.scout.clients.ScoutClient
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.showPage
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

/** Client details (each card only with its data) and an access point's Connected clients card. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class WifiClientDetailsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val client = ScoutClient(
        mac = "a2:4f:91:3c:0e:17", locallyAdministered = true, rssi = -51, lastHeardMs = NOW, channel = 36,
        bssid = "e0:63:da:41:7c:22",
        frameTypes = listOf(ClientFrameType.DATA, ClientFrameType.NULL_DATA, ClientFrameType.PROBE_REQUEST),
        probedSsids = listOf("CaribouRoam", "HOME-5G", "eduroam"),
    )
    private val heard = WifiClientDetailsState(mac = client.mac, client = client, heardNow = true, scoutName = "NS-A1B2", nowMs = NOW)

    @Test
    fun theHero_namesTheClient_whoHeardIt_andWhen() {
        showDetails(heard)

        compose.onNodeWithContentDescription("a2, 4f, 91, 3c, 0e, 17").assertIsDisplayed()
        compose.onNodeWithText("Randomized").assertIsDisplayed()
        compose.onNodeWithText("Heard by NS-A1B2").assertIsDisplayed()
        compose.onNodeWithText("-51 dBm").assertIsDisplayed()
        compose.onNodeWithText("Last heard", substring = true).assertIsDisplayed()
        compose.onNodeWithText("channel 36", substring = true).assertIsDisplayed()
    }

    @Test
    fun connectedTo_namesTheNetwork_andOpensIt_whenTheListHasIt() {
        showDetails(heard, inList = true)

        compose.onNodeWithText("Toast").assertIsDisplayed().performClick()

        assertEquals(listOf("network e0:63:da:41:7c:22"), calls)
    }

    @Test
    fun theOtherCards_showWhatItLookedFor_andWhatItSent() {
        showDetails(heard)

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Networks it looked for"))
        compose.onNodeWithText("eduroam").assertIsDisplayed()
        compose.onNodeWithText("CaribouRoam").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Seen in"))
        compose.onNodeWithText("Null data").assertExists()
        compose.onNodeWithText("Probe request").assertExists()
        compose.onNodeWithText("Randomized means", substring = true).assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Signal"))
    }

    @Test
    fun aClientThatOnlyProbes_hasNoConnectedCard_andAPublicAddressNoExplanation() {
        val prober = client.copy(bssid = null, locallyAdministered = false, frameTypes = listOf(ClientFrameType.PROBE_REQUEST))
        showDetails(heard.copy(client = prober))

        compose.onAllNodesWithText("Connected to").assertCountEquals(0)
        compose.onAllNodesWithText("Randomized means", substring = true).assertCountEquals(0)
    }

    @Test
    fun notHeardYet_saysSo_withoutCards() {
        showDetails(WifiClientDetailsState(mac = client.mac, scoutName = "NS-A1B2"))

        compose.onNodeWithText("Not heard recently", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Seen in").assertCountEquals(0)
    }

    @Test
    fun anAccessPointTheScoutHeard_listsItsConnectedClients() {
        showAccessPoint(listOf(client))

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Connected clients"))
        compose.onNodeWithText("1 heard").assertExists()
        compose.onNodeWithContentDescription("Address a2, 4f, 91, 3c, 0e, 17", substring = true).performClick()
        assertEquals(listOf("client a2:4f:91:3c:0e:17"), calls)
    }

    @Test
    fun anAccessPointWithoutClients_hasNoConnectedClientsCard() {
        showAccessPoint(emptyList())

        compose.onAllNodesWithText("Connected clients").assertCountEquals(0)
    }

    private fun showDetails(state: WifiClientDetailsState, inList: Boolean = false) {
        compose.showPage {
            WifiClientDetailsScreen(state, chart = null, actions = object : WifiClientDetailsActions {
                override fun onBack() { calls += "back" }
                override fun ssidOf(bssid: String) = "Toast"
                override fun canOpenNetwork(bssid: String) = inList
                override fun onOpenNetwork(bssid: String) { calls += "network $bssid" }
            })
        }
    }

    private fun showAccessPoint(clients: List<ScoutClient>) {
        val network = WifiNetwork(
            "e0:63:da:41:7c:22", -48f, "Toast", 5180, 36, null, "WPA2/WPA3", null, "", null,
            WifiNetworkScout(ScoutWifiDetails("NS-A1B2", null, null, 0, null), emptyList(), emptyList(), false, null, null),
        )
        val page = WifiDetailsPageState(-48, 15, WifiScanState.READY, excluded = false, watched = false, connectedClients = clients)
        compose.showPage {
            WifiDetailsScreen(network, page, PermissionAccess.AVAILABLE, chart = null, actions = object : WifiDetailsActions {
                override fun onBack() = Unit
                override fun onToggleExcluded() = Unit
                override fun onWatchlistClick() = Unit
                override fun onOpenSettings() = Unit
                override fun onAllow() = Unit
                override fun onOpenAppSettings() = Unit
                override fun onTurnOnLocation() = Unit
                override fun onTurnOnWifi() = Unit
                override fun onClientClick(client: ScoutClient) { calls += "client ${client.mac}" }
            })
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
