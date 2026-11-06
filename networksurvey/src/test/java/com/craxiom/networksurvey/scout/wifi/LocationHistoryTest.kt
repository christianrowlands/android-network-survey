package com.craxiom.networksurvey.scout.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The phone fix nearest a capture time, and none past the gap. */
class LocationHistoryTest {

    private fun fix(timeMs: Long) = PhoneFix(timeMs, 1.0, 2.0, null, null, null)

    @Test
    fun `finds the fix nearest in time, before or after`() {
        val history = LocationHistory()
        listOf(1_000L, 2_000L, 4_000L).forEach { history.append(fix(it)) }

        assertEquals(1_000L, history.nearest(0)?.timeMs)
        assertEquals(2_000L, history.nearest(2_900)?.timeMs)
        assertEquals(4_000L, history.nearest(3_100)?.timeMs)
        assertEquals(4_000L, history.nearest(9_000)?.timeMs)
    }

    @Test
    fun `out of order fixes are kept in time order`() {
        val history = LocationHistory()
        listOf(1_000L, 3_000L, 2_000L).forEach { history.append(fix(it)) }

        assertEquals(2_000L, history.nearest(2_100)?.timeMs)
        assertEquals(3_000L, history.nearest(Long.MAX_VALUE)?.timeMs)
    }

    @Test
    fun `no fix is used past the gap`() {
        val history = LocationHistory()
        history.append(fix(1_000))

        assertEquals(1_000L, history.nearestWithin(31_000, 30_000)?.timeMs)
        assertNull(history.nearestWithin(31_001, 30_000))
        assertNull(LocationHistory().nearestWithin(0, 30_000))
    }

    @Test
    fun `old fixes are trimmed past the capacity`() {
        val history = LocationHistory(capacity = 8)
        for (time in 1L..20L) history.append(fix(time))

        assertEquals(20L, history.nearest(Long.MAX_VALUE)?.timeMs)
        assertEquals(true, history.size <= 9)
    }
}
