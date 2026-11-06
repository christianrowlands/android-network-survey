package com.craxiom.networksurvey.scout.wifi

import android.os.SystemClock
import com.craxiom.networksurvey.GpsListener
import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.link.ScoutCapture
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutId
import com.craxiom.networksurvey.scout.model.ScoutRecords
import com.craxiom.networksurvey.services.controller.WifiScanSource
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.function.Consumer

/**
 * What the Scout Wi-Fi source needs from the Wi-Fi controller and the survey service.
 * Called on the Scout Wi-Fi thread.
 */
interface ScoutWifiHost : ScoutCaptureHost {
    /** One window of records for the survey's sinks (log files, streams, uploads, watchlist). */
    fun onScoutBeacons(beacons: List<ScoutBeacon>)

    /** Every access point heard in the last sweep, for the live Wi-Fi screens only. */
    fun onScoutSnapshot(beacons: List<ScoutBeacon>)
}

/**
 * The paired NS Scout as a [WifiScanSource]: access points through [ScoutWifiPipeline], on the
 * shared [ScoutCaptureSource] lifecycle.
 *
 * Without a live view, scanning faster never applies to the Scout: it listens all the time, and
 * the scan interval only sets how often each network is saved.
 */
class ScoutWifiSource @JvmOverloads constructor(
    manager: ScoutManager,
    private val host: ScoutWifiHost,
    gpsListener: GpsListener?,
    executor: ScheduledExecutorService = ScheduledThreadPoolExecutor(1).apply { setRemoveOnCancelPolicy(true) },
    wallClock: () -> Long = System::currentTimeMillis,
    elapsedClock: () -> Long = SystemClock::elapsedRealtime,
) : ScoutCaptureSource(manager, host, gpsListener, CAPTURE, executor, wallClock, elapsedClock), WifiScanSource {
    private val pipeline = ScoutWifiPipeline()

    override val logName: String = LOG_NAME

    override fun start() = super<ScoutCaptureSource>.start()

    override fun stop() = super<ScoutCaptureSource>.stop()

    override fun pause() = super<ScoutCaptureSource>.pause()

    override fun resume() = super<ScoutCaptureSource>.resume()

    override fun destroy() = super<ScoutCaptureSource>.destroy()

    /**
     * Gives [sink] what the open windows hold so far, without draining them, on the calling thread
     * and without waiting for any other. Does nothing when not running or paused (a pause already
     * handed everything over).
     */
    override fun peekPending(sink: Consumer<List<ScoutBeacon>>) {
        if (!running || paused) return
        pipeline.peekAll(settings()).forEach { sink.accept(it) }
    }

    override fun onCaptureStart(scout: PairedScout?, nowMs: Long) = pipeline.start(scout, nowMs)

    override fun onScout(scout: PairedScout?) = pipeline.setScout(scout)

    override fun wants(records: ScoutRecords): Boolean = records.accessPoints.isNotEmpty()

    override fun onBatch(records: ScoutRecords, windowMs: Long) = pipeline.onRecords(records, windowMs)

    override fun onBoardStatus(scoutId: ScoutId, status: BoardStatus, receivedAtMs: Long) =
        pipeline.onStatus(scoutId, status, receivedAtMs)

    override fun onPhoneFix(fix: PhoneFix) = pipeline.onFix(fix)

    override fun onTick(nowMs: Long, settings: ScoutWifiPipeline.Settings) {
        val output = pipeline.tick(nowMs, settings)
        output.windows.forEach(host::onScoutBeacons)
        output.snapshot?.let(host::onScoutSnapshot)
    }

    override fun onDrain(settings: ScoutWifiPipeline.Settings) = pipeline.drainAll(settings).forEach(host::onScoutBeacons)

    override fun onClear() = pipeline.clear()

    private companion object {
        /** Access points only; Wi-Fi clients come with their own record type and source. */
        val CAPTURE = ScoutCapture()

        const val LOG_NAME = "Wi-Fi"
    }
}
