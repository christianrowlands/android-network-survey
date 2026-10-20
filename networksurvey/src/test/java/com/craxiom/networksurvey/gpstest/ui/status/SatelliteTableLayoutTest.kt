package com.craxiom.networksurvey.gpstest.ui.status

import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.common.TrendPoint
import com.craxiom.networksurvey.ui.common.TrendSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The satellite table's columns at each width, and the trend panel's scale and changes. */
class SatelliteTableLayoutTest {

    /** 30dp per column and the trend at its 40dp minimum, with 8 gaps of 6dp: 328dp in all. */
    private val natural = TableColumn.entries.associateWith { if (it == TableColumn.TREND) TREND_MIN_WIDTH else 30.dp }

    @Test
    fun aWideScreen_showsEveryColumn_andGivesTheRestToTheTrend() {
        val layout = TableLayout.fit(natural, 500.dp, anyAgc = true)

        assertEquals(TableColumn.entries, layout.columns)
        assertEquals(TREND_MAX_WIDTH, layout.widths[TableColumn.TREND])
        assertFalse(layout.scrolls)
    }

    @Test
    fun withoutAgc_theAgcColumnGoesFirst() {
        val layout = TableLayout.fit(natural, 500.dp, anyAgc = false)

        assertFalse(TableColumn.AGC in layout.columns)
    }

    @Test
    fun tooNarrow_dropsAzimuth_thenElevation() {
        val withoutAz = TableLayout.fit(natural, 320.dp, anyAgc = true)
        assertFalse(TableColumn.AZ in withoutAz.columns)
        assertTrue(TableColumn.EL in withoutAz.columns)

        val withoutBoth = TableLayout.fit(natural, 290.dp, anyAgc = true)
        assertFalse(TableColumn.EL in withoutBoth.columns)
        assertTrue(TableColumn.AGC in withoutBoth.columns)
        assertFalse(withoutBoth.scrolls)
    }

    @Test
    fun stillTooNarrow_scrolls_andKeepsC_n0AndAgc() {
        val layout = TableLayout.fit(natural, 200.dp, anyAgc = true)

        assertTrue(layout.scrolls)
        assertTrue(TableColumn.CN0 in layout.columns)
        assertTrue(TableColumn.AGC in layout.columns)
    }

    private fun series(vararg values: Float) = TrendSeries(values.mapIndexed { i, v -> TrendPoint(i * 1_000L, v) })

    @Test
    fun agcScale_neverSpansLessThanTenDb() {
        assertEquals(25f to 35f, agcScale(series(30f, 30.5f, 29.5f)))
        assertEquals(10f to 40f, agcScale(series(10f, 40f)))
    }

    @Test
    fun aChange_isNotable_fromThreeDbOffTheMedian() {
        assertNull(notableChange(series(30f, 30f, 30f, 28f)))
        assertEquals(-8f, notableChange(series(30f, 30f, 30f, 22f))!!, 0.001f)
    }
}
