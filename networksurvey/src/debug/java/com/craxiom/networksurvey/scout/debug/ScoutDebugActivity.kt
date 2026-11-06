package com.craxiom.networksurvey.scout.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.fake.FakeScout
import com.craxiom.networksurvey.scout.link.ScoutCapture
import com.craxiom.networksurvey.scout.model.NearbyScout
import com.craxiom.networksurvey.scout.model.ScoutId
import com.craxiom.networksurvey.scout.model.ScoutPairingState
import com.craxiom.networksurvey.scout.model.ScoutRawFrames
import com.craxiom.networksurvey.scout.model.ScoutRecordListener
import com.craxiom.networksurvey.scout.model.ScoutRecords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/**
 * Debug-only hardware check for the Scout link layer, driven from adb (no UI beyond a log view):
 *
 * `adb shell am start -n com.craxiom.networksurvey.dev/com.craxiom.networksurvey.scout.debug.ScoutDebugActivity --es cmd scan`
 *
 * Commands: `run` (the whole check: wait for a pairable Scout, pair, capture 60 s, Stop, reconnect;
 * optional `--es id A1B2 --el waitms 600000`),
 * `scan`, `stopscan`, `pair` (optional `--es id ABCD`, else the strongest Scout),
 * `link`, `capture` (optional `--ez clients true --ez probes true --ez raw true`), `stopcapture`,
 * `release`, `remove`, and `fake`: makes the process use the fake Scout (`FakeScout`) instead of the
 * radio. `fake` must be the first command after the app process starts (`am force-stop` first),
 * before the survey service or a screen creates the real manager; then `scan` and `pair` pair the
 * fake and make it the Wi-Fi source. Everything is logged under the tag [TAG], including decoded
 * record counts.
 * Lives in src/debug, so release builds never contain it.
 */
