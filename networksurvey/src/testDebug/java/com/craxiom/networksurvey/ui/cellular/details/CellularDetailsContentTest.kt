package com.craxiom.networksurvey.ui.cellular.details

import android.telephony.TelephonyManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.band.BandTapTarget
import com.craxiom.networksurvey.data.band.BandTechnology
import com.craxiom.networksurvey.services.controller.CellularController
import com.craxiom.networksurvey.ui.cellular.details.model.CellularDetailsUiState
import com.craxiom.networksurvey.ui.cellular.details.model.CellularPage
import com.craxiom.networksurvey.ui.cellular.details.model.CellularScanState
import com.craxiom.networksurvey.ui.cellular.details.model.CellularSimData
import com.craxiom.networksurvey.ui.cellular.details.model.CellularSimView
import com.craxiom.networksurvey.ui.cellular.details.model.DetailsRecords
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.signals.ViewedSim
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.textLayout
import com.craxiom.networksurvey.util.TelephonyStateUtils.NrMode
import com.craxiom.networksurvey.util.TelephonyStateUtils.RegistrationRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Cellular view in each of its states, at a small phone's width. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class CellularDetailsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()
    private val default = CellularController.DEFAULT_SUBSCRIPTION_ID
    private val oneSim = listOf(CellularPage(default, ViewedSim(1, 7, "Mint")))
    private val nsa = DetailsRecords.technology(nrMode = NrMode.NON_STANDALONE, overrideDisplay = "NR NSA", overrideNetworkType = 3)

    private fun sim(data: CellularSimData) = CellularSimView.from(data, "None")

    private fun lte(): CellularSimView = sim(
        CellularSimData().withTechnology(DetailsRecords.technology(bandwidthsKhz = intArrayOf(20_000, 15_000)))
            .withBatch(listOf(DetailsRecords.lte(), DetailsRecords.lte(serving = false, pci = 41, rsrp = -91f)), nowMs = 0)
    )

    private fun ready(view: CellularSimView, pages: List<CellularPage> = oneSim) =
        CellularDetailsUiState(pages = pages, pagesKnown = true, scanState = CellularScanState.READY, bound = true, sim = view)

    @Test
    fun beforeTheFirstScan_itSaysItIsWaiting() {
        show(ready(CellularSimView()))

        compose.onNodeWithText("Waiting for the first scan").assertIsDisplayed()
    }

    @Test
    fun aMissingPermission_isAskedForOnlyFromItsButton() {
        show(CellularDetailsUiState(pages = oneSim, scanState = CellularScanState.PERMISSION_NEEDED))

        compose.onNodeWithText("To see the cells around you, allow precise Location and Phone.").assertIsDisplayed()
        assertEquals(emptyList<String>(), actions.calls)
        compose.onNodeWithText("Allow").performClick()

        assertEquals(listOf("allow"), actions.calls)
    }

    @Test
    fun aPermissionDeniedForGood_opensTheAppSettings() {
        show(CellularDetailsUiState(pages = oneSim, scanState = CellularScanState.PERMISSION_NEEDED), PermissionAccess.BLOCKED)

        compose.onNodeWithText("Open Settings").performClick()

        assertEquals(listOf("settings"), actions.calls)
    }

    @Test
    fun locationOff_offersToTurnItOn() {
        show(CellularDetailsUiState(pages = oneSim, scanState = CellularScanState.LOCATION_OFF))

        compose.onNodeWithText("Android only shares cell details while location is on.").assertIsDisplayed()
        compose.onNodeWithText("Turn on").performClick()

        assertEquals(listOf("location"), actions.calls)
    }

    @Test
    fun airplaneMode_replacesTheCells_andOpensItsSettings() {
        show(CellularDetailsUiState(pages = oneSim, scanState = CellularScanState.AIRPLANE_MODE, sim = lte()))

        compose.onNodeWithText("Airplane mode is on, so the phone sees no cells. Turn it off to see them.").assertIsDisplayed()
        compose.onAllNodesWithText("Serving cell").assertCountEquals(0)
        compose.onNodeWithText("Open Settings").performClick()

        assertEquals(listOf("airplane"), actions.calls)
    }

    @Test
    fun lte_showsTheHeroPillsAndServingCell() {
        show(ready(lte()))

        compose.onNodeWithText("Mint").assertIsDisplayed()
        compose.onNodeWithText("LTE", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Voice VoLTE").assertIsDisplayed()
        compose.onNodeWithText("Data LTE").assertIsDisplayed()
        compose.onNodeWithText("Branding None").assertIsDisplayed()
        compose.onNodeWithText("Serving cell").assertIsDisplayed()
        compose.onNodeWithText("LTE · Band 66").assertIsDisplayed()
        compose.onNodeWithContentDescription("RSRP, -79 dBm, strong").assertIsDisplayed()
        compose.onNodeWithContentDescription("PCI, 101 (2/33)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Bandwidth, 15 MHz").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Carrier aggregation · 2 carriers · 35 MHz").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("RSRP, last 2 minutes").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theBandValue_opensTheBandReference_andValuesCopyOnLongPress() {
        show(ready(lte()))

        compose.onNodeWithContentDescription("Band, 66 (AWS)").performScrollTo().performClick()
        compose.onNodeWithContentDescription("TAC, 12345").performScrollTo().performTouchInput { longClick() }

        assertEquals(listOf("band ${BandTapTarget.of(BandTechnology.LTE, 66)}", "copy TAC=12345"), actions.calls)
    }

    @Test
    fun theBand_showsItsNumber_withItsNameUnderIt() {
        show(ready(lte()))

        compose.onNodeWithText("66", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("AWS", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun signalValues_areBars_thatSpeakAndCopyAsOneItem() {
        show(ready(lte()))

        compose.onNodeWithContentDescription("RSRP, -79 dBm, strong").performTouchInput { longClick() }

        assertEquals(listOf("copy RSRP=-79 dBm"), actions.calls)
    }

    @Test
    fun theInfoButtons_openTheirExplanations() {
        show(ready(lte()))

        compose.onNodeWithContentDescription("Network technology information").performClick()
        compose.onNodeWithContentDescription("Terms on this card").performClick()

        assertEquals(listOf("info TECHNOLOGY", "info TERMS"), actions.calls)
    }

    @Test
    fun noService_isAWarning_withoutPills() {
        val unregistered = RegistrationRow(2, 1, false, TelephonyManager.NETWORK_TYPE_UNKNOWN, "")
        val technology = DetailsRecords.technology(
            baseDataRat = TelephonyManager.NETWORK_TYPE_UNKNOWN, voice = "None", data = "Unknown", rows = listOf(unregistered),
        )
        show(ready(sim(CellularSimData().withTechnology(technology).withBatch(emptyList(), nowMs = 0))))

        compose.onNodeWithText("No service", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Unknown carrier").assertIsDisplayed()
        compose.onNodeWithText("No serving cell in the last scan").assertIsDisplayed()
        compose.onAllNodesWithText("Voice None").assertCountEquals(0)
    }

    @Test
    fun noSim_hasNoTabs_andStillShowsWhatThePhoneSees() {
        show(ready(lte(), pages = listOf(CellularPage(default, ViewedSim.NO_SIM))))

        compose.onAllNodesWithText("No SIM").assertCountEquals(0)
        compose.onNodeWithText("Serving cell").assertIsDisplayed()
    }

    @Test
    fun nsa_showsTheNrSecondaryCell() {
        val view = sim(
            CellularSimData().withTechnology(nsa).withBatch(listOf(DetailsRecords.lte(), DetailsRecords.nrSecondary()), nowMs = 0)
        )
        show(ready(view))

        compose.onNodeWithText("LTE + NR · NSA").assertIsDisplayed()
        compose.onNodeWithText("Branding NR NSA").assertIsDisplayed()
        compose.onNodeWithText("5G NR secondary cell").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("SS-RSRP, -84 dBm, strong").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Frequency, 2600.550 MHz").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Last seen", substring = true).assertCountEquals(0)
    }

    @Test
    fun theIdleNrCard_saysHowLongAgo() {
        val live = CellularSimData().withTechnology(nsa).withBatch(listOf(DetailsRecords.lte(), DetailsRecords.nrSecondary()), nowMs = 0)
        show(ready(sim(live.withBatch(listOf(DetailsRecords.lte()), nowMs = 42_000))))

        val lastSeen = string(R.string.nr_last_seen_format, string(R.string.settings_seconds, 42))
        compose.onNodeWithContentDescription("${string(R.string.nr_idle_badge)}, $lastSeen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("PCI, 271 (1/90)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun twoSims_areTabs() {
        val pages = listOf(CellularPage(7, ViewedSim(1, 7, "Mint")), CellularPage(9, ViewedSim(2, 9, "Other")))
        show(ready(lte(), pages))

        compose.onNodeWithText("SIM 1 · Mint").assertIsDisplayed()
        compose.onNodeWithText("SIM 2 · Other").performClick()

        assertEquals(listOf("page 1"), actions.calls)
    }

    @Test
    fun neighbors_areListedByTechnology() {
        show(ready(lte()))

        compose.onNodeWithText("LTE neighbors").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("PCI 41, EARFCN 66811, Band 66, RSRQ -8 dB, TA 0, RSRP -91 dBm, good")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atLargeText_valuesStayOnOneLine() {
        val view = sim(
            CellularSimData().withTechnology(nsa).withBatch(listOf(DetailsRecords.lte(), DetailsRecords.nrSecondary()), nowMs = 0)
        )
        compose.showPage(largeFont = true) {
            CellularDetailsContent(ready(view), PermissionAccess.AVAILABLE, actions)
        }

        assertEquals(1, compose.textLayout("LTE + NR · NSA").lineCount)
        assertEquals(1, compose.textLayout("-79 dBm").lineCount)
        assertEquals(1, compose.textLayout("101 (2/33)").lineCount)
        assertEquals(1, compose.textLayout("25600003").lineCount)
        assertEquals(1, compose.textLayout("Voice VoLTE").lineCount)
    }

    private fun show(state: CellularDetailsUiState, access: PermissionAccess = PermissionAccess.AVAILABLE) {
        compose.showPage { CellularDetailsContent(state, access, actions) }
    }

    private class RecordingActions : CellularDetailsActions {
        val calls = mutableListOf<String>()
        override fun onSelectPage(index: Int) { calls += "page $index" }
        override fun onAllow() { calls += "allow" }
        override fun onOpenAppSettings() { calls += "settings" }
        override fun onTurnOnLocation() { calls += "location" }
        override fun onOpenAirplaneSettings() { calls += "airplane" }
        override fun onInfo(info: CellularInfo) { calls += "info $info" }
        override fun onBandClick(target: BandTapTarget) { calls += "band $target" }
        override fun onCopy(label: String, value: String) { calls += "copy $label=$value" }
    }
}
