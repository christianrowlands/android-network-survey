package com.craxiom.networksurvey.scout.wifi

import android.location.Location
import android.location.LocationListener
import com.craxiom.networksurvey.GpsListener
import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.link.ScoutCapture
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutId
import com.craxiom.networksurvey.scout.model.ScoutRecordListener
import com.craxiom.networksurvey.scout.model.ScoutRecords
import timber.log.Timber
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * What a Scout capture source needs from its owner to cut windows and feed live views. Called on
 * the source's own thread.
 */
interface ScoutCaptureHost {
    /** The survey's Wi-Fi scan interval, which sets how often each item is saved. */
    fun scanRateMs(): Int

    /** True while a live screen for this source's records is open. */
    fun hasLiveViews(): Boolean

    /** The shortest interval an open live screen asks for. */
    fun liveViewIntervalMs(): Int

    /** When the survey session started, Unix epoch milliseconds, null when none runs. */
    fun surveyStartMs(): Long?
}

/**
 * The lifecycle every Scout record source shares (access points, Wi-Fi clients): while it runs it
 * holds a Scout capture (so the board surveys and, with nothing else holding one, is told to Stop
 * when this source stops or pauses), listens to the Scout's records and the phone's location fixes,
 * and runs its pipeline on its own thread, ticking once a second. The Scout thread only hands
 * batches over, as [ScoutRecordListener] asks.
 *
 * Subclasses say what to capture ([capture]), which batches they want ([wants]) and what to do on
 * each step; every hook runs on the source's thread.
 */
abstract class ScoutCaptureSource(
    protected val manager: ScoutManager,
    private val host: ScoutCaptureHost,
    private val gpsListener: GpsListener?,
    private val capture: ScoutCapture,
    private val executor: ScheduledExecutorService,
    protected val wallClock: () -> Long,
    private val elapsedClock: () -> Long,
) : ScoutRecordListener {
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = addFix(location)
    }

    @Volatile
    protected var running = false
        private set

    @Volatile
    protected var paused = false
        private set
    private var tickFuture: ScheduledFuture<*>? = null

    /** A short name for the log, "Wi-Fi". */
    protected abstract val logName: String

    /** A capture started for [scout] at [nowMs]: forget what was heard before. */
    protected abstract fun onCaptureStart(scout: PairedScout?, nowMs: Long)

    /** The Scout's current pairing, so names and models stay current after a firmware update. */
    protected abstract fun onScout(scout: PairedScout?)

    /** @return True if [records] holds anything this source keeps. */
    protected abstract fun wants(records: ScoutRecords): Boolean

    protected abstract fun onBatch(records: ScoutRecords, windowMs: Long)

    protected abstract fun onBoardStatus(scoutId: ScoutId, status: BoardStatus, receivedAtMs: Long)

    protected abstract fun onPhoneFix(fix: PhoneFix)

    /** One tick at [nowMs]: hand closed windows and a live snapshot to the owner. */
    protected abstract fun onTick(nowMs: Long, settings: ScoutWifiPipeline.Settings)

    /** A pause or stop: hand everything held to the owner. */
    protected abstract fun onDrain(settings: ScoutWifiPipeline.Settings)

    /** A stop, after the drain: forget everything. */
    protected abstract fun onClear()

    open fun start() {
        running = true
        paused = false
        val nowMs = wallClock()
        val scout = manager.pairedScout.value
        execute { onCaptureStart(scout, nowMs) }
        gpsListener?.latestLocation?.let(::addFix)
        gpsListener?.registerListener(locationListener)
        manager.addRecordListener(this)
        manager.requestCapture(this, capture)
        tickFuture = executor.scheduleWithFixedDelay(::safeTick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS)
        Timber.i("NS Scout %s started for %s", logName, scout?.name)
    }

    open fun stop() {
        running = false
        tickFuture?.cancel(false)
        tickFuture = null
        manager.release(this)
        manager.removeRecordListener(this)
        gpsListener?.unregisterListener(locationListener)
        // Saves the open window to whatever still listens (a sink that leaves first takes its copy
        // through a peek), then forgets everything.
        execute {
            onDrain(settings())
            onClear()
        }
        Timber.i("NS Scout %s stopped", logName)
    }

    open fun pause() {
        paused = true
        // Stop goes to the board; the survey's sinks are still there for what is held so far.
        manager.release(this)
        execute { onDrain(settings()) }
    }

    open fun resume() {
        if (!paused) return
        paused = false
        if (running) manager.requestCapture(this, capture)
    }

    open fun destroy() {
        executor.shutdown()
    }

    override fun onScoutRecords(records: ScoutRecords) {
        if (!running || paused || !wants(records)) return
        execute {
            onScout(manager.pairedScout.value)
            onBatch(records, windowMs())
        }
    }

    override fun onScoutStatus(scoutId: ScoutId, status: BoardStatus) {
        val receivedAtMs = wallClock()
        execute { onBoardStatus(scoutId, status, receivedAtMs) }
    }

    /** Runs one tick now, for tests that drive the source without waiting. */
    internal fun tickNow() = tick()

    private fun addFix(location: Location?) {
        if (location == null) return
        val fix = PhoneFix.from(location, wallClock(), elapsedClock())
        execute { onPhoneFix(fix) }
    }

    private fun safeTick() {
        try {
            tick()
        } catch (e: Exception) {
            Timber.e(e, "The NS Scout %s tick failed", logName)
        }
    }

    private fun tick() {
        if (!running || paused) return
        onTick(wallClock(), settings())
    }

    protected fun settings(): ScoutWifiPipeline.Settings {
        val scanRateMs = host.scanRateMs().toLong()
        return ScoutWifiPipeline.Settings(
            windowMs = windowMs(),
            sweepMs = sweepMs(),
            maxFixGapMs = maxOf(MIN_FIX_GAP_MS, scanRateMs * FIX_GAP_SCAN_RATE_FACTOR),
            surveyStartMs = host.surveyStartMs(),
            liveViews = host.hasLiveViews(),
            snapshotIntervalMs = maxOf(MIN_SNAPSHOT_INTERVAL_MS, host.liveViewIntervalMs().toLong()),
        )
    }

    /** At most one record per item per window: the longer of the scan interval and one sweep. */
    private fun windowMs(): Long = maxOf(host.scanRateMs().toLong(), sweepMs())

    private fun sweepMs(): Long {
        val channels = manager.linkDetails.value.configured?.channelsList.orEmpty()
        val bands = manager.pairedScout.value?.bands.orEmpty()
        return if (channels.isEmpty() && bands.isEmpty()) ScoutSweep.FALLBACK_MS else ScoutSweep.estimateMs(channels, bands)
    }

    /** @return False when the source's thread is gone and [block] will not run. */
    private fun execute(block: () -> Unit): Boolean = try {
        executor.execute {
            try {
                block()
            } catch (e: Exception) {
                Timber.e(e, "The NS Scout %s thread failed a task", logName)
            }
        }
        true
    } catch (e: Exception) {
        Timber.w(e, "The NS Scout %s thread is gone", logName)
        false
    }

    companion object {
        /** How often closed windows are drained and the live snapshot is considered. */
        const val TICK_MS = 1_000L

        /** The live screens get a snapshot at most this often. */
        const val MIN_SNAPSHOT_INTERVAL_MS = 2_000L

        /** A capture with no phone fix this close (or two scan intervals, if longer) has no location. */
        const val MIN_FIX_GAP_MS = 30_000L

        private const val FIX_GAP_SCAN_RATE_FACTOR = 2L
    }
}
