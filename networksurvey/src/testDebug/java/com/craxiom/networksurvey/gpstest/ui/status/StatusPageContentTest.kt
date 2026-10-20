package com.craxiom.networksurvey.gpstest.ui.status

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.gpstest.model.SbasType
import com.craxiom.networksurvey.ui.common.TrendPoint
import com.craxiom.networksurvey.ui.common.TrendSeries
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
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

/** The GNSS Status page: the location card, AGC and C/N0 by band, the satellite table, and how they fit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class StatusPageContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val copies = mutableListOf<String>()
    private val sorts = mutableListOf<Int>()
    private var showAllTaps = 0
    private var timeExplained = 0

    private val fullFix = FixReadings(
        latitude = 40.6892,
        longitude = -74.0445,
        altitudeMeters = 192.0,
        altitudeMslMeters = 190.0,
        accuracyMeters = 4f,
        verticalAccuracyMeters = 6f,
        speedMetersPerSecond = 1.5f,
        speedAccuracyMetersPerSecond = 0.5f,
        bearingDegrees = 240f,
        bearingAccuracyDegrees = 5.6f,
        pdop = 1.1,
        hdop = 0.8,
        vdop = 1.2,
        timeMs = System.currentTimeMillis(),
        ttff = "38 s",
        satellitesUsed = 8,
        satellitesInView = 20,
        fix = true,
    )

    private val gps = SatelliteRow.from(
        SatelliteStatus(12, GnssType.NAVSTAR, 38.4f, true, true, true, 45f, 120f).apply {
            hasCarrierFrequency = true
            carrierFrequencyHz = 1_575_420_000.0
            hasAgc = true
            agcDb = 2.5f
        },
    )
    private val galileo = SatelliteRow.from(
        SatelliteStatus(24, GnssType.GALILEO, 0f, true, false, false, 20f, 250f).apply {
            hasCarrierFrequency = true
            carrierFrequencyHz = 1_176_450_000.0
        },
    )
    private val waas = SatelliteRow.from(
        SatelliteStatus(131, GnssType.SBAS, 30f, true, true, false, 35f, 200f).apply { sbasType = SbasType.WAAS },
    )

    private fun series(vararg values: Float) = TrendSeries(values.mapIndexed { i, v -> TrendPoint(i * 1_000L, v) })

    /** Two minutes of a steady L1 band, then a drop: AGC 30 to 22, C/N0 42 to 35. */
    private val l1Drop = GnssTrends(
        nowMs = 4_000L,
        bands = listOf(BandTrend(GnssBandGroup.L1, agc = series(30f, 30f, 30f, 30f, 22f), cn0Top = series(42f, 42f, 42f, 42f, 35f))),
        signals = emptyMap(),
        agcReported = true,
    )

    private fun show(
        readings: FixReadings = fullFix,
        gnss: List<SatelliteRow> = listOf(gps, galileo),
        sbas: List<SatelliteRow> = emptyList(),
        trends: GnssTrends = GnssTrends.EMPTY,
        total: Int = gnss.size + sbas.size,
        largeFont: Boolean = false,
        sortIndex: Int = 2,
    ) {
        compose.showPage(largeFont = largeFont) {
            StatusPageContent(
                readings = readings,
                gnssRows = gnss,
                sbasRows = sbas,
                trends = trends,
                sortIndex = sortIndex,
                shownSignals = gnss.size + sbas.size,
                totalSignals = total,
                onSort = { sorts += it },
                onShowAll = { showAllTaps++ },
                onCopyLocation = if (readings.hasPosition) ({ copies += "location" }) else null,
                onCopyValue = { label, value -> copies += "$label=$value" },
                onExplainTime = { timeExplained++ },
            )
        }
    }

    /** Scrolls the page's list until the item that says [description] is composed and on screen. */
    private fun scrollTo(description: String, substring: Boolean = false) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription(description, substring = substring))
    }

    @Test
    fun aFix_showsEveryReadingBesideItsLabel() {
        show()

        compose.onNodeWithContentDescription("Location, Fix, Horizontal accuracy 4.0 m").assertIsDisplayed()
        listOf(
            "Lat, 40.6892000°", "Lon, -74.0445000°", "Alt, 192.0 m", "V. accuracy, 6.0 m", "PDOP, 1.1",
            "Used / seen, 8/20", "Alt (MSL), 190.0 m", "H/V DOP, 0.8/1.2", "TTFF, 38 s", "Speed, 5.4 km/h",
            "Bearing, 240.0°", "Bearing acc, 5.6°",
        ).forEach { compose.onNodeWithContentDescription(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithContentDescription("Time, ", substring = true).assertIsDisplayed()
    }

    @Test
    fun beforeAFix_everyReadingIsMissing_andNothingCopies() {
        show(readings = FixReadings(), gnss = emptyList())

        compose.onNodeWithContentDescription("Location, No fix").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lat, --").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Time, --").assertIsDisplayed()
        compose.onNodeWithContentDescription("Used / seen, 0/0").assertIsDisplayed()
        compose.onAllNodesWithText("Satellites").assertCountEquals(0)
        assertEquals(emptyList<String>(), copies)
    }

    @Test
    fun theCopyButton_andALongPressOnACoordinate_copyTheLocation() {
        show()

        compose.onNodeWithContentDescription(string(R.string.gnss_status_copy_location)).performClick()
        compose.onNodeWithContentDescription("Lon, -74.0445000°").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("PDOP, 1.1").performTouchInput { longClick() }

        assertEquals(listOf("location", "location", "PDOP=1.1"), copies)
    }

    @Test
    fun afterTheFixIsLost_theCoordinatesSayWhenTheLastFixWas() {
        show(readings = fullFix.copy(fix = false))

        compose.onNodeWithText("Last fix", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Location, No fix").assertIsDisplayed()
    }

    // The explanation itself is not opened here: a dialog whose text holds a link never goes idle
    // under Robolectric (it runs out of memory), so the test stops at the tap reaching the page.
    @Test
    fun aFixTimeDaysAway_explainsItselfOnATap() {
        show(readings = fullFix.copy(timeMs = 1_000_000_000_000L, timeValid = false))

        compose.onNodeWithContentDescription("Time, ", substring = true).performScrollTo().performClick()

        assertEquals(1, timeExplained)
    }

    @Test
    fun aGoodFixTime_hasNoTapAction() {
        show()

        compose.onNodeWithContentDescription("Time, ", substring = true).assert(hasNoClickAction())
    }

    @Test
    fun aRow_isOneTalkBackItem_withWhatItIsMissing() {
        show()

        scrollTo("GPS 12, L1, C/N0 38.4 dB-Hz, elevation 45 degrees, azimuth 120 degrees, AGC 2.5 dB")
        scrollTo("Galileo 24, E5a, not heard, elevation 20 degrees, azimuth 250 degrees, not used in fix, no ephemeris")
        compose.onNodeWithText("Sorted by Signal strength").assertIsDisplayed()
        compose.onNodeWithText("U used in fix · E ephemeris · A almanac").assertIsDisplayed()
        compose.onNodeWithText("38.4", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("2.5", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun tappingAColumnHeader_sortsByIt() {
        show()
        scrollTo("AGC")

        compose.onNodeWithContentDescription("AGC").performClick()
        compose.onNodeWithContentDescription("Used in fix").performClick()

        assertEquals(listOf(7, 3), sorts)
    }

    @Test
    fun withoutAgc_theTableLeavesTheColumnOut() {
        show(gnss = listOf(galileo))
        scrollTo("C/N0")

        compose.onAllNodesWithContentDescription("AGC").assertCountEquals(0)
    }

    @Test
    fun sbasSignals_haveTheirOwnSection_onlyWhenThereAreAny() {
        show()
        compose.onAllNodesWithText("SBAS satellites").assertCountEquals(0)
    }

    @Test
    fun sbasSignals_listedUnderTheirSection() {
        show(sbas = listOf(waas))

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("SBAS satellites"))
        scrollTo("WAAS 131", substring = true)
    }

    @Test
    fun aBandsTrend_readsAsOneItem_withItsChange() {
        show(trends = l1Drop)

        scrollTo(
            "L1 · E1 · B1C, AGC 22.0 dB, down 8.0 from the 2 minute median, " +
                "C/N0 top 4, 35.0 dB-Hz, down 7.0 from the 2 minute median",
        )
        compose.onNodeWithText("-8.0", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aPhoneWithoutAgc_saysSo_andShowsOnlyC_n0() {
        show(trends = l1Drop.copy(agcReported = false))

        compose.onNodeWithText(string(R.string.gnss_trends_no_agc)).performScrollTo().assertIsDisplayed()
        scrollTo("L1 · E1 · B1C, C/N0 top 4, 35.0 dB-Hz, down 7.0 from the 2 minute median")
    }

    @Test
    fun aFilterHidingSome_saysHowMany_andShowsAll_andTheTrendsSayTheyIgnoreIt() {
        show(total = 9, trends = l1Drop)

        compose.onNodeWithText("Showing 2 of 9 signals").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.gnss_trends_all_systems)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show all").performClick()

        assertEquals(1, showAllTaps)
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp")
    fun aLandscapePhone_putsTheSignalsBesideTheFix() {
        show()

        val fix = compose.onNodeWithText("Location", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val satellites = compose.onNodeWithText("Satellites").getUnclippedBoundsInRoot()
        assertTrue("fix $fix satellites $satellites", fix.left >= 0.dp && satellites.left >= fix.right)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atLargeText_theValuesWrapNothingAndNothingIsCut() {
        show(
            readings = fullFix.copy(timeMs = 1_000_000_000_000L, timeValid = false),
            largeFont = true,
            sortIndex = 4,
        )

        listOf("40.6892000°", "-74.0445000°", "6.0 m", "38 s", "0.8/1.2").forEach {
            val layout = compose.textLayout(it)
            assertFalse(it, layout.isLineEllipsized(layout.lineCount - 1))
            assertEquals(it, 1, layout.lineCount)
        }
        val time = compose.onNodeWithContentDescription("Time, ", substring = true).getUnclippedBoundsInRoot()
        assertTrue("time $time", time.right <= 360.dp)
        assertFalse(compose.textLayout("Location").isLineEllipsized(0))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun onA360dpPhone_c_n0TrendAndAgcFit_withoutScrollingSideways() {
        show()
        scrollTo("AGC")

        val agc = compose.onNodeWithContentDescription("AGC").getUnclippedBoundsInRoot()
        assertTrue("AGC header $agc", agc.right <= 360.dp)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun onA360dpPhone_azimuthMakesRoom_andElevationStays() {
        show(gnss = listOf(gps))
        scrollTo("GPS 12", substring = true)

        compose.onNodeWithText("45", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("120", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w392dp-h800dp")
    fun onA392dpPhone_everyColumnFits() {
        show(gnss = listOf(gps))
        scrollTo("GPS 12", substring = true)

        val azimuth = compose.onNodeWithText("120", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("azimuth $azimuth", azimuth.right <= 392.dp)
    }
}
