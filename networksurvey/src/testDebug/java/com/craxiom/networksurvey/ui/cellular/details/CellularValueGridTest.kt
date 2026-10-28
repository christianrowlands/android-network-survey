package com.craxiom.networksurvey.ui.cellular.details

import android.telephony.TelephonyManager
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.craxiom.networksurvey.data.band.BandTapTarget
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
import com.craxiom.networksurvey.ui.textLayout
import com.craxiom.networksurvey.util.TelephonyStateUtils.NrMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The serving cell's value grid never breaks a number: a 5G cell ID and a frequency with its unit
 * each stay on one line at a Pixel 10 Pro XL's default width (443dp), at a small phone's width
 * (360dp), and at large text on both. The grid gives the wide columns the width they need, and
 * drops a column when the values cannot all fit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CellularValueGridTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = WIDE_PHONE)
    fun atTheDefaultWidth_theScreenshotsValuesStayOnOneLine_fourToARow() {
        assertOneLine(SHOT_NCI, SHOT_NARFCN, SHOT_FREQUENCY)

        // The first row still holds MCC-MNC, TAC, CID and PCI, as in the prototype.
        val tops = listOf("MCC-MNC, 310-260", "CID, $SHOT_NCI", "PCI, $PCI_TEXT")
            .map { compose.onNodeWithContentDescription(it).getUnclippedBoundsInRoot().top }
        assertEquals(tops.toString(), 1, tops.distinct().size)
    }

    @Test
    @Config(qualifiers = WIDE_PHONE)
    fun atTheDefaultWidth_theLongestValuesStayOnOneLine() = assertOneLine(MAX_NCI, C_BAND_NARFCN, C_BAND_FREQUENCY)

    @Test
    @Config(qualifiers = WIDE_PHONE)
    fun atTheDefaultWidthAndLargeText_theLongestValuesStayOnOneLine() =
        assertOneLine(MAX_NCI, C_BAND_NARFCN, C_BAND_FREQUENCY, largeFont = true)

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun atASmallPhonesWidth_theLongestValuesStayOnOneLine() = assertOneLine(MAX_NCI, C_BAND_NARFCN, C_BAND_FREQUENCY)

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun atASmallPhonesWidthAndLargeText_theLongestValuesStayOnOneLine() =
        assertOneLine(MAX_NCI, C_BAND_NARFCN, C_BAND_FREQUENCY, largeFont = true)

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun atASmallPhonesWidth_aMillimeterWaveFrequencyStaysOnOneLine() = assertOneLine(SHOT_NCI, MMWAVE_NARFCN, MMWAVE_FREQUENCY)

    /** Shows a 5G standalone serving cell and checks its CID and frequency each lay out on one line. */
    private fun assertOneLine(nci: Long, narfcn: Int, frequency: String, largeFont: Boolean = false) {
        val records = listOf(DetailsRecords.nr(nci = nci, narfcn = narfcn, pci = SHOT_PCI, bands = null))
        val view = CellularSimView.from(CellularSimData().withTechnology(SA).withBatch(records, nowMs = 0), "None")
        val state = CellularDetailsUiState(
            pages = listOf(CellularPage(CellularController.DEFAULT_SUBSCRIPTION_ID, ViewedSim(1, 7, "T-Mobile"))),
            pagesKnown = true,
            scanState = CellularScanState.READY,
            bound = true,
            sim = view,
        )
        compose.showPage(largeFont = largeFont) { CellularDetailsContent(state, PermissionAccess.AVAILABLE, NoActions) }

        assertEquals(nci.toString(), 1, compose.textLayout(nci.toString()).lineCount)
        assertEquals(frequency, 1, compose.textLayout(frequency).lineCount)
        assertEquals(PCI_TEXT, 1, compose.textLayout(PCI_TEXT).lineCount)
    }

    private object NoActions : CellularDetailsActions {
        override fun onSelectPage(index: Int) = Unit
        override fun onAllow() = Unit
        override fun onOpenAppSettings() = Unit
        override fun onTurnOnLocation() = Unit
        override fun onOpenAirplaneSettings() = Unit
        override fun onInfo(info: CellularInfo) = Unit
        override fun onBandClick(target: BandTapTarget) = Unit
        override fun onCopy(label: String, value: String) = Unit
    }

    private companion object {
        /** The Pixel 10 Pro XL at its default display size: 1080 px at 390 dpi. */
        const val WIDE_PHONE = "w443dp-h986dp"
        const val SMALL_PHONE = "w360dp-h740dp"

        val SA = DetailsRecords.technology(
            nrMode = NrMode.STANDALONE,
            baseDataRat = TelephonyManager.NETWORK_TYPE_NR,
            voice = "VoNR",
            data = "NR",
        )

        /** The values in the Play Store screenshot that wrapped. */
        const val SHOT_NCI = 4_584_018_221L
        const val SHOT_NARFCN = 519_390
        const val SHOT_FREQUENCY = "2596.950 MHz"
        const val SHOT_PCI = 725
        const val PCI_TEXT = "725 (2/241)"

        /** The largest NR cell identity (36 bits, 11 digits) and a C-band frequency. */
        const val MAX_NCI = 68_719_476_735L
        const val C_BAND_NARFCN = 636_664
        const val C_BAND_FREQUENCY = "3549.960 MHz"

        /** A 28 GHz channel, the longest frequency text the grid shows. */
        const val MMWAVE_NARFCN = 2_070_833
        const val MMWAVE_FREQUENCY = "27500.040 MHz"
    }
}
