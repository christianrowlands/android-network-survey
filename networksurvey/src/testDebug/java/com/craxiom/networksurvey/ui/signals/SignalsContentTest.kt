package com.craxiom.networksurvey.ui.signals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.main.SurveyStatus
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Signals sub-tab row, the scan strip, and the top bar actions of each sub-tab. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
// A small phone's width, so the label checks are not flattered by a wide screen.
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
// Real text measurement, so the label checks measure the actual font.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignalsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val idle = SurveyStatus()
    private val recordingCellular = SurveyStatus(recording = true, recordTypes = setOf(RecordType.CELLULAR, RecordType.PHONE_STATE))
    private val mint = ViewedSim(slot = 1, subscriptionId = 3, carrier = "Mint")

    @Test
    fun theSubTabs_areTabs_withTheSelectedOneMarked() {
        var selected = SignalsSubTab.CELLULAR
        compose.showPage {
            Column(Modifier.fillMaxSize()) { SignalsSubTabRow(selected, { selected = it }) }
        }
        listOf("Cellular", "Wi-Fi", "Bluetooth", "GNSS").forEach { label ->
            compose.onNodeWithText(label)
                .assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                .assertHeightIsAtLeast(48.dp)
            // One touch target tall, not stretched over the space below it.
            assertTrue(compose.onNodeWithText(label).getUnclippedBoundsInRoot().let { it.bottom - it.top } <= 56.dp)
        }
        compose.onNodeWithText("Cellular").assertIsSelected()
        compose.onNodeWithText("GNSS").assertIsNotSelected().performClick()
        assertEquals(SignalsSubTab.GNSS, selected)
    }

    @Test
    fun theLongestLabel_fitsAtLargeFont() {
        compose.showPage(largeFont = true) { SignalsSubTabRow(SignalsSubTab.BLUETOOTH, {}) }
        // Not cut short: one line, nothing ellipsized.
        val layout = compose.textLayout("Bluetooth")
        assertEquals(1, layout.lineCount)
        assertFalse(layout.isLineEllipsized(0))
    }

    @Test
    fun theLabels_stayOnOneLine_atTheLargestFont() {
        compose.showPage(fontScale = 1.5f) { SignalsSubTabRow(SignalsSubTab.CELLULAR, {}) }
        listOf("Cellular", "Wi-Fi", "Bluetooth", "GNSS").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
            assertEquals(1, compose.textLayout(label).lineCount)
            assertFalse(compose.textLayout(label).isLineEllipsized(0))
        }
    }

    @Test
    fun noSurvey_isALiveView_withTheScanInterval() {
        val state = scanStripState(SignalsSubTab.BLUETOOTH, idle, 30, null)
        assertEquals(ScanStripState(StripState.LIVE, 30, null), state)
        compose.showPage { ScanStrip(state) }
        compose.onNodeWithContentDescription("Live view, scans every 30 seconds").assertIsDisplayed()
    }

    @Test
    fun aSurveyRecordingThisType_saysRecording_withTheSim() {
        val state = scanStripState(SignalsSubTab.CELLULAR, recordingCellular, 5, mint)
        assertEquals(ScanStripState(StripState.RECORDING, 5, mint), state)
        compose.showPage { ScanStrip(state) }
        compose.onNodeWithContentDescription("SIM 1, Mint. Recording, scans every 5 seconds").assertIsDisplayed()
    }

    @Test
    fun aSurveyWithoutThisType_saysSo_withoutAnInterval() {
        val state = scanStripState(SignalsSubTab.BLUETOOTH, recordingCellular, 30, mint)
        assertEquals(ScanStripState(StripState.NOT_IN_SURVEY, null, null), state)
        compose.showPage { ScanStrip(state) }
        compose.onNodeWithContentDescription("Live view, not in this survey").assertIsDisplayed()
    }

    @Test
    fun aPausedSurvey_saysPaused() {
        val paused = recordingCellular.copy(pausedForBattery = true)
        val state = scanStripState(SignalsSubTab.CELLULAR, paused, 5, ViewedSim.NO_SIM)
        assertEquals(ScanStripState(StripState.PAUSED, null, ViewedSim.NO_SIM), state)
        compose.showPage { ScanStrip(state) }
        compose.onNodeWithContentDescription("No SIM. Paused").assertIsDisplayed()
    }

    @Test
    fun gnss_hasNoInterval_becauseItsViewUpdatesAtTheReceiversRate() {
        val recording = SurveyStatus(recording = true, recordTypes = setOf(RecordType.GNSS))
        assertEquals(ScanStripState(StripState.LIVE, null, null), scanStripState(SignalsSubTab.GNSS, idle, 45, mint))
        assertEquals(ScanStripState(StripState.RECORDING, null, null), scanStripState(SignalsSubTab.GNSS, recording, 45, mint))
    }

    @Test
    fun onlyCellular_showsTheSim() {
        assertNull(scanStripState(SignalsSubTab.BLUETOOTH, idle, 30, mint).sim)
        assertEquals(mint, scanStripState(SignalsSubTab.CELLULAR, idle, 5, mint).sim)
    }

    @Test
    fun aLongCarrier_atLargeFont_keepsTheWholeStrip() {
        val longCarrier = mint.copy(carrier = "A Very Long Mobile Virtual Network Operator Name")
        compose.showPage(largeFont = true) { ScanStrip(scanStripState(SignalsSubTab.CELLULAR, recordingCellular, 10, longCarrier)) }
        compose.onNodeWithContentDescription(
            "SIM 1, A Very Long Mobile Virtual Network Operator Name. Recording, scans every 10 seconds"
        ).assertIsDisplayed()
    }

    @Test
    fun eachSubTab_hasItsOwnActions() {
        assertEquals(listOf(SignalsAction.OPEN_MAP), signalsActions(SignalsSubTab.CELLULAR, GnssPage.STATUS))
        assertEquals(
            listOf(SignalsAction.WIFI_SPECTRUM, SignalsAction.WIFI_FILTER),
            signalsActions(SignalsSubTab.WIFI, GnssPage.STATUS),
        )
        assertEquals(listOf(SignalsAction.BLUETOOTH_FILTER), signalsActions(SignalsSubTab.BLUETOOTH, GnssPage.STATUS))
        assertEquals(
            listOf(SignalsAction.GNSS_SORT, SignalsAction.GNSS_FILTER),
            signalsActions(SignalsSubTab.GNSS, GnssPage.STATUS),
        )
        assertEquals(listOf(SignalsAction.GNSS_FILTER), signalsActions(SignalsSubTab.GNSS, GnssPage.SKY))
    }

}
