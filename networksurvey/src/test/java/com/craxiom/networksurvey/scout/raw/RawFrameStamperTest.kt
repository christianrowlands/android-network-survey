package com.craxiom.networksurvey.scout.raw

import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.board.v1.RawFrame
import com.craxiom.networksurvey.scout.model.ScoutRawFrames
import com.google.protobuf.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Capture times for raw frames (synced, unsynced, before the start) and the snaplen guard. */
class RawFrameStamperTest {
    private val scout = RawFrameTestData.SCOUT_A

    private fun raw(vararg frames: RawFrame, receivedAtMs: Long = RECEIVED_MS) =
        ScoutRawFrames(scout, receivedAtMs, frames.toList(), missedNotifications = 0)

    private fun frame(boardTimeUs: Long, synced: Boolean) =
        RawFrameTestData.LEGACY.toBuilder().setBoardTimeUs(boardTimeUs).setTimeSynced(synced).build()

    @Test
    fun `a synced frame keeps its board time to the microsecond`() {
        val stamper = RawFrameStamper().apply { start(START_MS) }
        val stamped = stamper.stamp(raw(frame(RECEIVED_MS * 1000 - 250_123, synced = true)), snaplen = 0).single()
        assertEquals(RECEIVED_MS * 1000 - 250_123, stamped.timestampUs)
        assertEquals(scout, stamped.scoutId)
    }

    @Test
    fun `an unsynced frame is placed from the board's status`() {
        val stamper = RawFrameStamper().apply { start(START_MS) }
        // The board had been up 60 s when its status arrived at RECEIVED_MS - 1 s
        stamper.onStatus(scout, BoardStatus.newBuilder().setBoardTimeMs(60_000).setTimeSynced(false).build(), RECEIVED_MS - 1_000)
        val stamped = stamper.stamp(raw(frame(60_500_042, synced = false)), snaplen = 0).single()
        assertEquals((RECEIVED_MS - 500) * 1000 + 42, stamped.timestampUs)
    }

    @Test
    fun `without a board time the receive time is used`() {
        val stamper = RawFrameStamper().apply { start(START_MS) }
        assertEquals(RECEIVED_MS * 1000, stamper.stamp(raw(frame(0, synced = true)), snaplen = 0).single().timestampUs)
    }

    @Test
    fun `frames heard before the start are dropped`() {
        val stamper = RawFrameStamper().apply { start(RECEIVED_MS) }
        val before = frame((RECEIVED_MS - 2) * 1000, synced = true)
        val after = frame((RECEIVED_MS + 1) * 1000, synced = true)
        val stamped = stamper.stamp(raw(before, after, receivedAtMs = RECEIVED_MS + 5), snaplen = 0)
        assertEquals(listOf((RECEIVED_MS + 1) * 1000), stamped.map { it.timestampUs })
    }

    @Test
    fun `a frame longer than the effective snaplen is cut, keeping its length on air`() {
        val long = RawFrameTestData.LEGACY.toBuilder().setData(ByteString.copyFrom(ByteArray(300))).setOrigLen(300).build()
        val cut = RawFrameStamper.withinSnaplen(long, 256)
        assertEquals(256, cut.data.size())
        assertEquals(300, cut.origLen)
        assertSame(long, RawFrameStamper.withinSnaplen(long, 0))
        assertSame(long, RawFrameStamper.withinSnaplen(long, 512))
        assertTrue(RawFrameStamper.withinSnaplen(long.toBuilder().setOrigLen(0).build(), 100).origLen == 300)
    }

    private companion object {
        const val START_MS = 1_792_000_000_000L
        const val RECEIVED_MS = 1_792_000_100_000L
    }
}
