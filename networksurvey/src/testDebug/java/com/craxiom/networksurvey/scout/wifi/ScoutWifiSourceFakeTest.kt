package com.craxiom.networksurvey.scout.wifi

import android.content.Context
import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.scout.ScoutEnvironment
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.ScoutStore
import com.craxiom.networksurvey.scout.TestScoutClock
import com.craxiom.networksurvey.scout.fake.FakeScout
import com.craxiom.networksurvey.scout.fake.FakeScoutTransport
import com.craxiom.networksurvey.scout.fake.ScoutFixtures
import com.craxiom.networksurvey.scout.model.WifiSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The Scout Wi-Fi source end to end on the debug fake Scout: it makes the board capture, turns
 * its records into windowed records and live snapshots named after the Scout, and sends Stop when
 * it stops or pauses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScoutWifiSourceFakeTest {
    private val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("scout-wifi-source-test", Context.MODE_PRIVATE)
    private val fixtures = ScoutFixtures.fromClasspath()
    private val transports = ArrayList<FakeScoutTransport>()
    private val bond = FakeScoutTransport.Bond(bonded = false)
    private val windows = ArrayList<List<ScoutBeacon>>()
    private val snapshots = ArrayList<List<ScoutBeacon>>()
    private var liveViews = true

    private object Environment : ScoutEnvironment {
        override fun isBluetoothOn() = true
        override fun hasConnectPermission() = true
        override fun watchBluetooth(onChange: () -> Unit) = Unit
    }

    private val host = object : ScoutWifiHost {
        override fun scanRateMs() = SCAN_RATE_MS
        override fun hasLiveViews() = liveViews
        override fun liveViewIntervalMs() = 5_000
        override fun surveyStartMs(): Long? = null
        override fun onScoutBeacons(beacons: List<ScoutBeacon>) {
            windows.add(beacons)
        }

        override fun onScoutSnapshot(beacons: List<ScoutBeacon>) {
            snapshots.add(beacons)
        }
    }

    private fun TestScope.settle(ms: Long = 500) {
        runCurrent()
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun TestScope.pairedSource(): Pair<ScoutManager, ScoutWifiSource> {
        val clock = TestScoutClock(testScheduler)
        val manager = ScoutManager(
            store = ScoutStore(preferences),
            environment = Environment,
            dispatcher = StandardTestDispatcher(testScheduler),
            transportFactory = { FakeScoutTransport(backgroundScope, fixtures, FakeScoutTransport.Behavior(), clock, bond).also { transports.add(it) } },
            scannerFactory = { onSightings, _ -> FakeScout.Scanner(onSightings) { testScheduler.currentTime } },
            clock = clock,
            appVersion = "2.0-test",
            parentJob = backgroundScope.coroutineContext[Job],
        )
        manager.startNearbyScan()
        settle()
        manager.pair(manager.nearbyScouts.value.single())
        settle(1_500)
        assertEquals(WifiSource.SCOUT, manager.wifiSource.value)
        val source = ScoutWifiSource(manager, host, null, InlineScheduledExecutor(), clock::wallMs, clock::elapsedMs)
        return manager to source
    }

    @Test
    fun startingMakesTheBoardCaptureAndFeedsSnapshotsAndWindows() = runTest {
        val (_, source) = pairedSource()

        source.start()
        settle(3_000)
        assertTrue(transports.last().isStarted)

        source.tickNow()
        assertTrue("No live snapshot", snapshots.isNotEmpty())
        val snapshot = snapshots.last()
        assertTrue(snapshot.all { it.scoutName == "NS-ABCD" && !it.late })

        // Records reach the survey's sinks only once their window closes
        assertTrue(windows.isEmpty())
        settle(SCAN_RATE_MS + 3_000L)
        source.tickNow()
        assertTrue("No window of records", windows.isNotEmpty())
        val keys = windows.first().map { it.observation.bssid }
        assertEquals("One record per access point per window", keys.size, keys.toSet().size)

        source.stop()
        settle()
        assertEquals(ControlRequest.CommandCase.STOP, transports.last().requests.last().commandCase)
        assertFalse(transports.last().isStarted)
    }

    @Test
    fun aPauseSendsStopAndHandsOverWhatIsHeldAndResumeCapturesAgain() = runTest {
        val (_, source) = pairedSource()
        liveViews = false
        source.start()
        settle(3_000)

        source.pause()
        settle()
        assertFalse(transports.last().isStarted)
        assertTrue("What was held reaches the sinks at the pause", windows.isNotEmpty())

        source.resume()
        settle(1_000)
        assertTrue(transports.last().isStarted)
        source.stop()
        settle()
    }

    @Test
    fun aLeavingSinkTakesTheOpenWindowSoFarAndTheWindowStillCloses() = runTest {
        val (_, source) = pairedSource()
        liveViews = false
        source.start()
        settle(3_000)
        assertTrue("The window is still open", windows.isEmpty())

        val peeked = ArrayList<List<ScoutBeacon>>()
        source.peekPending { peeked.add(it) }
        assertTrue("The leaving sink gets what was heard so far", peeked.isNotEmpty())

        source.stop()
        settle()
        assertTrue("The window still reaches the sinks that stay", windows.isNotEmpty())
    }

    @Test
    fun stoppingSavesTheOpenWindow() = runTest {
        val (_, source) = pairedSource()
        liveViews = false
        source.start()
        settle(3_000)
        assertTrue(windows.isEmpty())

        source.stop()
        settle()

        assertTrue("The open window is saved at the stop", windows.isNotEmpty())
        val keys = windows.flatten().map { it.observation.bssid }
        assertEquals("One record per access point", keys.size, keys.toSet().size)
    }

    private companion object {
        const val SCAN_RATE_MS = 15_000
    }
}
