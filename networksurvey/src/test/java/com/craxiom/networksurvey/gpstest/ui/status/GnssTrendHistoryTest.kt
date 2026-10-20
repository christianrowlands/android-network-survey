package com.craxiom.networksurvey.gpstest.ui.status

import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two minutes of AGC and C/N0, by signal and by band. */
class GnssTrendHistoryTest {

    private val history = GnssTrendHistory()

    private fun signal(svid: Int, cn0: Float, agc: Float? = null, mhz: Double = 1575.42) =
        SatelliteStatus(svid, GnssType.NAVSTAR, cn0, true, true, true, 45f, 90f).apply {
            hasCarrierFrequency = true
            carrierFrequencyHz = mhz * 1_000_000.0
            signalKey = "$svid $mhz"
            if (agc != null) {
                hasAgc = true
                agcDb = agc
            }
        }

    @Test
    fun eachSignal_keepsItsC_n0_andOldPointsLeaveTheWindow() {
        history.record(0, listOf(signal(1, 40f)))
        history.record(1_000, listOf(signal(1, 41f)))
        history.record(TREND_WINDOW_MS + 500, listOf(signal(1, 42f)))

        val points = history.snapshot(TREND_WINDOW_MS + 500).signal("1 1575.42").points
        assertEquals(listOf(41f, 42f), points.map { it.value })
    }

    @Test
    fun aSignalStillListedAtZero_recordsTheDrop() {
        history.record(0, listOf(signal(1, 40f)))
        history.record(1_000, listOf(signal(1, 0f)))

        assertEquals(0f, history.snapshot(1_000).signal("1 1575.42").last)
    }

    @Test
    fun aBand_averagesItsFourStrongestSignals_andTakesTheMedianAgc() {
        val sky = listOf(
            signal(1, 45f, agc = 30f),
            signal(2, 43f, agc = 31f),
            signal(3, 41f, agc = 35f),
            signal(4, 39f),
            signal(5, 20f),
            signal(6, 30f, agc = 10f, mhz = 1176.45),
        )
        history.record(0, sky)

        val bands = history.snapshot(0).bands
        assertEquals(listOf(GnssBandGroup.L1, GnssBandGroup.L5), bands.map { it.group })
        val l1 = bands.first()
        assertEquals(42f, l1.cn0Top.last!!, 0.001f)
        assertEquals(31f, l1.agc.last!!, 0.001f)
        assertEquals(10f, bands.last().agc.last!!, 0.001f)
    }

    @Test
    fun noAgc_isNotReported_andLeavesNoAgcPoints() {
        history.record(0, listOf(signal(1, 40f)))

        val trends = history.snapshot(0)
        assertFalse(trends.agcReported)
        assertTrue(trends.bands.single().agc.points.isEmpty())
    }

    @Test
    fun clear_forgetsEverything() {
        history.record(0, listOf(signal(1, 40f, agc = 30f)))
        history.clear()

        val trends = history.snapshot(0)
        assertTrue(trends.bands.isEmpty())
        assertTrue(trends.signals.isEmpty())
        assertFalse(trends.agcReported)
    }
}
