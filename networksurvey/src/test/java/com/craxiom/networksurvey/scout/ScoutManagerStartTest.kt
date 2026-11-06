package com.craxiom.networksurvey.scout

import android.content.Context
import com.craxiom.networksurvey.scout.link.ScoutCharacteristic
import com.craxiom.networksurvey.scout.link.ScoutNearbyScanner
import com.craxiom.networksurvey.scout.link.ScoutTransport
import com.craxiom.networksurvey.scout.link.ScoutTransportListener
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.coroutines.CoroutineContext

/**
 * A phone that never paired a Scout gets no Scout work: nothing runs on the Scout thread and no
 * Bluetooth receiver is registered until a Scout is paired, the pairing scan starts, or something
 * asks for a link or a capture. Also the 3 s a link outlives its last owner.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScoutManagerStartTest {
    private val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("scout-manager-start-test", Context.MODE_PRIVATE)
    private val store = ScoutStore(preferences)
    private val parent = Job()
    private var watchers = 0
    private var scanners = 0
    private var dispatches = 0
    private var opens = 0
    private var firmware: Any? = null

    /** A link attempt that never finishes, so the manager stays Connecting. */
    private inner class NeverConnects : ScoutTransport {
        override suspend fun open(address: String, listener: ScoutTransportListener): Int {
            opens++
            awaitCancellation()
        }

        override suspend fun readInfo(): ByteArray = error("not reached")
        override suspend fun ensureBonded(allowPairing: Boolean) = error("not reached")
        override suspend fun setNotify(characteristic: ScoutCharacteristic, enabled: Boolean) = error("not reached")
        override suspend fun writeControl(request: ByteArray) = error("not reached")
        override suspend fun writeOta(chunk: ByteArray) = error("not reached")
        override fun close() = Unit
    }

    @After
    fun tearDown() {
        parent.cancel()
        preferences.edit().clear().commit()
    }

    @Test
    fun withoutAPairedScout_nothingStarts_andStateOnlyCallsStayNoOps() = runTest {
        val manager = manager(this)
        manager.refreshEnvironment()
        manager.release(Any())
        manager.setWifiSource(WifiSource.PHONE)
        manager.stopNearbyScan()
        manager.clearPairingResult()
        runCurrent()

        assertFalse(manager.isStarted)
        assertEquals(0, watchers)
        assertEquals(0, scanners)
        assertEquals(0, dispatches)
        assertEquals(ScoutState.NotPaired, manager.state.value)
        assertEquals(WifiSource.PHONE, manager.wifiSource.value)
    }

    @Test
    fun thePairingScan_startsTheManagerOnce() = runTest {
        val manager = manager(this)
        manager.startNearbyScan()
        manager.startNearbyScan()
        runCurrent()

        assertTrue(manager.isStarted)
        assertEquals(1, watchers)
        assertEquals(1, scanners)
        assertTrue(manager.nearbyScanActive.value)
    }

    @Test
    fun aLinkRequest_startsTheManager() = runTest {
        val manager = manager(this)
        manager.requestLink(Any())
        runCurrent()

        assertTrue(manager.isStarted)
        assertEquals(1, watchers)
        assertEquals(ScoutState.NotPaired, manager.state.value)
    }

    @Test
    fun aPairedScout_startsTheManagerAtOnce() = runTest {
        store.replacePairedScout(PairedScout("NS-02005E00ABCD", "02:00:5E:00:AB:CD", "ABCD", 2, "", "0.1.0", 1, listOf(1, 2), 0))
        store.setWifiSource(WifiSource.SCOUT)
        val manager = manager(this)
        runCurrent()

        assertTrue(manager.isStarted)
        assertEquals(1, watchers)
        assertTrue(manager.state.value is ScoutState.NotConnected)
    }

    @Test
    fun theScoutThread_startsOnFirstUse() {
        val thread = ScoutThread("NS-Scout-test")
        assertFalse(thread.isStarted)
        thread.handler
        assertTrue(thread.isStarted)
        thread.handler.looper.quitSafely()
    }

    @Test
    fun aLinkOutlivesItsLastOwnerByThreeSeconds_soTheNextScreenKeepsIt() = runTest {
        store.replacePairedScout(PairedScout("NS-02005E00ABCD", "02:00:5E:00:AB:CD", "ABCD", 2, "", "0.1.0", 1, listOf(1, 2), 0))
        store.setWifiSource(WifiSource.SCOUT)
        val manager = manager(this, countDispatches = false)
        val page = Any()
        manager.requestLink(page)
        runCurrent()
        assertTrue(manager.state.value is ScoutState.Connecting)

        // The page leaves and the firmware screen asks within the linger: the same attempt goes on
        manager.release(page)
        advanceTimeBy(ScoutLinkLinger.LINGER_MS - 1)
        runCurrent()
        assertTrue(manager.state.value is ScoutState.Connecting)
        manager.requestLink(Any().also { firmware = it })
        runCurrent()
        assertEquals(1, opens)

        // The last owner leaves for good: the link closes after the linger
        manager.release(firmware!!)
        advanceTimeBy(ScoutLinkLinger.LINGER_MS + 1)
        runCurrent()
        assertTrue(manager.state.value.toString(), manager.state.value is ScoutState.NotConnected)
        assertEquals(1, opens)
    }

    /** @param countDispatches Counts every dispatch; the counting wrapper has no virtual-time delay. */
    private fun manager(test: TestScope, countDispatches: Boolean = true): ScoutManager {
        val target = StandardTestDispatcher(test.testScheduler)
        val counting = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                dispatches++
                target.dispatch(context, block)
            }
        }
        return ScoutManager(
            store = store,
            environment = object : ScoutEnvironment {
                override fun isBluetoothOn(): Boolean = true
                override fun hasConnectPermission(): Boolean = true
                override fun watchBluetooth(onChange: () -> Unit) {
                    watchers++
                }
            },
            dispatcher = if (countDispatches) counting else target,
            transportFactory = { NeverConnects() },
            scannerFactory = { _, _ ->
                scanners++
                object : ScoutNearbyScanner {
                    override fun start(): Boolean = true
                    override fun stop() = Unit
                }
            },
            parentJob = parent,
        )
    }
}
