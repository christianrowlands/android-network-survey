package com.craxiom.networksurvey.scout

import com.craxiom.networksurvey.scout.link.ScoutCharacteristic
import com.craxiom.networksurvey.scout.link.ScoutClock
import com.craxiom.networksurvey.scout.link.ScoutLinkException
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure
import com.craxiom.networksurvey.scout.link.ScoutTransport
import com.craxiom.networksurvey.scout.link.ScoutTransportListener
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reconnect attempts that reach the Scout and then fail (the link comes up, the Info read fails
 * with status 133) read "Can't connect" after three in a row instead of "Out of range", and wait
 * long enough for Android to close the failed link before the next attempt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScoutReconnectTest {
    private val scout = PairedScout("NS-02005E00ABCD", ADDRESS, "ABCD", 2, "", "0.1.0", 1, listOf(1, 2), 0)

    @Test
    fun attemptsThatReachTheScoutAndFail_readCantConnectAfterThree_fiveSecondsApart() = runTest {
        val link = Link(this, reaches = true)
        link.controller.connect(ADDRESS)
        runCurrent()
        assertEquals(listOf(0L), link.opens)
        assertEquals(ScoutState.Connecting(scout), link.state())

        advanceTimeBy(ATTEMPT_GAP_MS)
        runCurrent()
        assertEquals(ScoutState.Connecting(scout), link.state())

        advanceTimeBy(ATTEMPT_GAP_MS)
        runCurrent()
        assertEquals(listOf(0L, 5_000L, 10_000L), link.opens)
        assertEquals(ScoutState.CantConnect(scout), link.state())

        // Reconnects go on; the backoff takes over once it is longer than the gap
        advanceTimeBy(ATTEMPT_GAP_MS + 8_000L)
        runCurrent()
        assertEquals(listOf(0L, 5_000L, 10_000L, 15_000L, 23_000L), link.opens)
        assertEquals(ScoutState.CantConnect(scout), link.state())
    }

    @Test
    fun attemptsThatNeverReachTheScout_keepTheBackoff_andReadOutOfRangeAfterTheGrace() = runTest {
        val link = Link(this, reaches = false)
        link.controller.connect(ADDRESS)
        runCurrent()
        advanceTimeBy(7_000L)
        runCurrent()
        assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L), link.opens)
        assertEquals(ScoutState.Connecting(scout), link.state())

        advanceTimeBy(ScoutStateMapper.CONNECT_GRACE_MS)
        runCurrent()
        assertEquals(ScoutState.OutOfRange(scout, batteryMayBeEmpty = false), link.state())
    }

    @Test
    fun anAttemptThatNeverReachesTheScout_endsTheRun() = runTest {
        val link = Link(this, reaches = true)
        link.controller.connect(ADDRESS)
        runCurrent()
        advanceTimeBy(ATTEMPT_GAP_MS)
        runCurrent()
        link.reaches = false
        advanceTimeBy(ATTEMPT_GAP_MS)
        runCurrent()
        link.reaches = true
        advanceTimeBy(4_000L)
        runCurrent()
        // Reached, reached, not reached, reached: never three in a row
        assertEquals(4, link.opens.size)
        assertEquals(ScoutState.Connecting(scout), link.state())
    }

    /** A controller and its link state over transports that always fail, on the test clock. */
    private inner class Link(private val test: TestScope, var reaches: Boolean) {
        val opens = mutableListOf<Long>()
        private val clock = object : ScoutClock {
            override fun wallMs(): Long = test.testScheduler.currentTime
            override fun elapsedMs(): Long = test.testScheduler.currentTime
        }
        private val linkState = ScoutLinkState(clock)
        val controller = ScoutLinkController(
            scope = test.backgroundScope,
            transportFactory = { FailingTransport() },
            appVersion = "2.0",
            clock = clock,
            events = linkState.events(onEvent = {}, listeners = emptyList(), scoutId = { null }),
        )

        fun state(): ScoutState = ScoutStateMapper.map(
            linkState.inputs(scout, WifiSource.SCOUT, hasPermission = true, bluetoothOn = true, capturing = false),
        )

        /** Connects (or not, by [reaches]) and then fails the Info read as the stuck board did. */
        private inner class FailingTransport : ScoutTransport {
            override var reachedScout = false

            override suspend fun open(address: String, listener: ScoutTransportListener): Int {
                opens += clock.elapsedMs()
                if (!reaches) throw ScoutLinkException(ScoutLinkFailure.TIMEOUT, "connect timed out")
                reachedScout = true
                return STUCK_MTU
            }

            override suspend fun readInfo(): ByteArray =
                throw ScoutLinkException(ScoutLinkFailure.GATT_ERROR, "READ failed with status 133")

            override suspend fun ensureBonded(allowPairing: Boolean) = error("not reached")
            override suspend fun setNotify(characteristic: ScoutCharacteristic, enabled: Boolean) = error("not reached")
            override suspend fun writeControl(request: ByteArray) = error("not reached")
            override suspend fun writeOta(chunk: ByteArray) = error("not reached")
            override fun close() = Unit
        }
    }

    private companion object {
        const val ADDRESS = "02:00:5E:00:AB:CD"

        /** The phone's MTU when the stuck reconnect hit. */
        const val STUCK_MTU = 23

        /** The gap after an attempt that reached the Scout (longer than Android's 4 s idle link). */
        const val ATTEMPT_GAP_MS = 5_000L
    }
}
