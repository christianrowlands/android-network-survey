package com.craxiom.networksurvey.scout.fake

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.board.v1.BoardModel
import com.craxiom.networksurvey.scout.ScoutConstants
import com.craxiom.networksurvey.scout.ScoutEnvironment
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.ScoutStore
import com.craxiom.networksurvey.scout.link.ScoutAdvertisement
import com.craxiom.networksurvey.scout.link.ScoutNearbyScanner
import com.craxiom.networksurvey.scout.link.ScoutProtocol
import com.craxiom.networksurvey.scout.link.ScoutSighting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.asCoroutineDispatcher

/**
 * Debug-only wiring for a Scout that does not exist: a [ScoutManager] on [FakeScoutTransport] and
 * a scanner that always hears one ready-to-pair Scout, for development without hardware and for
 * tests. Release builds never contain it; the app's real manager comes from
 * `ScoutManager.getInstance`.
 */
object FakeScout {
    /** The fake board's address; its Info says bt_address 02:00:5E:00:AB:CD, short ID ABCD. */
    const val ADDRESS = "02:00:5E:00:AB:CD"

    /** The fake board's short ID. */
    const val SHORT_ID = "ABCD"

    private const val RSSI = -52
    private const val NAME_SUFFIX = "-fake"

    /** @return The advertisement of a new C5 Scout that is ready to pair. */
    @JvmStatic
    fun advertisement(): ScoutAdvertisement = ScoutAdvertisement(
        protocolMajor = ScoutProtocol.APP_PROTOCOL_MAJOR,
        pairable = true,
        hasBond = false,
        model = BoardModel.BOARD_MODEL_ESP32_C5_DEVKITC_1,
    )

    /** A scanner that hears the fake Scout as soon as it starts. */
    class Scanner(
        private val onSightings: (List<ScoutSighting>) -> Unit,
        private val nowElapsedMs: () -> Long,
    ) : ScoutNearbyScanner {
        override fun start(): Boolean {
            onSightings(listOf(ScoutSighting(ADDRESS, SHORT_ID, RSSI, advertisement(), nowElapsedMs())))
            return true
        }

        override fun stop() = onSightings(emptyList())
    }

    /** An environment where Bluetooth is on and the permission granted. */
    class Environment : ScoutEnvironment {
        override fun isBluetoothOn(): Boolean = true
        override fun hasConnectPermission(): Boolean = true
        override fun watchBluetooth(onChange: () -> Unit) = Unit
    }

    /**
     * @return A manager on its own thread that pairs with and streams from the fake Scout, storing
     * its choices in the default preferences (as the real one does).
     */
    @JvmStatic
    fun createManager(context: Context): ScoutManager {
        val thread = HandlerThread(ScoutConstants.THREAD_NAME + NAME_SUFFIX).apply { start() }
        val dispatcher = Handler(thread.looper).asCoroutineDispatcher()
        val scope = CoroutineScope(dispatcher)
        val fixtures = ScoutFixtures.fromAssets(context.applicationContext)
        val bond = FakeScoutTransport.Bond(bonded = false)
        return ScoutManager(
            store = ScoutStore(PreferenceManager.getDefaultSharedPreferences(context.applicationContext)),
            environment = Environment(),
            dispatcher = dispatcher,
            transportFactory = { FakeScoutTransport(scope, fixtures, bond = bond) },
            scannerFactory = { onSightings, _ -> Scanner(onSightings) { SystemClock.elapsedRealtime() } },
        )
    }
}
