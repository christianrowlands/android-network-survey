package com.craxiom.networksurvey.scout

import android.content.Context
import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.board.v1.ResultCode
import com.craxiom.networksurvey.scout.fake.FakeScout
import com.craxiom.networksurvey.scout.fake.FakeScoutTransport
import com.craxiom.networksurvey.scout.fake.ScoutFixtures
import com.craxiom.networksurvey.scout.firmware.FirmwareTestData
import com.craxiom.networksurvey.scout.firmware.FirmwareUpdateRun
import com.craxiom.networksurvey.scout.link.ScoutCapture
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure
import com.craxiom.networksurvey.scout.model.NearbyScoutStatus
import com.craxiom.networksurvey.scout.model.ScoutPairingState
import com.craxiom.networksurvey.scout.model.ScoutRecordListener
import com.craxiom.networksurvey.scout.model.ScoutRecords
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The Scout manager end to end on the debug fake: pairing from the nearby list, demand-driven
 * connect, capture and Stop, records to listeners, reconnects, the connect grace and the
 * environment states.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScoutManagerFakeTest {
    private val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("scout-manager-test", Context.MODE_PRIVATE)
    private val fixtures = ScoutFixtures.fromClasspath()
    private val transports = ArrayList<FakeScoutTransport>()
    private val bond = FakeScoutTransport.Bond(bonded = false)
    private var behavior = FakeScoutTransport.Behavior()
    private val environment = TestEnvironment()
    private val received = ArrayList<ScoutRecords>()
    private val surveyOwner = Any()
    private val pageOwner = Any()

    private class TestEnvironment : ScoutEnvironment {
        var bluetoothOn = true
        var permission = true
        var onChange: () -> Unit = {}
        override fun isBluetoothOn() = bluetoothOn
        override fun hasConnectPermission() = permission
        override fun watchBluetooth(onChange: () -> Unit) {
            this.onChange = onChange
        }
    }

    private fun TestScope.manager(): ScoutManager {
        val clock = TestScoutClock(testScheduler)
        return ScoutManager(
            store = ScoutStore(preferences),
            environment = environment,
            dispatcher = StandardTestDispatcher(testScheduler),
            transportFactory = { FakeScoutTransport(backgroundScope, fixtures, behavior, clock, bond).also { transports.add(it) } },
            scannerFactory = { onSightings, _ -> FakeScout.Scanner(onSightings) { testScheduler.currentTime } },
            clock = clock,
            appVersion = "2.0-test",
            parentJob = backgroundScope.coroutineContext[Job],
        ).also { it.addRecordListener(listener) }
    }

    private val listener = object : ScoutRecordListener {
        override fun onScoutRecords(records: ScoutRecords) {
            received.add(records)
        }
    }

    private fun TestScope.settle(ms: Long = 500) {
        runCurrent()
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun TestScope.pairedManager(): ScoutManager {
        val manager = manager()
        manager.requestLink(pageOwner)
        manager.startNearbyScan()
        settle()
        manager.pair(manager.nearbyScouts.value.single())
        settle(1_500)
        return manager
    }

    @Test
    fun forgettingTheScout_forgetsAFailedFirmwarePackage() = runTest {
        val manager = pairedManager()
        val forAnotherBoard = FirmwareTestData.releasePackage(FirmwareTestData.image(), boardModel = "ANOTHER_BOARD")
        manager.firmwareUpdater.install(forAnotherBoard)
        settle(1_000)
        assertTrue(manager.firmwareUpdater.state.value is FirmwareUpdateRun.Failed)

        manager.removePairedScout()
        settle()

        assertEquals(FirmwareUpdateRun.Idle, manager.firmwareUpdater.state.value)
    }

    @Test
    fun aReleaseFollowedAtOnceByANewCapture_endsUpConnectedAndCapturingOnTheNewLink() = runTest {
        val manager = pairedManager()
        manager.release(pageOwner)
        manager.requestCapture(surveyOwner, ScoutCapture())
        settle(500)
        manager.release(surveyOwner)
        // The pause and resume of a survey: the old link's Stop must not close the new link
        manager.requestCapture(surveyOwner, ScoutCapture())
        settle(5_000)

        assertTrue(manager.state.value is ScoutState.Connected)
        assertTrue("The newest link captures", transports.last().isStarted)
        assertEquals("Only the newest link is open", 1, transports.count { !it.isClosed })
    }

    @Test
    fun pairingWithNoOneHoldingTheLink_leavesNoLinkOpen() = runTest {
        // Nobody asked for the link, so the manager lets go as soon as pairing finishes; the
        // established link must be closed, not abandoned
        val manager = manager()
        manager.startNearbyScan()
        settle()
        manager.pair(manager.nearbyScouts.value.single())
        settle(5_000)

        assertTrue(transports.isNotEmpty())
        assertTrue("Every link is closed", transports.all { it.isClosed })
    }

    @Test
    fun withoutAScout_itIsNotPairedAndNeverConnects() = runTest {
        val manager = manager()
        manager.requestLink(pageOwner)
        settle()
        assertEquals(ScoutState.NotPaired, manager.state.value)
        assertEquals(WifiSource.PHONE, manager.wifiSource.value)
        assertTrue(transports.isEmpty())
    }

    @Test
    fun pairing_fromTheNearbyListMakesTheScoutTheWifiSource() = runTest {
        val manager = manager()
        manager.startNearbyScan()
        settle()
        val nearby = manager.nearbyScouts.value.single()
        assertEquals(NearbyScoutStatus.READY_TO_PAIR, nearby.status)

        manager.requestLink(pageOwner)
        manager.pair(nearby)
        settle(1_500)

        val paired = (manager.pairing.value as ScoutPairingState.Paired).scout
        assertEquals("NS-02005E00ABCD", paired.id)
        assertEquals(paired, manager.pairedScout.value)
        assertEquals(WifiSource.SCOUT, manager.wifiSource.value)
        assertTrue(manager.state.value is ScoutState.Connected)
        assertEquals(paired, ScoutStore(preferences).pairedScout())
    }

    @Test
    fun pairing_refusedLeavesNoScout() = runTest {
        behavior = FakeScoutTransport.Behavior(acceptPairing = false)
        val manager = manager()
        manager.startNearbyScan()
        settle()
        manager.pair(manager.nearbyScouts.value.single())
        settle()
        assertEquals(ScoutPairingState.Failed("NS-ABCD", ScoutLinkFailure.PAIRING_REJECTED), manager.pairing.value)
        assertNull(manager.pairedScout.value)
        assertEquals(ScoutState.NotPaired, manager.state.value)
    }

    @Test
    fun capture_startsTheBoardDeliversWifiRecordsAndStopsOnRelease() = runTest {
        val manager = pairedManager()
        manager.requestCapture(surveyOwner, ScoutCapture())
        settle(2_000)

        val fake = transports.last()
        assertTrue(fake.isStarted)
        assertTrue((manager.state.value as ScoutState.Connected).capturing)
        assertTrue(manager.linkDetails.value.configured?.channelsCount ?: 0 > 0)
        assertTrue(received.isNotEmpty())
        assertTrue(received.all { it.scoutId.value == "NS-02005E00ABCD" })
        assertTrue(received.any { it.accessPoints.isNotEmpty() } && received.any { it.probes.isNotEmpty() })

        manager.release(surveyOwner)
        settle()
        assertEquals(ControlRequest.CommandCase.STOP, fake.requests.last().commandCase)
        assertTrue(manager.state.value is ScoutState.Connected)

        manager.release(pageOwner)
        // The link outlives its last owner briefly, for the next Scout screen
        settle(ScoutLinkLinger.LINGER_MS + 500)
        assertEquals(ScoutState.NotConnected(manager.pairedScout.value!!), manager.state.value)
    }

    @Test
    fun aDroppedLinkReconnectsAndKeepsCapturing() = runTest {
        val manager = pairedManager()
        manager.requestCapture(surveyOwner, ScoutCapture())
        settle()
        val first = transports.last()

        first.dropLink()
        runCurrent()
        assertTrue(manager.state.value is ScoutState.Connecting)
        settle(1_000)

        val second = transports.last()
        assertTrue(second !== first)
        assertTrue(second.isStarted)
        assertTrue(manager.state.value is ScoutState.Connected)
    }

    @Test
    fun aScoutThatStaysAwayReadsOutOfRangeAfterTheGrace() = runTest {
        val manager = pairedManager()
        behavior = FakeScoutTransport.Behavior(openFailure = ScoutLinkFailure.LINK_LOST)
        transports.last().dropLink()
        settle(10_000)
        assertTrue(manager.state.value is ScoutState.Connecting)

        settle(21_000)
        assertTrue(manager.state.value is ScoutState.OutOfRange)
        val attempts = transports.size

        // Back in range: the next backoff attempt connects.
        behavior = FakeScoutTransport.Behavior()
        settle(31_000)
        assertTrue(transports.size > attempts)
        assertTrue(manager.state.value is ScoutState.Connected)
    }

    @Test
    fun aMissingBondOnReconnectIsPairingLostAndStopsRetrying() = runTest {
        val manager = pairedManager()
        bond.bonded = false
        transports.last().dropLink()
        settle(5_000)
        assertTrue(manager.state.value is ScoutState.PairingLost)
        val attempts = transports.size
        settle(60_000)
        assertEquals(attempts, transports.size)
    }

    @Test
    fun aNoBondAdvertisementFromThePairedAddress_isNotPairingLost() = runTest {
        // The fake scanner advertises no bond at the paired address, as anyone nearby could
        val manager = pairedManager()
        behavior = FakeScoutTransport.Behavior(openFailure = ScoutLinkFailure.LINK_LOST)
        transports.last().dropLink()
        settle(1_000)
        manager.startNearbyScan()
        settle(1_000)
        assertEquals(NearbyScoutStatus.WAS_RESET, manager.nearbyScouts.value.single().status)
        assertTrue(manager.state.value.toString(), manager.state.value is ScoutState.Connecting)

        behavior = FakeScoutTransport.Behavior()
        settle(31_000)
        assertTrue(manager.state.value.toString(), manager.state.value is ScoutState.Connected)
    }

    @Test
    fun erasingPairings_forgetsTheScoutOnceTheBoardSaysOk() = runTest {
        val manager = pairedManager()
        var result: Boolean? = null
        manager.erasePairingsOnScout { result = it }
        settle()

        assertEquals(true, result)
        assertNull(manager.pairedScout.value)
        assertNull(ScoutStore(preferences).pairedScout())
    }

    @Test
    fun erasingPairings_keepsTheScoutWhenTheBoardRefuses() = runTest {
        behavior = FakeScoutTransport.Behavior(
            refused = mapOf(ControlRequest.CommandCase.FORGET_BONDS to ResultCode.RESULT_CODE_ERROR),
        )
        val manager = pairedManager()
        var result: Boolean? = null
        manager.erasePairingsOnScout { result = it }
        settle()

        assertEquals(false, result)
        assertTrue(manager.pairedScout.value != null)
        assertTrue(ScoutStore(preferences).pairedScout() != null)
    }

    @Test
    fun environmentAndSourceStates() = runTest {
        val manager = pairedManager()

        environment.bluetoothOn = false
        environment.onChange()
        settle()
        assertTrue(manager.state.value is ScoutState.BluetoothOff)

        environment.bluetoothOn = true
        environment.permission = false
        manager.refreshEnvironment()
        settle()
        assertTrue(manager.state.value is ScoutState.PermissionMissing)

        environment.permission = true
        manager.setWifiSource(WifiSource.PHONE)
        settle()
        assertTrue(manager.state.value is ScoutState.NotInUse)
        assertEquals(WifiSource.PHONE, ScoutStore(preferences).wifiSource())

        manager.removePairedScout()
        settle()
        assertEquals(ScoutState.NotPaired, manager.state.value)
        assertNull(ScoutStore(preferences).pairedScout())
    }
}
