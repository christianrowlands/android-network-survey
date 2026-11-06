package com.craxiom.networksurvey.ui.wifi.clients

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.craxiom.messaging.wifi.ClientFrameType
import com.craxiom.networksurvey.scout.clients.ScoutClient
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.signals.SignalsStripWords
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.wifi.WifiListActions
import com.craxiom.networksurvey.ui.wifi.WifiListContent
import com.craxiom.networksurvey.ui.wifi.model.ThrottleWarning
import com.craxiom.networksurvey.ui.wifi.model.WifiListMode
import com.craxiom.networksurvey.ui.wifi.model.WifiListUiState
import com.craxiom.networksurvey.ui.wifi.model.WifiScoutUi
import com.craxiom.networksurvey.util.WifiBand
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Clients mode of Signals > Wi-Fi: its pill only with an NS Scout as the source, the clients
 * by address, and the networks they looked for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class WifiClientsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()
    private val live = SignalsStripWords(warning = null, spoken = "Live view")
    private val scoutList = WifiListUiState(scanNumber = 12, apCount = 0, mode = WifiListMode.GROUPED, scout = WifiScoutUi("NS-A1B2", null))

    private val connected = ScoutClient(
        mac = "a2:4f:91:3c:0e:17", locallyAdministered = true, rssi = -51, lastHeardMs = NOW, channel = 36,
        bssid = "e0:63:da:41:7c:22", frameTypes = listOf(ClientFrameType.DATA, ClientFrameType.NULL_DATA), probedSsids = emptyList(),
    )
    private val prober = ScoutClient(
        mac = "3e:7b:20:d4:91:0a", locallyAdministered = true, rssi = -63, lastHeardMs = NOW - 4_000, channel = 6,
        bssid = null, frameTypes = listOf(ClientFrameType.PROBE_REQUEST), probedSsids = listOf("HOME-5G", "eduroam"),
    )
    private val clients = listOf(connected, prober)
    private val available = WifiClientsUiState(
        available = true, scoutName = "NS-A1B2", clients = clients, networks = WifiClientsMapper.networks(clients), nowMs = NOW,
    )

    @Test
    fun neverPaired_orThePhoneAsTheSource_hasNoClientsPill() {
        show(WifiListUiState(scanNumber = 3, apCount = 0), WifiClientsUiState())

        compose.onNodeWithText("Group by SSID").assertIsDisplayed()
        compose.onAllNodesWithText("Clients").assertCountEquals(0)
    }

    @Test
    fun theScoutAsTheSource_offersClients() {
        show(scoutList, available)

        compose.onNodeWithText("Clients").assertIsNotSelected().performClick()

        assertEquals(listOf("clients"), actions.calls)
    }

    @Test
    fun theClientsList_replacesTheAccessPoints_andCountsClients() {
        show(scoutList, available.copy(selected = true))

        compose.onNodeWithText("Clients").assertIsSelected()
        compose.onNodeWithText("Group by SSID").assertIsNotSelected()
        compose.onNodeWithText("NS-A1B2 · Scan #12 · 2 clients", useUnmergedTree = true).assertIsDisplayed()
        // Each row is one TalkBack item, so its words are its content description
        compose.onNodeWithContentDescription("Address a2, 4f, 91, 3c, 0e, 17, Randomized, Connected to Toast, -51 dBm", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Randomized, Looking for eduroam, HOME-5G, -63 dBm", substring = true).assertIsDisplayed()
    }

    @Test
    fun aClientRow_readsItsAddressInPairs_andOpensClientDetails() {
        show(scoutList, available.copy(selected = true))

        compose.onNodeWithContentDescription("Address a2, 4f, 91, 3c, 0e, 17", substring = true).performClick()

        assertEquals(listOf("client a2:4f:91:3c:0e:17"), actions.calls)
    }

    @Test
    fun byNetwork_listsTheNamesLookedFor_andOpensOneToItsClients() {
        val state = available.copy(selected = true, view = WifiClientsView.BY_NETWORK)
        show(scoutList, state)

        compose.onNodeWithContentDescription("HOME-5G, Looked for by 1 client").assertIsDisplayed()
        compose.onNodeWithContentDescription("eduroam, Looked for by 1 client").performClick()
        assertEquals(listOf("network eduroam"), actions.calls)
    }

    @Test
    fun anOpenNetwork_listsItsClients() {
        show(scoutList, available.copy(selected = true, view = WifiClientsView.BY_NETWORK, expandedNetworks = setOf("eduroam")))

        compose.onNodeWithContentDescription("Address 3e, 7b, 20, d4, 91, 0a", substring = true).performClick()

        assertEquals(listOf("client 3e:7b:20:d4:91:0a"), actions.calls)
    }

    @Test
    fun theViewToggle_switchesToNetworks() {
        show(scoutList, available.copy(selected = true))

        compose.onNodeWithText("By network looked for").performClick()

        assertEquals(listOf("view BY_NETWORK"), actions.calls)
    }

    @Test
    fun nothingHeardYet_saysSo() {
        show(scoutList, WifiClientsUiState(available = true, selected = true, scoutName = "NS-A1B2"))

        compose.onNodeWithText("No Wi-Fi clients heard yet", substring = true).assertIsDisplayed()
    }

    private fun show(state: WifiListUiState, clients: WifiClientsUiState) {
        compose.showPage {
            WifiListContent(state, live, PermissionAccess.AVAILABLE, actions, clients = clients, ssidOf = { if (it == "e0:63:da:41:7c:22") "Toast" else null })
        }
    }

    private class RecordingActions : WifiListActions {
        val calls = mutableListOf<String>()
        override fun onClientsSelected() { calls += "clients" }
        override fun onClientsViewChanged(view: WifiClientsView) { calls += "view $view" }
        override fun onToggleProbedNetwork(ssid: String) { calls += "network $ssid" }
        override fun onClientClick(mac: String) { calls += "client $mac" }
        override fun onNetworkClick(bssid: String) = Unit
        override fun onCopySsid(ssid: String) = Unit
        override fun onCopyBssid(bssid: String) = Unit
        override fun onToggleGroup(groupKey: String) = Unit
        override fun onModeChanged(mode: WifiListMode) { calls += "mode $mode" }
        override fun onSortSelected(index: Int) = Unit
        override fun onTogglePause() = Unit
        override fun onClearFilter() = Unit
        override fun onFilterQueryChanged(query: String) = Unit
        override fun onFilterBandToggled(band: WifiBand) = Unit
        override fun onManageExclusionList() = Unit
        override fun onOpenSpectrum() = Unit
        override fun onOpenThrottleSettings(warning: ThrottleWarning) = Unit
        override fun onDismissThrottle() = Unit
        override fun onAllow() = Unit
        override fun onOpenAppSettings() = Unit
        override fun onTurnOnLocation() = Unit
        override fun onTurnOnWifi() = Unit
        override fun onAllowNearby() = Unit
        override fun onTurnOnBluetooth() = Unit
        override fun onOpenScoutPage() = Unit
        override fun onUsePhoneWifi() = Unit
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
