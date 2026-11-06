package com.craxiom.networksurvey.scout

import com.craxiom.networksurvey.board.v1.BoardInfo
import com.craxiom.networksurvey.board.v1.ConfigureResult
import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.board.v1.ControlRequest.CommandCase
import com.craxiom.networksurvey.board.v1.ControlResponse
import com.craxiom.networksurvey.board.v1.HelloResult
import com.craxiom.networksurvey.board.v1.ResultCode
import com.craxiom.networksurvey.scout.link.ScoutCapture
import com.craxiom.networksurvey.scout.link.ScoutCharacteristic
import com.craxiom.networksurvey.scout.link.ScoutClock
import com.craxiom.networksurvey.scout.link.ScoutFraming
import com.craxiom.networksurvey.scout.link.ScoutLinkException
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure
import com.craxiom.networksurvey.scout.link.ScoutProtocol
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the link controller does with a board's answers and with how links end: a new link stops a
 * board left surveying, a refused capture reads as an outage and is retried, a board left in its
 * OTA state is freed, key failures in a row read as Pairing lost, a link the app closed waits for
 * a fresh link, and a link lost before it became current still reconnects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScoutLinkControllerTest {
    private val scout = PairedScout("NS-02005E00ABCD", ADDRESS, "ABCD", 2, "", "0.1.0", 1, listOf(1, 2), 0)

    @Test
    fun aNewLinkWithNoCaptureWanted_stopsTheBoardOnce() = runTest {
        val link = Link(this)
        link.controller.connect(ADDRESS)
        runCurrent()
        assertEquals(SETUP + CommandCase.STOP, link.commands)

        link.controller.setCapture(null)
        runCurrent()
        assertEquals(SETUP + CommandCase.STOP, link.commands)
    }

    @Test
    fun aNewLinkWithACaptureWanted_configuresAndStartsWithoutAStop() = runTest {
        val link = Link(this)
        link.controller.setCapture(ScoutCapture())
        link.controller.connect(ADDRESS)
        runCurrent()
        assertEquals(SETUP + CommandCase.CONFIGURE + CommandCase.START, link.commands)
        assertTrue(link.state() is ScoutState.Connected)
    }

    @Test
    fun aRefusedCapture_readsAsAnOutageAndIsRetriedUntilTheBoardTakesIt() = runTest {
        val link = Link(this)
        link.answer = { if (it == CommandCase.START) ResultCode.RESULT_CODE_ERROR else ResultCode.RESULT_CODE_OK }
        link.controller.setCapture(ScoutCapture())
        link.controller.connect(ADDRESS)
        runCurrent()
        assertEquals(ScoutState.CantConnect(scout), link.state())
        assertFalse(CommandCase.OTA_ABORT in link.commands)

        // Still refused on the first retry, after the backoff's first second
        advanceTimeBy(FIRST_RETRY_MS)
        runCurrent()
        assertEquals(2, link.commands.count { it == CommandCase.START })
        assertEquals(ScoutState.CantConnect(scout), link.state())

        // The next wait is two seconds
        link.answer = { ResultCode.RESULT_CODE_OK }
        advanceTimeBy(2 * FIRST_RETRY_MS)
        runCurrent()
        assertEquals(3, link.commands.count { it == CommandCase.START })
        assertTrue(link.state() is ScoutState.Connected)
    }

    @Test
    fun aRefusedCaptureThatIsNoLongerWanted_endsTheOutage() = runTest {
        val link = Link(this)
        link.controller.connect(ADDRESS)
        runCurrent()
        link.answer = { if (it == CommandCase.CONFIGURE) ResultCode.RESULT_CODE_INVALID_ARGUMENT else ResultCode.RESULT_CODE_OK }
        link.controller.setCapture(ScoutCapture())
        runCurrent()
        assertEquals(ScoutState.CantConnect(scout), link.state())

        link.controller.setCapture(null)
        runCurrent()
        assertTrue(link.state() is ScoutState.Connected)
        val attempts = link.commands.count { it == CommandCase.CONFIGURE }
        advanceTimeBy(RETRIES_WOULD_HAVE_RUN_MS)
        runCurrent()
        assertEquals("No retry is left behind", attempts, link.commands.count { it == CommandCase.CONFIGURE })
    }

    @Test
    fun aBoardLeftInItsOtaState_isFreedAndTheCaptureThenApplies() = runTest {
        val link = Link(this)
        var inOta = true
        link.answer = { command ->
            if (command == CommandCase.OTA_ABORT) inOta = false
            val refused = inOta && (command == CommandCase.CONFIGURE || command == CommandCase.START)
            if (refused) ResultCode.RESULT_CODE_INVALID_STATE else ResultCode.RESULT_CODE_OK
        }
        link.controller.setCapture(ScoutCapture())
        link.controller.connect(ADDRESS)
        runCurrent()
        assertEquals(SETUP + CommandCase.CONFIGURE + CommandCase.OTA_ABORT, link.commands)

        advanceTimeBy(FIRST_RETRY_MS)
        runCurrent()
        assertEquals(SETUP + CommandCase.CONFIGURE + CommandCase.OTA_ABORT + CommandCase.CONFIGURE + CommandCase.START, link.commands)
        assertTrue(link.state() is ScoutState.Connected)
    }

    @Test
    fun withNoCaptureWanted_aPartialFirmwareImageIsLeftAlone() = runTest {
        val link = Link(this)
        link.controller.connect(ADDRESS)
        runCurrent()
        assertFalse("A later install can resume it", CommandCase.OTA_ABORT in link.commands)
    }

    @Test
    fun threeKeyFailuresInARow_readPairingLostAndStopRetrying() = runTest {
        // As seen with a board bonded to another phone: connected, then status 5, on every attempt
        val link = Link(this)
        link.openFailures.addAll(List(100) { ScoutLinkFailure.KEY_REJECTED })
        link.controller.connect(ADDRESS)
        runCurrent()
        assertEquals(ScoutState.Connecting(scout), link.state())

        advanceTimeBy(FRESH_LINK_GAP_MS)
        runCurrent()
        assertEquals(ScoutState.Connecting(scout), link.state())

        advanceTimeBy(FRESH_LINK_GAP_MS)
        runCurrent()
        assertEquals(listOf(0L, 5_000L, 10_000L), link.opens)
        assertEquals(ScoutState.PairingLost(scout), link.state())

        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(3, link.opens.size)
    }

    @Test
    fun keyFailuresBrokenUpByAnotherFailure_keepRetrying() = runTest {
        val link = Link(this)
        link.openFailures.addAll(
            listOf(
                ScoutLinkFailure.KEY_REJECTED, ScoutLinkFailure.KEY_REJECTED,
                // The Service Changed case fails like this, and so does a stray GATT error
                ScoutLinkFailure.GATT_ERROR,
                ScoutLinkFailure.KEY_REJECTED, ScoutLinkFailure.KEY_REJECTED,
            ),
        )
        link.controller.connect(ADDRESS)
        runCurrent()
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(6, link.opens.size)
        assertTrue(link.state().toString(), link.state() is ScoutState.Connected)
    }

    @Test
    fun aGoodLink_startsTheKeyFailureCountOver() = runTest {
        val link = Link(this)
        link.openFailures.addAll(listOf(ScoutLinkFailure.KEY_REJECTED, ScoutLinkFailure.KEY_REJECTED))
        link.controller.connect(ADDRESS)
        runCurrent()
        advanceTimeBy(2 * FRESH_LINK_GAP_MS)
        runCurrent()
        assertTrue(link.state() is ScoutState.Connected)

        link.openFailures.addAll(listOf(ScoutLinkFailure.KEY_REJECTED, ScoutLinkFailure.KEY_REJECTED))
        link.lastTransport.loseLink(ScoutLinkFailure.KEY_REJECTED)
        runCurrent()
        advanceTimeBy(2 * FRESH_LINK_GAP_MS)
        runCurrent()
        assertTrue(link.state().toString(), link.state() is ScoutState.Connected)
    }

    @Test
    fun aKeyFailureWhilePairing_saysTheBondIsStaleAtOnce() = runTest {
        val link = Link(this)
        link.openFailures.add(ScoutLinkFailure.KEY_REJECTED)
        val failure = runCatching { link.controller.pair(ADDRESS) }.exceptionOrNull() as ScoutLinkException
        assertEquals(ScoutLinkFailure.BOND_MISSING, failure.failure)
    }

    @Test
    fun aLinkLostBeforeItBecameCurrent_stillReconnects() = runTest {
        val link = Link(this)
        link.loseLinkAfter = CommandCase.SET_TIME
        link.controller.connect(ADDRESS)
        runCurrent()
        assertEquals("It reconnects instead of waiting on a dead link", 2, link.opens.size)
        assertTrue(link.state() is ScoutState.Connected)
    }

    @Test
    fun aLinkTheAppClosed_waitsForAFreshLink() = runTest {
        val link = Link(this)
        link.controller.connect(ADDRESS)
        runCurrent()

        link.controller.dropLink()
        runCurrent()
        advanceTimeBy(FRESH_LINK_GAP_MS - 1)
        runCurrent()
        assertEquals(1, link.opens.size)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(0L, FRESH_LINK_GAP_MS), link.opens)
    }

    @Test
    fun aCommandThatTimesOut_closesTheLinkAndWaitsForAFreshOne() = runTest {
        val link = Link(this)
        link.controller.connect(ADDRESS)
        runCurrent()
        link.silent = true
        link.controller.setCapture(ScoutCapture())
        runCurrent()
        advanceTimeBy(ScoutProtocol.CONTROL_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals("The fresh link gap is still running", 1, link.opens.size)

        link.silent = false
        advanceTimeBy(FRESH_LINK_GAP_MS)
        runCurrent()
        assertEquals(2, link.opens.size)
    }

    @Test
    fun aLinkTheBoardDropped_reconnectsAtOnce() = runTest {
        val link = Link(this)
        link.controller.connect(ADDRESS)
        runCurrent()
        link.lastTransport.loseLink(ScoutLinkFailure.LINK_LOST)
        runCurrent()
        assertEquals(listOf(0L, 0L), link.opens)
    }

    /** A controller and its link state over scripted boards, on the test clock. */
    private inner class Link(private val test: TestScope) {
        val opens = mutableListOf<Long>()
        val commands = mutableListOf<CommandCase>()

        /** What each control command is answered with. */
        var answer: (CommandCase) -> ResultCode = { ResultCode.RESULT_CODE_OK }

        /** True while the board doesn't answer control requests. */
        var silent = false

        /** How the next connect attempts fail, in order; empty means they succeed. */
        val openFailures = ArrayDeque<ScoutLinkFailure>()

        /** Loses the link right after answering this command, once. */
        var loseLinkAfter: CommandCase? = null
        lateinit var lastTransport: ScriptedBoard

        private val clock = object : ScoutClock {
            override fun wallMs(): Long = test.testScheduler.currentTime
            override fun elapsedMs(): Long = test.testScheduler.currentTime
        }
        private val linkState = ScoutLinkState(clock)
        val controller = ScoutLinkController(
            scope = test.backgroundScope,
            transportFactory = { ScriptedBoard().also { lastTransport = it } },
            appVersion = "2.0",
            clock = clock,
            events = linkState.events(onEvent = {}, listeners = emptyList(), scoutId = { null }),
        )

        fun state(): ScoutState = ScoutStateMapper.map(
            linkState.inputs(scout, WifiSource.SCOUT, hasPermission = true, bluetoothOn = true, capturing = false),
        )

        /** A board that answers at once, inside the write, so the tests need no response delays. */
        inner class ScriptedBoard : ScoutTransport {
            private var listener: ScoutTransportListener? = null
            private var sequence = 0
            override var reachedScout = false

            override suspend fun open(address: String, listener: ScoutTransportListener): Int {
                opens += clock.elapsedMs()
                reachedScout = true
                openFailures.removeFirstOrNull()?.let { throw ScoutLinkException(it, "scripted failure") }
                this.listener = listener
                return MTU
            }

            override suspend fun readInfo(): ByteArray =
                BoardInfo.newBuilder().setProtocolMajor(ScoutProtocol.APP_PROTOCOL_MAJOR).build().toByteArray()

            override suspend fun ensureBonded(allowPairing: Boolean) = Unit
            override suspend fun setNotify(characteristic: ScoutCharacteristic, enabled: Boolean) = Unit
            override suspend fun writeOta(chunk: ByteArray) = Unit

            override suspend fun writeControl(request: ByteArray) {
                val parsed = ControlRequest.parseFrom(request)
                commands += parsed.commandCase
                if (silent) return
                val response = ControlResponse.newBuilder().setRequestId(parsed.requestId).setResult(answer(parsed.commandCase))
                if (parsed.hasHello()) {
                    response.setHello(HelloResult.newBuilder().setProtocolMajor(ScoutProtocol.APP_PROTOCOL_MAJOR).setCompatible(true))
                }
                if (parsed.hasConfigure()) response.setConfigure(ConfigureResult.getDefaultInstance())
                val frame = ScoutFraming.encode(sequence++, listOf(response.build().toByteArray()))
                listener?.onNotification(ScoutCharacteristic.CONTROL, frame, clock.wallMs())
                if (parsed.commandCase == loseLinkAfter) {
                    loseLinkAfter = null
                    loseLink(ScoutLinkFailure.LINK_LOST)
                }
            }

            /** Ends the link from the board's side. */
            fun loseLink(failure: ScoutLinkFailure) {
                val owner = listener ?: return
                listener = null
                owner.onLinkLost(failure)
            }

            override fun close() {
                listener = null
            }
        }
    }

    private companion object {
        const val ADDRESS = "02:00:5E:00:AB:CD"
        const val MTU = 517

        /** The backoff's first wait, which a refused capture retries after. */
        const val FIRST_RETRY_MS = 1_000L

        /** Longer than the first several backoff waits together. */
        const val RETRIES_WOULD_HAVE_RUN_MS = 20_000L

        /** The gap after an attempt that reached the Scout or a link the app closed. */
        const val FRESH_LINK_GAP_MS = 5_000L

        /** What every compatible link says while it sets up. */
        val SETUP = listOf(CommandCase.HELLO, CommandCase.PING, CommandCase.SET_TIME)
    }
}
