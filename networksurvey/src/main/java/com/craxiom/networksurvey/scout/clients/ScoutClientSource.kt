package com.craxiom.networksurvey.scout.clients

import android.os.SystemClock
import com.craxiom.networksurvey.GpsListener
import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.board.v1.ClientObservation
import com.craxiom.networksurvey.board.v1.ProbeObservation
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.link.ScoutCapture
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutId
import com.craxiom.networksurvey.scout.model.ScoutRecords
import com.craxiom.networksurvey.scout.wifi.PhoneFix
import com.craxiom.networksurvey.scout.wifi.ScoutCaptureHost
import com.craxiom.networksurvey.scout.wifi.ScoutCaptureSource
import com.craxiom.networksurvey.scout.wifi.ScoutHeard
import com.craxiom.networksurvey.scout.wifi.ScoutWifiPipeline
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor

/**
 * What the Scout Wi-Fi clients source needs from its owner. Called on the source's own thread.
 */
interface ScoutClientHost : ScoutCaptureHost {
    /** One window of client observations for the survey's sinks. */
    fun onScoutClients(clients: List<ScoutHeard<ClientObservation>>)

    /** One window of probe request observations for the survey's sinks. */
    fun onScoutProbes(probes: List<ScoutHeard<ProbeObservation>>)

    /** Every client heard recently, for the live client screens only. */
    fun onScoutClientSnapshot(snapshot: ScoutClientSnapshot)

    /** @return True when [ssid] is on the user's exclusion list. May be called on any thread. */
    fun isExcludedSsid(ssid: String): Boolean
}

/**
 * The paired NS Scout's Wi-Fi clients and probe requests, through [ScoutClientPipeline] on the
 * shared [ScoutCaptureSource] lifecycle. It runs while something wants Wi-Fi clients (a survey
 * sink or a live client screen) and the Scout is the Wi-Fi source, and asks the board for access
 * points too, so the live screens can name the network a client talks to and the pipeline can leave
 * out clients of an excluded network.
 */
class ScoutClientSource @JvmOverloads constructor(
    manager: ScoutManager,
    private val host: ScoutClientHost,
    gpsListener: GpsListener?,
    executor: ScheduledExecutorService = ScheduledThreadPoolExecutor(1).apply { setRemoveOnCancelPolicy(true) },
    wallClock: () -> Long = System::currentTimeMillis,
    elapsedClock: () -> Long = SystemClock::elapsedRealtime,
) : ScoutCaptureSource(manager, host, gpsListener, CAPTURE, executor, wallClock, elapsedClock) {
    private val pipeline = ScoutClientPipeline(host::isExcludedSsid)

    override val logName: String = LOG_NAME

    /** True between [start] and [stop]. */
    val isRunning: Boolean get() = running

    /**
     * Hands [onClients] and [onProbes] what the open windows hold so far, without draining them,
     * on the calling thread, for a sink about to leave while others stay. Does nothing when not
     * running or paused (a pause already handed everything over).
     */
    fun peekPending(onClients: (List<ScoutHeard<ClientObservation>>) -> Unit, onProbes: (List<ScoutHeard<ProbeObservation>>) -> Unit) {
        if (!running || paused) return
        val output = pipeline.peekAll(settings())
        output.clients.forEach(onClients)
        output.probes.forEach(onProbes)
    }

    override fun onCaptureStart(scout: PairedScout?, nowMs: Long) = pipeline.start(scout, nowMs)

    override fun onScout(scout: PairedScout?) = pipeline.setScout(scout)

    /** Access points too: they say which BSSIDs belong to an excluded SSID. */
    override fun wants(records: ScoutRecords): Boolean =
        records.accessPoints.isNotEmpty() || records.clients.isNotEmpty() || records.probes.isNotEmpty()

    override fun onBatch(records: ScoutRecords, windowMs: Long) = pipeline.onRecords(records, windowMs)

    override fun onBoardStatus(scoutId: ScoutId, status: BoardStatus, receivedAtMs: Long) =
        pipeline.onStatus(scoutId, status, receivedAtMs)

    override fun onPhoneFix(fix: PhoneFix) = pipeline.onFix(fix)

    override fun onTick(nowMs: Long, settings: ScoutWifiPipeline.Settings) {
        val output = pipeline.tick(nowMs, settings)
        deliver(output)
        output.snapshot?.let(host::onScoutClientSnapshot)
    }

    override fun onDrain(settings: ScoutWifiPipeline.Settings) = deliver(pipeline.drainAll(settings))

    override fun onClear() = pipeline.clear()

    private fun deliver(output: ScoutClientPipeline.Output) {
        output.clients.forEach(host::onScoutClients)
        output.probes.forEach(host::onScoutProbes)
    }

    private companion object {
        val CAPTURE = ScoutCapture(wifiAccessPoints = true, wifiClients = true, wifiProbes = true)

        const val LOG_NAME = "Wi-Fi clients"
    }
}
