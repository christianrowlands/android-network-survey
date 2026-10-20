package com.craxiom.networksurvey.ui.bluetooth.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.messaging.bluetooth.AddressType
import com.craxiom.messaging.bluetooth.SupportedTechnologies
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothScanState
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

/** The restyled Bluetooth details page: its fields, the notice when the phone cannot scan, and its actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class BluetoothDetailsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()
    private val keys = BluetoothRecordData.newBuilder()
        .setSourceAddress("02:00:F4:12:00:9C")
        .setOtaDeviceName("Forklift scanner 12")
        .setSupportedTechnologies(SupportedTechnologies.LE)
        .setAddressType(AddressType.PUBLIC)
        .setCompanyId("4C")
        .addServiceUuids("0000fe07-0000-1000-8000-00805f9b34fb")
        .build()

    @Test
    fun theHeroAndFields_showTheDevice() {
        show(keys, -63)

        compose.onNodeWithText("Bluetooth device details").assertIsDisplayed()
        compose.onNodeWithText("Forklift scanner 12").assertIsDisplayed()
        compose.onNodeWithText("02:00:F4:12:00:9C").assertIsDisplayed()
        compose.onNodeWithText("-63 dBm").assertIsDisplayed()
        // The fields are value grid cells, each one TalkBack item "label, value"
        compose.onNodeWithContentDescription("Technology, LE").assertIsDisplayed()
        compose.onNodeWithContentDescription("Address type, Public").assertIsDisplayed()
        compose.onNodeWithContentDescription("Company ID, 4C").assertIsDisplayed()
    }

    @Test
    fun aFindMyAdvertisement_showsItsKindAndTheManufacturerData() {
        // The raw advertisement from Android 13: Apple's ID, then Find My (0x12) with device type 2
        show(keys.toBuilder().setCompanyId("4c").setMfgData("4c00121920aabb").build(), -63)

        compose.onNodeWithContentDescription("Find My type, Tracker or accessory").assertIsDisplayed()
        compose.onNodeWithText("Manufacturer data").assertIsDisplayed()
        compose.onNodeWithText("4C 00 12 19 20 AA BB").assertIsDisplayed()
    }

    @Test
    fun anUnnamedDeviceWithoutASignal_saysSo() {
        show(keys.toBuilder().clearOtaDeviceName().build(), null)

        compose.onNodeWithText("No name").assertIsDisplayed()
        compose.onNodeWithText("--").assertIsDisplayed()
    }

    @Test
    fun bluetoothOff_offersToTurnItOn() {
        show(keys, -63, BluetoothScanState.BLUETOOTH_OFF)

        compose.onNodeWithText("Bluetooth is off, so no new scans arrive.").assertIsDisplayed()
        compose.onNodeWithText("Turn on").performClick()

        assertEquals(listOf("bluetooth"), actions.calls)
    }

    @Test
    fun aLongPressOnTheAddress_copiesIt() {
        show(keys, -63)

        compose.onNodeWithText("02:00:F4:12:00:9C").performTouchInput { longClick() }

        assertEquals(listOf("copy"), actions.calls)
    }

    @Test
    fun theScanIntervalCard_opensItsSetting() {
        show(keys, -63)

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Scans every 24 seconds"))
        compose.onNodeWithContentDescription("Bluetooth settings").performClick()

        assertEquals(listOf("scan settings"), actions.calls)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atLargeText_theNameAndSignalStillFit() {
        compose.showPage(largeFont = true) {
            BluetoothDetailsScreen(keys, -63, 24, BluetoothScanState.READY, PermissionAccess.AVAILABLE, chart = null, actions = actions)
        }

        assertFalse(compose.textLayout("Forklift scanner 12").isLineEllipsized(0))
        assertFalse(compose.textLayout("-63 dBm").isLineEllipsized(0))
    }

    private fun show(device: BluetoothRecordData, rssi: Int?, scanState: BluetoothScanState = BluetoothScanState.READY) {
        compose.showPage {
            BluetoothDetailsScreen(device, rssi, 24, scanState, PermissionAccess.AVAILABLE, chart = null, actions = actions)
        }
    }

    private class RecordingActions : BluetoothDetailsActions {
        val calls = mutableListOf<String>()
        override fun onBack() { calls += "back" }
        override fun onCopyAddress() { calls += "copy" }
        override fun onOpenSettings() { calls += "scan settings" }
        override fun onAllow() { calls += "allow" }
        override fun onOpenAppSettings() { calls += "settings" }
        override fun onTurnOnLocation() { calls += "location" }
        override fun onTurnOnBluetooth() { calls += "bluetooth" }
    }
}
