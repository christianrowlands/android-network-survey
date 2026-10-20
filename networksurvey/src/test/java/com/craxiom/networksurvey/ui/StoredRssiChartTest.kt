package com.craxiom.networksurvey.ui

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The details pages' chart store: one chart, seeded once, plotted, released. */
@RunWith(RobolectricTestRunner::class)
class StoredRssiChartTest {

    @Test
    fun theChart_isSeededOnce_andShowsTheLatestReading() {
        val stored = StoredRssiChart()

        stored.start(-52f)
        stored.start(-90f)
        assertEquals(-52f, stored.chart.rssiFlow.value)

        stored.plot(-61f)
        assertEquals(-61f, stored.chart.rssiFlow.value)

        stored.setPaused(true)
        stored.clear()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
