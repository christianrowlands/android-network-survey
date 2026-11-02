package com.craxiom.networksurvey.services.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telling Android's cached cell list apart from a new measurement, per SIM. */
class CellInfoFreshnessTest {

    private val freshness = CellInfoFreshness()

    @Test
    fun theFirstList_isFresh() {
        assertFalse(freshness.isRepeat(SIM_1, longArrayOf(100, 200)))
    }

    @Test
    fun theSameTimestamps_inAnyOrder_areARepeat() {
        freshness.isRepeat(SIM_1, longArrayOf(100, 200))

        assertTrue(freshness.isRepeat(SIM_1, longArrayOf(200, 100)))
    }

    @Test
    fun aNewTimestampOrADifferentCellCount_isFresh() {
        freshness.isRepeat(SIM_1, longArrayOf(100, 200))
        assertFalse(freshness.isRepeat(SIM_1, longArrayOf(100, 300)))
        assertFalse(freshness.isRepeat(SIM_1, longArrayOf(100, 300, 300)))
    }

    @Test
    fun anEmptyList_isNeverARepeat() {
        // An empty scan says the phone sees no cells, which the screens and sinks still need.
        freshness.isRepeat(SIM_1, longArrayOf())
        assertFalse(freshness.isRepeat(SIM_1, longArrayOf()))
    }

    @Test
    fun eachSim_isComparedWithItsOwnLastList() {
        freshness.isRepeat(SIM_1, longArrayOf(100))

        assertFalse(freshness.isRepeat(SIM_2, longArrayOf(100)))
        assertTrue(freshness.isRepeat(SIM_1, longArrayOf(100)))
    }

    @Test
    fun aReset_letsTheNextListThrough() {
        freshness.isRepeat(SIM_1, longArrayOf(100))
        freshness.reset()

        assertFalse(freshness.isRepeat(SIM_1, longArrayOf(100)))
    }

    @Test
    fun theNewestPreviousTimestamp_isReported() {
        assertNull(freshness.newestPrevious(SIM_1))

        freshness.isRepeat(SIM_1, longArrayOf(300, 100))
        assertEquals(300L, freshness.newestPrevious(SIM_1))
    }

    private companion object {
        const val SIM_1 = 1
        const val SIM_2 = 2
    }
}
