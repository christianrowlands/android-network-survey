package com.craxiom.networksurvey.ui.wifi

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.plural
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.textLayout
import com.craxiom.networksurvey.ui.wifi.model.ThrottleWarning
import com.craxiom.networksurvey.ui.wifi.model.WifiAccessPointDisplay
import com.craxiom.networksurvey.ui.wifi.model.WifiDisplayFilter
import com.craxiom.networksurvey.ui.wifi.model.WifiDisplayItem
import com.craxiom.networksurvey.ui.wifi.model.WifiListMode
import com.craxiom.networksurvey.ui.wifi.model.WifiListUiState
import com.craxiom.networksurvey.ui.wifi.model.WifiScanState
import com.craxiom.networksurvey.util.WifiBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Wi-Fi list in each of its states, at a small phone's width. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class WifiListContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()
    private val live = "Live view" to "Live view"

    private val corp = ap("02:00:A4:11:F0:21", "Acme-Corp", -52, WifiBand.GHZ_5, 36)
    private val corp24 = ap("02:00:A4:11:F0:22", "Acme-Corp", -58, WifiBand.GHZ_24, 11)
    private val hidden = ap("02:00:70:20:1D:55", "", null, WifiBand.GHZ_24, 6)
    private val flat = WifiListUiState(
        items = listOf(WifiDisplayItem.Flat(corp), WifiDisplayItem.Flat(hidden)),
        scanNumber = 4,
        apCount = 2,
        visibleApCount = 2,
        mode = WifiListMode.FLAT,
    )
    private val group = WifiDisplayItem.GroupParent(
        groupKey = "ssid:Acme-Corp",
        displaySsid = "Acme-Corp",
        isHidden = false,
        aps = listOf(corp, corp24),
        bestRssi = -52,
        bands = listOf(WifiBand.GHZ_24, WifiBand.GHZ_5),
        encryptionLabel = "WPA2",
        anyPasspoint = false,
        expanded = true,
    )
    private val grouped = WifiListUiState(
        items = listOf(group, WifiDisplayItem.GroupChild(group.groupKey, corp), WifiDisplayItem.GroupChild(group.groupKey, corp24)),
        scanNumber = 4,
        apCount = 2,
        visibleApCount = 2,
    )

    @Test
    fun nothingFoundYet_saysSo() {
        show(WifiListUiState())

        compose.onNodeWithText("No Wi-Fi networks found yet").assertIsDisplayed()
        compose.onNodeWithText("Scan #0", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Live view", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aMissingPermission_isAskedForOnlyFromItsButton() {
        show(flat.copy(scanState = WifiScanState.PERMISSION_NEEDED))

        compose.onNodeWithText("Permission needed", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("To list nearby Wi-Fi networks, allow precise location.").assertIsDisplayed()
        assertEquals(emptyList<String>(), actions.calls)
        compose.onNodeWithText("Allow").performClick()

        assertEquals(listOf("allow"), actions.calls)
    }

    @Test
    fun aPermissionDeniedForGood_opensTheAppSettings() {
        show(flat.copy(scanState = WifiScanState.PERMISSION_NEEDED), PermissionAccess.BLOCKED)

        compose.onNodeWithText("Open Settings").performClick()

        assertEquals(listOf("settings"), actions.calls)
    }

    @Test
    fun locationOff_hidesTheRowsAndCounts_andOffersToTurnItOn() {
        show(flat.copy(scanState = WifiScanState.LOCATION_OFF))

        compose.onNodeWithText("Location is off", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText(plural(R.plurals.wifi_list_access_points, 2, 2), substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Acme-Corp", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("Turn on").performClick()

        assertEquals(listOf("location"), actions.calls)
    }

    @Test
    fun wifiAndScanningOff_offersToTurnWifiOn() {
        show(flat.copy(scanState = WifiScanState.WIFI_OFF))

        compose.onNodeWithText("Wi-Fi is off", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Turn on").performClick()

        assertEquals(listOf("wifi"), actions.calls)
    }

    @Test
    fun flatRows_showTheNameBssidTagsAndSignal() {
        show(flat)

        compose.onNodeWithText("Acme-Corp", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("02:00:A4:11:F0:21", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("-52 dBm", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("WPA2", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText("<Hidden SSID>", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("--", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(plural(R.plurals.wifi_list_access_points, 2, 2), substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aRow_isOneTalkBackItemWithTheBssidLast() {
        show(flat)

        val corpRow = compose.onNode(hasContentDescription("Acme-Corp, 5 GHz, channel 36, WPA2, Wi-Fi 6, -52 dBm", substring = true))
        corpRow.assertIsDisplayed()
        val spoken = corpRow.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(spoken, spoken.endsWith("02:00:A4:11:F0:21"))
        compose.onNode(hasContentDescription("Hidden network, 2.4 GHz, channel 6", substring = true)).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tapOpensDetails_andTheCopyActionsCopy() {
        show(flat)

        val row = compose.onNode(hasContentDescription("Acme-Corp, 5 GHz", substring = true))
        row.performClick()
        row.performCustomAccessibilityActionWithLabel("Copy SSID")
        row.performCustomAccessibilityActionWithLabel("Copy BSSID")

        assertEquals(listOf("open 02:00:A4:11:F0:21", "ssid Acme-Corp", "bssid 02:00:A4:11:F0:21"), actions.calls)
    }

    @Test
    fun aHiddenNetwork_hasNoSsidToCopy() {
        show(flat)

        val row = compose.onNode(hasContentDescription("Hidden network", substring = true)).fetchSemanticsNode()
        val labels = row.config[SemanticsActions.CustomActions].map { it.label }

        assertEquals(listOf("Copy BSSID"), labels)
    }

    @Test
    fun groupedRows_toggleTheGroup_andOpenAnAccessPoint() {
        show(grouped)

        compose.onNode(hasContentDescription("Acme-Corp, 2 APs, 2.4 GHz, 5 GHz, WPA2, -52 dBm")).performClick()
        compose.onNode(hasContentDescription("2.4 GHz, channel 11", substring = true)).performClick()

        assertEquals(listOf("toggle ssid:Acme-Corp", "open 02:00:A4:11:F0:22"), actions.calls)
    }

    @Test
    fun theModeTabs_switchMode() {
        show(grouped)

        compose.onNodeWithText("Flat · BSSID").performClick()

        assertEquals(listOf("mode FLAT"), actions.calls)
    }

    @Test
    fun aFilter_showsHowManyItLeaves() {
        show(flat.copy(apCount = 47, visibleApCount = 2, filter = WifiDisplayFilter(searchQuery = "acme")))

        compose.onNodeWithText(plural(R.plurals.wifi_list_access_points_filtered, 47, 2, 47), substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aFilterThatHidesEverything_canBeClearedInPlace() {
        show(WifiListUiState(apCount = 3, filter = WifiDisplayFilter(searchQuery = "nothing")))

        compose.onNodeWithText("No networks match the current filter").assertIsDisplayed()
        compose.onNodeWithText("Clear filter").performClick()

        assertEquals(listOf("clear"), actions.calls)
    }

    @Test
    fun theSortPill_opensTheSortChoices() {
        show(flat)

        compose.onNodeWithContentDescription("Sort by: Signal strength. Tap to change.").performClick()
        compose.onNodeWithText("Security type").assertIsDisplayed()
        compose.onNodeWithText("SSID").performClick()

        assertEquals(listOf("sort 1"), actions.calls)
    }

    @Test
    fun pausedUpdates_sayWhyTheListIsStill_andCanResume() {
        show(flat.copy(updatesPaused = true))

        compose.onNodeWithText("Updates paused", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Resume updates").performClick()

        assertEquals(listOf("pause"), actions.calls)
    }

    @Test
    fun throttling_isExplainedAboveTheRows_withOpenAndDismiss() {
        show(flat.copy(throttleWarning = ThrottleWarning(android9 = false, developerOptionsOn = false)))

        compose.onNodeWithText(string(R.string.android_10_throttling_information), substring = true).assertIsDisplayed()
        compose.onNodeWithText("Tap Build Number 7 times", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Open Settings").performClick()
        compose.onNodeWithText("Dismiss").performClick()

        assertEquals(listOf("throttle false", "dismiss"), actions.calls)
    }

    @Test
    fun android9Throttling_hasNothingToOpen() {
        show(flat.copy(throttleWarning = ThrottleWarning(android9 = true, developerOptionsOn = true)))

        compose.onNodeWithText("Android 9 throttles", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Open Settings").assertCountEquals(0)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atLargeText_theStripAndRowsStillFit() {
        val forklift = corp.copy(ssid = "Forklift-AP-3")
        compose.showPage(largeFont = true) {
            WifiListContent(
                state = flat.copy(
                    items = listOf(WifiDisplayItem.Flat(forklift)),
                    scanNumber = 128,
                    apCount = 47,
                    visibleApCount = 1,
                    filter = WifiDisplayFilter(searchQuery = "fork"),
                ),
                stateWords = "Recording" to "Recording",
                permissionAccess = PermissionAccess.AVAILABLE,
                actions = actions,
            )
        }

        assertFalse(compose.textLayout("Recording").isLineEllipsized(0))
        assertFalse(compose.textLayout("Scan #128", substring = true).isLineEllipsized(0))
        assertFalse(compose.textLayout(plural(R.plurals.wifi_list_access_points_filtered, 47, 1, 47), substring = true).isLineEllipsized(0))
        assertFalse(compose.textLayout("Signal strength", substring = true).isLineEllipsized(0))
        assertFalse(compose.textLayout("Forklift-AP-3").isLineEllipsized(0))
        assertFalse(compose.textLayout("-52 dBm").isLineEllipsized(0))
        compose.onNodeWithContentDescription("Pause updates").assertIsDisplayed()
    }

    private fun show(state: WifiListUiState, access: PermissionAccess = PermissionAccess.AVAILABLE) {
        compose.showPage { WifiListContent(state, live, access, actions) }
    }

    private fun ap(bssid: String, ssid: String, rssi: Int?, band: WifiBand, channel: Int) = WifiAccessPointDisplay(
        bssid = bssid,
        ssid = ssid,
        ssidIsHidden = ssid.isEmpty(),
        rssi = rssi,
        frequencyMhz = null,
        band = band,
        channel = channel,
        bandwidthLabel = "80 MHz",
        standardLabel = "Wi-Fi 6",
        encryptionLabel = "WPA2",
        passpoint = false,
        isExcluded = false,
    )

    private class RecordingActions : WifiListActions {
        val calls = mutableListOf<String>()
        override fun onNetworkClick(bssid: String) { calls += "open $bssid" }
        override fun onCopySsid(ssid: String) { calls += "ssid $ssid" }
        override fun onCopyBssid(bssid: String) { calls += "bssid $bssid" }
        override fun onToggleGroup(groupKey: String) { calls += "toggle $groupKey" }
        override fun onModeChanged(mode: WifiListMode) { calls += "mode $mode" }
        override fun onSortSelected(index: Int) { calls += "sort $index" }
        override fun onTogglePause() { calls += "pause" }
        override fun onClearFilter() { calls += "clear" }
        override fun onAllow() { calls += "allow" }
        override fun onOpenAppSettings() { calls += "settings" }
        override fun onTurnOnLocation() { calls += "location" }
        override fun onTurnOnWifi() { calls += "wifi" }
        override fun onOpenThrottleSettings(warning: ThrottleWarning) { calls += "throttle ${warning.developerOptionsOn}" }
        override fun onDismissThrottle() { calls += "dismiss" }
    }
}
