package com.craxiom.networksurvey.gpstest.ui.sky

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Sky page in its states, at a small phone's width. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class SkyPageContentTest {

    @get:Rule
    val compose = createComposeRule()

    private var showAllTaps = 0

    private fun signal(svid: Int, cn0: Float, used: Boolean = false, type: GnssType = GnssType.NAVSTAR) =
        SatelliteStatus(svid, type, cn0, true, true, used, 30f + svid, 40f * svid)

    private val inViewOnly = listOf(signal(1, 28f), signal(2, 22f), signal(3, 0f))
    private val withFix = listOf(signal(1, 40f, used = true), signal(2, 34f, used = true), signal(3, 21f), signal(4, 25f, type = GnssType.GALILEO))

    private fun show(signals: List<SatelliteStatus>, fix: Boolean = false, total: Int = signals.size, largeFont: Boolean = false) {
        compose.showPage(largeFont = largeFont) {
            SkyPageContent(
                sky = SkyModel.from(signals),
                fix = fix,
                shownSignals = signals.size,
                totalSignals = total,
                heading = { 0f },
                onShowAll = { showAllTaps++ },
            )
        }
    }

    @Test
    fun withoutAFix_itSaysSo_andHasNoUsedAverage() {
        show(inViewOnly)

        compose.onNodeWithText("No fix").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sky plot, 3 satellites, 0 used in the fix").assertIsDisplayed()
        compose.onNodeWithText("3 satellites · 0 used", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("In view, 25.0 dB-Hz").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Used in fix, none").assertIsDisplayed()
    }

    @Test
    fun withAFix_bothAveragesShow_andTheUsedCount() {
        show(withFix, fix = true)

        compose.onNodeWithText("Fix").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sky plot, 4 satellites, 2 used in the fix").assertIsDisplayed()
        compose.onNodeWithContentDescription("In view, 30.0 dB-Hz").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Used in fix, 37.0 dB-Hz").assertIsDisplayed()
    }

    @Test
    fun aFilterHidingSome_saysHowMany_andShowsAll() {
        show(withFix, fix = true, total = 9)

        compose.onNodeWithText("Showing 4 of 9 signals").assertIsDisplayed()
        compose.onNodeWithText("Show all").performClick()

        assertEquals(1, showAllTaps)
    }

    @Test
    fun withoutAFilter_thereIsNoNotice() {
        show(withFix)

        compose.onAllNodesWithText("Show all").assertCountEquals(0)
    }

    @Test
    fun legend_opensTheLegendSheet() {
        show(withFix)

        compose.onNodeWithText("Legend").performClick()

        compose.onNodeWithText("NAVSTAR GPS (USA)").assertIsDisplayed()
        compose.onNodeWithText("Signal strength (C/N0)").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp")
    fun aLandscapePhone_putsThePlotBesideTheCards_atAUsableSize() {
        show(withFix)

        val plot = compose.onNodeWithContentDescription("Sky plot", substring = true).getUnclippedBoundsInRoot()
        val averages = compose.onNodeWithText("Average C/N0").getUnclippedBoundsInRoot()
        assertTrue("plot $plot averages $averages", plot.left >= 0.dp && averages.left >= plot.right)
        assertTrue("plot $plot", plot.width >= 280.dp)
        assertEquals(plot.width.value, plot.height.value, 1f)
    }

    @Test
    @Config(qualifiers = "w1000dp-h700dp")
    fun aWideTallScreen_putsThePlotBesideTheCards() {
        show(withFix)

        val plot = compose.onNodeWithContentDescription("Sky plot", substring = true).getUnclippedBoundsInRoot()
        val averages = compose.onNodeWithText("Average C/N0").getUnclippedBoundsInRoot()
        assertTrue("plot $plot averages $averages", plot.left >= 0.dp && averages.left >= plot.right)
        assertTrue("plot $plot", minOf(plot.width, plot.height) >= 400.dp)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atLargeText_theHeaderAveragesAndCountsStillFit() {
        show(withFix, fix = true, total = 12, largeFont = true)

        assertFalse(compose.textLayout("Sky").isLineEllipsized(0))
        assertEquals(1, compose.textLayout("Legend").lineCount)
        assertEquals(1, compose.textLayout("Fix").lineCount)
        assertEquals(1, compose.textLayout("4 satellites · 2 used").lineCount)
        assertEquals(1, compose.textLayout("Average C/N0").lineCount)
        assertEquals(1, compose.textLayout("Showing 4 of 12 signals").lineCount)
        compose.onNodeWithText("37.0", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }
}
