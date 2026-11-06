package com.craxiom.networksurvey.scout.wifi

import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.scout.model.ScoutId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Board time to wall time, as on iOS. */
class BoardClockTest {

    private val clock = BoardClock()
    private val scout = ScoutId("NS-02005E00ABCD")

    @Test
    fun `a synced time is used as is, even for a backlog minutes old`() {
        val heard = RECEIVED - 5 * 60_000
        assertEquals(heard, clock.wallTimeMs(heard, synced = true, scout, RECEIVED))
    }

    @Test
    fun `a synced time far from the receive time is not trusted`() {
        val wrong = RECEIVED - 2 * BoardClock.DEFAULT_MAX_SKEW_MS
        assertEquals(RECEIVED, clock.wallTimeMs(wrong, synced = true, scout, RECEIVED))
    }

    @Test
    fun `an unsynced time uses the offset learned from the status, never the future`() {
        clock.noteStatus(BoardStatus.newBuilder().setBoardTimeMs(50_000).setTimeSynced(false).build(), scout, RECEIVED)

        assertEquals(RECEIVED - 10_000, clock.wallTimeMs(40_000, synced = false, scout, RECEIVED))
        assertEquals(RECEIVED, clock.wallTimeMs(60_000, synced = false, scout, RECEIVED))
    }

    @Test
    fun `without an offset or a time the receive time is used`() {
        assertEquals(RECEIVED, clock.wallTimeMs(40_000, synced = false, scout, RECEIVED))
        assertEquals(RECEIVED, clock.wallTimeMs(0, synced = true, scout, RECEIVED))
    }

    private companion object {
        const val RECEIVED = 1_792_000_000_000L
    }
}
