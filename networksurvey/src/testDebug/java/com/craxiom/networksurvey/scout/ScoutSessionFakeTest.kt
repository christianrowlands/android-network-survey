package com.craxiom.networksurvey.scout

import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.board.v1.OtaAck
import com.craxiom.networksurvey.board.v1.Record
import com.craxiom.networksurvey.scout.fake.FakeScoutTransport
import com.craxiom.networksurvey.scout.fake.ScoutFixtures
import com.craxiom.networksurvey.scout.link.ReceivedRawFrames
import com.craxiom.networksurvey.scout.link.ReceivedRecordBatch
import com.craxiom.networksurvey.scout.link.ScoutCapture
import com.craxiom.networksurvey.scout.link.ScoutClock
import com.craxiom.networksurvey.scout.link.ScoutCompatibility
import com.craxiom.networksurvey.scout.link.ScoutLinkException
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure
import com.craxiom.networksurvey.scout.link.ScoutSession
import com.craxiom.networksurvey.scout.link.ScoutSessionListener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The session's Android connection order and capture commands against the debug fake Scout,
 * which replays the firmware fixtures through the real framing and decoder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScoutSessionFakeTest {
    private val fixtures = ScoutFixtures.fromClasspath()
    private val batches = ArrayList<ReceivedRecordBatch>()
    private val raw = ArrayList<ReceivedRawFrames>()
    private val statuses = ArrayList<BoardStatus>()
    private var lost: ScoutLinkFailure? = null

    private val listener = object : ScoutSessionListener {
        override fun onRecords(batch: ReceivedRecordBatch) {
            batches.add(batch)
        }

        override fun onRawFrames(frames: ReceivedRawFrames) {
            raw.add(frames)
        }

        override fun onStatus(status: BoardStatus) {
            statuses.add(status)
        }

        override fun onOtaAck(ack: OtaAck) = Unit

        override fun onLinkLost(failure: ScoutLinkFailure) {
            lost = failure
        }
    }

    private fun TestScope.clock() = TestScoutClock(testScheduler)

    private fun TestScope.fake(behavior: FakeScoutTransport.Behavior = FakeScoutTransport.Behavior()) =
        // The fake's loops never end, so they run in backgroundScope, which runTest cancels at the end.
        FakeScoutTransport(backgroundScope, fixtures, behavior, clock())

    private suspend fun TestScope.establish(session: ScoutSession, allowPairing: Boolean = true) =
        async { runCatching { session.establish(ADDRESS, allowPairing) } }.also { runCurrent(); advanceTimeBy(500) }.await()

    @Test
    fun establish_pairsThenSaysHelloPingsAndSetsTheTime() = runTest {
        val fake = fake()
        val session = ScoutSession(fake, "2.0", listener, clock())

        val info = establish(session).getOrThrow()

        assertEquals("ABCD", info.info.shortId)
        assertEquals(517, info.mtu)
        assertEquals(ScoutCompatibility.COMPATIBLE, info.compatibility)
        assertEquals("0.1.0", info.firmwareVersion)
        assertEquals(
            listOf(ControlRequest.CommandCase.HELLO, ControlRequest.CommandCase.PING, ControlRequest.CommandCase.SET_TIME),
            fake.requests.map { it.commandCase },
        )
        assertEquals("2.0", fake.requests[0].hello.appVersion)
        assertTrue(fake.requests[2].setTime.unixTimeMs >= TestScoutClock.BASE_WALL_MS)
    }

    @Test
    fun establish_withoutABondAndWithoutPairingIsAStaleBond() = runTest {
        val result = establish(ScoutSession(fake(), "2.0", listener, clock()), allowPairing = false)
        assertEquals(ScoutLinkFailure.BOND_MISSING, (result.exceptionOrNull() as ScoutLinkException).failure)
    }

    @Test
    fun establish_refusedPairingFails() = runTest {
        val session = ScoutSession(fake(FakeScoutTransport.Behavior(acceptPairing = false)), "2.0", listener, clock())
        assertEquals(ScoutLinkFailure.PAIRING_REJECTED, (establish(session).exceptionOrNull() as ScoutLinkException).failure)
    }

    @Test
    fun establish_anOlderProtocolStopsAfterControlForAFirmwareUpdate() = runTest {
        val fake = fake(FakeScoutTransport.Behavior(protocolMajor = 0))
        val session = ScoutSession(fake, "2.0", listener, clock())

        val info = establish(session).getOrThrow()

        assertEquals(ScoutCompatibility.NEEDS_FIRMWARE_UPDATE, info.compatibility)
        assertNull(info.hello)
        assertTrue(fake.requests.isEmpty())
        assertTrue(runCatching { session.applyCapture(ScoutCapture()) }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun capture_configuresStartsStreamsAndStops() = runTest {
        val fake = fake()
        val session = ScoutSession(fake, "2.0", listener, clock())
        establish(session).getOrThrow()

        val apply = async { session.applyCapture(ScoutCapture()) }
        runCurrent()
        advanceTimeBy(200)
        apply.await()
        assertTrue(fake.isStarted)
        assertEquals(FakeScoutTransport.DEFAULT_CHANNELS, session.configureResult?.channelsList)
        val commands = fake.requests.map { it.commandCase }.takeLast(2)
        assertEquals(listOf(ControlRequest.CommandCase.CONFIGURE, ControlRequest.CommandCase.START), commands)
        assertFalse(fake.requests.last { it.hasConfigure() }.configure.ble)

        advanceTimeBy(1_600)
        runCurrent()
        assertTrue(batches.size >= 5)
        val kinds = batches.flatMap { batch -> batch.batch.recordsList.map { it.kindCase } }.toSet()
        assertTrue(kinds.containsAll(listOf(Record.KindCase.AP, Record.KindCase.CLIENT, Record.KindCase.PROBE)))
        assertEquals(0, batches.sumOf { it.missedNotifications })
        assertTrue(statuses.isNotEmpty())
        assertTrue(raw.isEmpty())

        val stop = async { session.applyCapture(null) }
        runCurrent()
        advanceTimeBy(200)
        stop.await()
        assertFalse(fake.isStarted)
        assertEquals(ControlRequest.CommandCase.STOP, fake.requests.last().commandCase)
    }

    @Test
    fun capture_subscribesToRawOnlyWhenRawFramesAreOn() = runTest {
        val session = ScoutSession(fake(), "2.0", listener, clock())
        establish(session).getOrThrow()

        val apply = async { session.applyCapture(ScoutCapture(rawFrames = true)) }
        runCurrent()
        advanceTimeBy(1_000)
        apply.await()
        runCurrent()
        assertTrue(raw.isNotEmpty())
        assertEquals(0x80.toByte(), raw.first().frames.single().data.byteAt(0))
    }

    @Test
    fun missedRecordsFrames_areCountedFromTheSequenceNumbers() = runTest {
        val session = ScoutSession(fake(FakeScoutTransport.Behavior(skipEveryNthRecordsFrame = 3)), "2.0", listener, clock())
        establish(session).getOrThrow()
        val apply = async { session.applyCapture(ScoutCapture()) }
        runCurrent()
        advanceTimeBy(3_000)
        apply.await()
        assertTrue(batches.sumOf { it.missedNotifications } > 0)
    }

    @Test
    fun aNewLink_stopsTheBoardOnceEvenWithNoCaptureOfItsOwn() = runTest {
        val fake = fake()
        val session = ScoutSession(fake, "2.0", listener, clock())
        establish(session).getOrThrow()
        assertFalse(session.isApplied(null))

        val stop = async { session.applyCapture(null) }
        runCurrent()
        advanceTimeBy(200)
        stop.await()
        assertEquals(ControlRequest.CommandCase.STOP, fake.requests.last().commandCase)
        assertTrue(session.isApplied(null))

        val sent = fake.requests.size
        session.applyCapture(null)
        assertEquals(sent, fake.requests.size)
    }

    @Test
    fun aDroppedLinkIsReportedOnceAfterSetup() = runTest {
        val fake = fake()
        val session = ScoutSession(fake, "2.0", listener, clock())
        establish(session).getOrThrow()
        fake.dropLink()
        assertEquals(ScoutLinkFailure.LINK_LOST, lost)
    }

    private companion object {
        const val ADDRESS = "02:00:5E:00:AB:CD"
    }
}

/** A [ScoutClock] on the test scheduler's virtual time. */
class TestScoutClock(private val scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) : ScoutClock {
    override fun wallMs(): Long = BASE_WALL_MS + scheduler.currentTime
    override fun elapsedMs(): Long = scheduler.currentTime

    companion object {
        /** 2026-11-02, so restamped records read as live. */
        const val BASE_WALL_MS = 1_793_577_600_000L
    }
}
