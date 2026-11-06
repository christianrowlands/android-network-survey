package com.craxiom.networksurvey.scout

import com.craxiom.networksurvey.board.v1.ApObservation
import com.craxiom.networksurvey.board.v1.BoardInfo
import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.board.v1.Record
import com.craxiom.networksurvey.board.v1.RecordBatch
import com.craxiom.networksurvey.scout.link.ReceivedRecordBatch
import com.craxiom.networksurvey.scout.link.ScoutCapture
import com.craxiom.networksurvey.scout.link.ScoutClock
import com.craxiom.networksurvey.scout.link.ScoutCompatibility
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure
import com.craxiom.networksurvey.scout.link.ScoutLinkInfo
import com.craxiom.networksurvey.scout.model.WifiSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The link state's details, its run of failed attempts, catch-up and refused captures. */
class ScoutLinkStateTest {
    private val clock = object : ScoutClock {
        override fun wallMs(): Long = 0
        override fun elapsedMs(): Long = 0
    }
    private val state = ScoutLinkState(clock)
    private val events = state.events(onEvent = {}, listeners = emptyList(), scoutId = { null })

    @Test
    fun aLinkTheAppClosed_dropsItsMtuAndStatus_butKeepsTheBoardInfo() {
        val info = BoardInfo.newBuilder().setFirmwareVersion("0.1.0").build()
        events.onPhase(ScoutLinkPhase.CONNECTING)
        events.onReady(ScoutLinkInfo(info, mtu = 517, hello = null, compatibility = ScoutCompatibility.COMPATIBLE))
        events.onPhase(ScoutLinkPhase.READY)
        events.onStatus(BoardStatus.newBuilder().setCurrentChannel(36).build())
        assertEquals(517, state.details.value.mtu)

        events.onPhase(ScoutLinkPhase.IDLE)
        assertEquals(0, state.details.value.mtu)
        assertNull(state.details.value.status)
        assertEquals(info, state.details.value.info)
    }

    @Test
    fun theRunOfFailedAttempts_countsOnlyThoseThatReachedTheScout_andEndsWhenTheLinkIsReadyOrIdle() {
        events.onPhase(ScoutLinkPhase.CONNECTING)
        repeat(2) { events.onAttemptFailed(reachedScout = true) }
        assertEquals(2, failed())
        events.onAttemptFailed(reachedScout = false)
        assertEquals(0, failed())
        repeat(3) { events.onAttemptFailed(reachedScout = true) }
        events.onPhase(ScoutLinkPhase.IDLE)
        assertEquals(0, failed())
        events.onPhase(ScoutLinkPhase.CONNECTING)
        events.onAttemptFailed(reachedScout = true)
        events.onReady(ScoutLinkInfo(BoardInfo.getDefaultInstance(), 517, null, ScoutCompatibility.COMPATIBLE))
        assertEquals(0, failed())
    }

    @Test
    fun catchUp_endsWhenTheCaptureStopsAndStaysOffWithoutOne() {
        events.onCaptureChanged(ScoutCapture(), null)
        events.onRecords(lateBatch())
        assertNotNull(catchUp())

        events.onCaptureChanged(null, null)
        assertNull(catchUp())

        // Stop's last batch can still arrive late; with no capture nothing would ever end it
        events.onRecords(lateBatch())
        assertNull(catchUp())
    }

    @Test
    fun catchUp_endsOnANewLink() {
        events.onCaptureChanged(ScoutCapture(), null)
        events.onRecords(lateBatch())
        assertNotNull(catchUp())

        events.onReady(ScoutLinkInfo(BoardInfo.getDefaultInstance(), 517, null, ScoutCompatibility.COMPATIBLE))
        assertNull(catchUp())
    }

    @Test
    fun aRefusedCapture_lastsUntilTheBoardTakesOneOrTheLinkEnds() {
        fun rejected() = state.inputs(null, WifiSource.SCOUT, hasPermission = true, bluetoothOn = true, capturing = false).captureRejected

        events.onCaptureRejected()
        assertTrue(rejected())
        events.onCaptureChanged(ScoutCapture(), null)
        assertFalse(rejected())

        events.onCaptureRejected()
        events.onLinkLost(ScoutLinkFailure.LINK_LOST)
        assertFalse(rejected())

        events.onCaptureRejected()
        events.onReady(ScoutLinkInfo(BoardInfo.getDefaultInstance(), 517, null, ScoutCompatibility.COMPATIBLE))
        assertFalse(rejected())
    }

    /** One record far older than its arrival, as a board's buffered backlog is. */
    private fun lateBatch(): ReceivedRecordBatch {
        val record = Record.newBuilder().setAp(ApObservation.newBuilder().setBoardTimeMs(1_000))
        val batch = RecordBatch.newBuilder().setTimeSynced(true).addRecords(record).build()
        return ReceivedRecordBatch(0, 0, batch, receivedAtMs = 120_000)
    }

    private fun catchUp() = state.inputs(null, WifiSource.SCOUT, hasPermission = true, bluetoothOn = true, capturing = true).catchUp

    private fun failed(): Int = state.inputs(null, WifiSource.SCOUT, hasPermission = true, bluetoothOn = true, capturing = false).failedAfterConnect
}
