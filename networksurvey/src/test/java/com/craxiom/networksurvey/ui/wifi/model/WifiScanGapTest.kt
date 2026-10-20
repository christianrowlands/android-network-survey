package com.craxiom.networksurvey.ui.wifi.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The gap between live Wi-Fi scans that the Android 9 and 10 throttling check measures. */
class WifiScanGapTest {

    private var now = 0L
    private val gap = WifiScanGap { now }

    @Test
    fun theFirstScan_measuresNothing_andTheNextMeasuresTheTimeBetween() {
        assertNull(gap.measure())
        now = 15_000
        assertEquals(15_000L, gap.measure())
        now = 20_000
        assertEquals(5_000L, gap.measure())
    }

    @Test
    fun aReset_startsTheGapOver() {
        gap.measure()
        now = 90_000
        gap.reset()

        assertNull(gap.measure())
        now = 105_000
        assertEquals(15_000L, gap.measure())
    }
}