class ScoutDebugActivity : Activity() {
    private lateinit var view: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = TextView(this).apply { text = getString(android.R.string.untitled) }
        setContentView(view)
        // Android throttles BLE scans hard with the screen off (no results in 30 s on a Pixel 6),
        // so the check keeps it on while it runs.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (intent?.getStringExtra(EXTRA_COMMAND) == CMD_FAKE) {
            val installed = ScoutManager.installIfAbsent(FakeScout.createManager(this))
            log(if (installed) "fake Scout installed" else "a Scout manager already exists; force-stop the app and run fake first")
        }
        Monitor.start(ScoutManager.getInstance(this))
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val command = intent?.getStringExtra(EXTRA_COMMAND) ?: return
        val manager = ScoutManager.getInstance(this)
        log("command $command")
        view.text = command
        when (command) {
            CMD_SCAN -> manager.startNearbyScan()
            CMD_STOP_SCAN -> manager.stopNearbyScan()
            CMD_PAIR -> {
                val id = intent.getStringExtra(EXTRA_ID)
                val nearby = manager.nearbyScouts.value.firstOrNull { id == null || it.shortId.equals(id, ignoreCase = true) }
                if (nearby == null) log("no nearby Scout to pair; run scan first") else manager.pair(nearby)
            }

            CMD_LINK -> manager.requestLink(Monitor.linkOwner)
            CMD_CAPTURE -> manager.requestCapture(
                Monitor.captureOwner,
                ScoutCapture(
                    wifiClients = intent.getBooleanExtra(EXTRA_CLIENTS, false),
                    wifiProbes = intent.getBooleanExtra(EXTRA_PROBES, false),
                    rawFrames = intent.getBooleanExtra(EXTRA_RAW, false),
                ),
            )

            CMD_STOP_CAPTURE -> manager.release(Monitor.captureOwner)
            CMD_RELEASE -> {
                manager.release(Monitor.captureOwner)
                manager.release(Monitor.linkOwner)
            }

            CMD_REMOVE -> manager.removePairedScout()
            CMD_RUN -> Monitor.run(manager, intent.getStringExtra(EXTRA_ID), intent.getLongExtra(EXTRA_WAIT_MS, RUN_SCAN_TIMEOUT_MS))
            CMD_FAKE -> Unit
            else -> log("unknown command $command")
        }
    }

    /** Logs every Scout flow and counts decoded records, once per process. */
    private object Monitor : ScoutRecordListener {
        val linkOwner = Any()
        val captureOwner = Any()
        private var started = false
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val batches = AtomicLong()
        private val accessPoints = AtomicLong()
        private val clients = AtomicLong()
        private val probes = AtomicLong()
        private val rawFrames = AtomicLong()
        private val missed = AtomicLong()
        private val statuses = AtomicLong()
        @Volatile
        private var lastStatus: BoardStatus? = null

        @Synchronized
        fun start(manager: ScoutManager) {
            if (started) return
            started = true
            manager.addRecordListener(this)
            manager.state.onEach { log("state $it") }.launchIn(scope)
            manager.pairing.onEach { log("pairing $it") }.launchIn(scope)
            manager.wifiSource.onEach { log("wifi source $it") }.launchIn(scope)
            manager.nearbyScouts.onEach { list ->
                log("nearby ${list.size}: " + list.joinToString { "${it.name} ${it.address} ${it.rssi} dBm ${it.status} ${it.advertisement}" })
            }.launchIn(scope)
            manager.linkDetails.onEach { details ->
                details.info?.let { log("info fw=${it.firmwareVersion} model=${it.boardModel} bands=${it.bandsList} mtu=${details.mtu} capturing=${details.capturing} configured=${details.configured?.channelsList}") }
            }.launchIn(scope)
            scope.launch {
                while (true) {
                    delay(SUMMARY_INTERVAL_MS)
                    val status = lastStatus
                    log(
                        "records batches=${batches.get()} ap=${accessPoints.get()} clients=${clients.get()} probes=${probes.get()} " +
                            "raw=${rawFrames.get()} missedNotifications=${missed.get()} statuses=${statuses.get()} " +
                            "status=${status?.let { "state=${it.state} ch=${it.currentChannel} rx/s=${it.rxFramesPerSec} rec/s=${it.recordsPerSec} ring=${it.ringFillPercent}% drops=${it.droppedRecords}/${it.droppedRawFrames}/${it.droppedRxFrames} mtu=${it.mtu} bonds=${it.bondCount} battery=${if (it.hasBatteryMv()) it.batteryMv else "none"}" }}",
                    )
                }
            }
        }

        /**
         * The whole check in one go: scan until [id] (or any Scout) advertises as pairable, for up
         * to [waitMs], pair, connect, capture for [RUN_CAPTURE_MS] and Stop, then let go of the
         * link and take it again (a reconnect with the stored bond, no pairing prompt), capture
         * briefly once more, and let go.
         */
        fun run(manager: ScoutManager, id: String?, waitMs: Long) {
            scope.launch {
                log("run: scanning up to ${waitMs / 1000} s for a pairable Scout")
                manager.startNearbyScan()
                val nearby = withTimeoutOrNull(waitMs) {
                    manager.nearbyScouts.first { list -> list.any { matches(it, id) } }.first { matches(it, id) }
                }
                if (nearby == null) {
                    log("run: no pairable Scout heard; hold BOOT for 3 s and run again")
                    manager.stopNearbyScan()
                    return@launch
                }
                log("run: pairing ${nearby.name} ${nearby.status} ${nearby.advertisement}")
                manager.requestLink(linkOwner)
                manager.pair(nearby)
                val result = manager.pairing.first { it is ScoutPairingState.Paired || it is ScoutPairingState.Failed }
                log("run: pairing result $result")
                if (result is ScoutPairingState.Failed) {
                    manager.release(linkOwner)
                    return@launch
                }
                capture(manager, RUN_CAPTURE_MS, "first link")

                log("run: reconnect test, letting go of the link")
                manager.release(linkOwner)
                manager.state.first { !it.isUsable }
                delay(RUN_STOP_SETTLE_MS)
                log("run: state while released ${manager.state.value}")
                manager.requestLink(linkOwner)
                val back = withTimeoutOrNull(RUN_RECONNECT_TIMEOUT_MS) { manager.state.first { it.isUsable } }
                log("run: reconnected=${back != null} state ${manager.state.value}")
                if (back != null) capture(manager, RUN_SECOND_CAPTURE_MS, "after reconnect")
                manager.release(linkOwner)
                delay(RUN_STOP_SETTLE_MS)
                log("run: finished, state ${manager.state.value}")
            }
        }

        private suspend fun capture(manager: ScoutManager, durationMs: Long, label: String) {
            manager.state.first { it.isUsable }
            log("run: $label connected, starting capture")
            manager.requestCapture(captureOwner, ScoutCapture(wifiClients = true, wifiProbes = true))
            delay(durationMs)
            log("run: $label capture done, stopping")
            manager.release(captureOwner)
            delay(RUN_STOP_SETTLE_MS)
            val details = manager.linkDetails.value
            log("run: $label after Stop capturing=${details.capturing} board state=${details.status?.state} drops=${details.drops}")
        }

        private fun matches(nearby: NearbyScout, id: String?): Boolean =
            (id == null || nearby.shortId.equals(id, ignoreCase = true)) && nearby.advertisement.pairable

        override fun onScoutRecords(records: ScoutRecords) {
            batches.incrementAndGet()
            accessPoints.addAndGet(records.accessPoints.size.toLong())
            clients.addAndGet(records.clients.size.toLong())
            probes.addAndGet(records.probes.size.toLong())
            missed.addAndGet(records.missedNotifications.toLong())
            records.accessPoints.firstOrNull()?.takeIf { batches.get() % SAMPLE_EVERY == 1L }?.let {
                log("sample ap ssid=${it.ssid.toStringUtf8()} ch=${it.channel} rssi=${it.rssiMax} sec=${it.security} synced=${records.timeSynced} age=${records.receivedAtMs - it.boardTimeMs} ms")
            }
        }

        override fun onScoutRawFrames(frames: ScoutRawFrames) {
            rawFrames.addAndGet(frames.frames.size.toLong())
        }

        override fun onScoutStatus(scoutId: ScoutId, status: BoardStatus) {
            statuses.incrementAndGet()
            lastStatus = status
        }
    }

    private companion object {
        const val TAG = "ScoutDebug"
        const val EXTRA_COMMAND = "cmd"
        const val EXTRA_ID = "id"
        const val EXTRA_CLIENTS = "clients"
        const val EXTRA_PROBES = "probes"
        const val EXTRA_RAW = "raw"
        const val CMD_SCAN = "scan"
        const val CMD_STOP_SCAN = "stopscan"
        const val CMD_PAIR = "pair"
        const val CMD_LINK = "link"
        const val CMD_CAPTURE = "capture"
        const val CMD_STOP_CAPTURE = "stopcapture"
        const val CMD_RELEASE = "release"
        const val CMD_REMOVE = "remove"
        const val CMD_RUN = "run"
        const val CMD_FAKE = "fake"
        const val EXTRA_WAIT_MS = "waitms"
        const val RUN_SCAN_TIMEOUT_MS = 30_000L
        const val RUN_RECONNECT_TIMEOUT_MS = 30_000L
        const val RUN_SECOND_CAPTURE_MS = 15_000L
        const val RUN_CAPTURE_MS = 60_000L
        const val RUN_STOP_SETTLE_MS = 3_000L
        const val SUMMARY_INTERVAL_MS = 5_000L
        const val SAMPLE_EVERY = 20L

        fun log(message: String) {
            Log.i(TAG, message)
        }
    }
}
