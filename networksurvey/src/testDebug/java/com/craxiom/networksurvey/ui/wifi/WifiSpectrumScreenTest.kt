package com.craxiom.networksurvey.ui.wifi

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.wifi.model.WifiScanState
import com.craxiom.networksurvey.ui.wifi.spectrum.SpectrumBand
import com.craxiom.networksurvey.ui.wifi.spectrum.WifiSpectrumActions
import com.craxiom.networksurvey.ui.wifi.spectrum.WifiSpectrumScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The spectrum page: the scan interval, one card per band, and the notice when the phone cannot scan. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class WifiSpectrumScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()
    private val drawn = mutableSetOf<SpectrumBand>()

    @Test
    fun everyBand_hasItsCardAndChart() {
        show(WifiScanState.READY)

        compose.onNodeWithText("Scans every 15 seconds").assertIsDisplayed()
        compose.onNodeWithText("2.4 GHz").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("6 GHz"))
        compose.onNodeWithText("6 GHz").assertIsDisplayed()

        assertTrue(drawn.containsAll(listOf(SpectrumBand.GHZ_24, SpectrumBand.GHZ_6)))
    }

    @Test
    fun aMissingPermission_isAskedForOnlyFromItsButton() {
        show(WifiScanState.PERMISSION_NEEDED)

        compose.onNodeWithText("Allow precise location to get new scans.").assertIsDisplayed()
        compose.onNodeWithText("Allow").performClick()

        assertEquals(listOf("allow"), actions.calls)
    }

    @Test
    fun theScanIntervalGear_opensItsSetting_andInfoExplainsIt() {
        show(WifiScanState.READY)

        compose.onNodeWithContentDescription("Wi-Fi settings").performClick()
        compose.onNodeWithContentDescription("About Wi-Fi scan rate").performClick()
        compose.onNodeWithText("Wi-Fi scan rate").assertIsDisplayed()

        assertEquals(listOf("scan settings"), actions.calls)
    }

    private fun show(scanState: WifiScanState) {
        compose.showPage {
            WifiSpectrumScreen(scanState, scanRateSeconds = 15, permissionAccess = PermissionAccess.AVAILABLE, actions = actions) { band ->
                drawn += band
            }
        }
    }

    private class RecordingActions : WifiSpectrumActions {
        val calls = mutableListOf<String>()
        override fun onOpenSettings() { calls += "scan settings" }
        override fun onAllow() { calls += "allow" }
        override fun onOpenAppSettings() { calls += "settings" }
        override fun onTurnOnLocation() { calls += "location" }
        override fun onTurnOnWifi() { calls += "wifi" }
    }
}
