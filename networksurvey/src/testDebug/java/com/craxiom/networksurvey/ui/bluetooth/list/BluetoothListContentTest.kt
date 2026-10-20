package com.craxiom.networksurvey.ui.bluetooth.list

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothDeviceDisplay
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothDisplayFilter
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothListUiState
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothScanState
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothSort
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothTech
import com.craxiom.networksurvey.ui.bluetooth.list.model.FindMyDevice
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Bluetooth list in each of its states, at a small phone's width. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class BluetoothListContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()
    private val live = "Live view" to "Live view"

    private val keys = BluetoothDeviceDisplay("02:00:F4:12:00:9C", "Keys", -48, "Apple, Inc.", BluetoothTech.LE, tracker = FindMyDevice.AIRTAG)
    private val unnamed = BluetoothDeviceDisplay("02:00:5C:8D:31:AA", "", null, "", BluetoothTech.UNKNOWN, tracker = null)
    private val populated = BluetoothListUiState(rows = listOf(keys, unnamed), totalCount = 2)

    @Test
    fun nothingHeardYet_saysSo() {
        show(BluetoothListUiState())

        compose.onNodeWithText("No Bluetooth devices found yet").assertIsDisplayed()
        compose.onNodeWithText("0 devices", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Live view", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aMissingPermission_isAskedForOnlyFromItsButton() {
        show(BluetoothListUiState(scanState = BluetoothScanState.PERMISSION_NEEDED))

        compose.onNodeWithText("Permission needed", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(emptyList<String>(), actions.calls)
        compose.onNodeWithText("Allow").performClick()

        assertEquals(listOf("allow"), actions.calls)
    }

    @Test
    fun aPermissionDeniedForGood_opensTheAppSettings() {
        show(BluetoothListUiState(scanState = BluetoothScanState.PERMISSION_NEEDED), PermissionAccess.BLOCKED)

        compose.onNodeWithText("Open Settings").performClick()

        assertEquals(listOf("settings"), actions.calls)
    }

    @Test
    fun locationOff_offersToTurnItOn() {
        show(BluetoothListUiState(scanState = BluetoothScanState.LOCATION_OFF))

        compose.onNodeWithText("Location is off", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Android only finds Bluetooth devices while location is on.").assertIsDisplayed()
        compose.onNodeWithText("Turn on").performClick()

        assertEquals(listOf("location"), actions.calls)
    }

    @Test
    fun bluetoothOff_offersToTurnItOn() {
        show(BluetoothListUiState(totalCount = 4, scanState = BluetoothScanState.BLUETOOTH_OFF))

        compose.onNodeWithText("Bluetooth is off", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("4 devices", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("Turn on").performClick()

        assertEquals(listOf("bluetooth"), actions.calls)
    }

    @Test
    fun noRadio_saysSoWithNothingToTap() {
        show(BluetoothListUiState(scanState = BluetoothScanState.NOT_SUPPORTED))

        compose.onNodeWithText("This phone has no Bluetooth radio.").assertIsDisplayed()
        compose.onAllNodesWithText("Turn on").assertCountEquals(0)
    }

    @Test
    fun theRows_showNameTagAddressCompanyAndSignal() {
        show(populated)

        compose.onNodeWithText("Keys", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("AirTag", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("02:00:F4:12:00:9C · Apple, Inc. · LE", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("-48 dBm", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("No name", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("--", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("2 devices", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aFindMyTrackerThatMayNotBeAnAirTag_isTaggedFindMy() {
        val tracker = BluetoothDeviceDisplay("C2:10:4A:7B:33:01", "", -55, "Apple, Inc.", BluetoothTech.LE, tracker = FindMyDevice.FIND_MY_ACCESSORY)
        show(BluetoothListUiState(rows = listOf(tracker), totalCount = 1))

        compose.onNodeWithText("Find My", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("AirTag", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aRow_isOneTalkBackItemWithTheAddressLast() {
        show(populated)

        compose.onNodeWithContentDescription("Keys, AirTag, Apple, Inc., LE, -48 dBm, 02:00:F4:12:00:9C").assertIsDisplayed()
        compose.onNodeWithContentDescription("No name, No signal, 02:00:5C:8D:31:AA").assertIsDisplayed()
    }

    @Test
    fun tapOpensDetails_andLongPressCopiesTheAddress() {
        show(populated)

        compose.onNodeWithContentDescription("Keys", substring = true).performClick()
        compose.onNodeWithContentDescription("No name", substring = true).performTouchInput { longClick() }

        assertEquals(listOf("open 02:00:F4:12:00:9C", "copy 02:00:5C:8D:31:AA"), actions.calls)
    }

    @Test
    fun aFilter_showsHowManyItLeaves() {
        show(BluetoothListUiState(rows = listOf(keys, unnamed), totalCount = 5, filter = BluetoothDisplayFilter(namedOnly = true)))

        compose.onNodeWithText("2 of 5 devices", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aFilterThatHidesEverything_canBeClearedInPlace() {
        show(BluetoothListUiState(totalCount = 3, filter = BluetoothDisplayFilter(query = "nothing")))

        compose.onNodeWithText("No devices match the current filter").assertIsDisplayed()
        compose.onNodeWithText("Clear filter").performClick()

        assertEquals(listOf("clear"), actions.calls)
    }

    @Test
    fun theSortPill_opensTheSortChoices() {
        show(populated)

        compose.onNodeWithContentDescription("Sort by: Signal strength. Tap to change.").performClick()
        compose.onNodeWithText("Technology").assertIsDisplayed()
        compose.onNodeWithText("Name").performClick()

        assertEquals(listOf("sort ${BluetoothSort.NAME}"), actions.calls)
    }

    @Test
    fun pausedUpdates_sayWhyTheListIsStill_andCanResume() {
        show(populated.copy(paused = true))

        compose.onNodeWithText("Updates paused", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Resume updates").performClick()

        assertEquals(listOf("pause"), actions.calls)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atLargeText_theStripAndRowsStillFit() {
        val longName = keys.copy(name = "Forklift scanner 12")
        compose.showPage(largeFont = true) {
            BluetoothListContent(
                state = BluetoothListUiState(rows = listOf(longName), totalCount = 12, filter = BluetoothDisplayFilter(namedOnly = true)),
                stateWords = "Recording" to "Recording",
                permissionAccess = PermissionAccess.AVAILABLE,
                actions = actions,
            )
        }

        assertFalse(compose.textLayout("Recording").isLineEllipsized(0))
        assertFalse(compose.textLayout("1 of 12 devices").isLineEllipsized(0))
        assertFalse(compose.textLayout("Signal strength", substring = true).isLineEllipsized(0))
        assertFalse(compose.textLayout("Forklift scanner 12").isLineEllipsized(0))
        compose.onNodeWithContentDescription("Pause updates").assertIsDisplayed()
    }

    private fun show(state: BluetoothListUiState, access: PermissionAccess = PermissionAccess.AVAILABLE) {
        compose.showPage { BluetoothListContent(state, live, access, actions) }
    }

    private class RecordingActions : BluetoothListActions {
        val calls = mutableListOf<String>()
        override fun onDeviceClick(address: String) { calls += "open $address" }
        override fun onDeviceLongClick(address: String) { calls += "copy $address" }
        override fun onSortSelected(index: Int) { calls += "sort $index" }
        override fun onTogglePause() { calls += "pause" }
        override fun onClearFilter() { calls += "clear" }
        override fun onAllow() { calls += "allow" }
        override fun onOpenAppSettings() { calls += "settings" }
        override fun onTurnOnLocation() { calls += "location" }
        override fun onTurnOnBluetooth() { calls += "bluetooth" }
    }
}
