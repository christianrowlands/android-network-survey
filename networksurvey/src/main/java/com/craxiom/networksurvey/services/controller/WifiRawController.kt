package com.craxiom.networksurvey.services.controller

import android.os.Environment
import androidx.annotation.VisibleForTesting
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.PcapngFileWriter
import com.craxiom.networksurvey.logging.RawFramesProgress
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.scout.raw.ScoutRawSource
import com.craxiom.networksurvey.scout.raw.StampedRawFrame
import com.craxiom.networksurvey.scout.wifi.ScoutSourceWatcher
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.services.SurveyRecordProcessor
import com.craxiom.networksurvey.util.NsUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The Raw frames record type: 802.11 frames an NS Scout hears, saved as pcapng to Log files only
 * (no other destination takes them, so a survey without Log files never turns this on and never
 * creates a file).
 *
 * While raw frame logging is on and the Scout is the Wi-Fi source, the [ScoutRawSource] holds a raw
 * capture (which subscribes to the Scout's Raw characteristic); otherwise nothing is captured. Frames
 * go from the Scout thread to this controller's own writer thread, which owns the [PcapngFileWriter].
 * When the writer falls behind, whole notifications are dropped (counted) rather than slowing the
 * Scout thread. When storage runs low the writer stops, the capture is let go, and [progress] says so.
 * [progress] also carries the frames that never reached a file (the writer fell behind, or the link
 * lost a notification). A capture that starts during a battery or backpressure pause stays paused.
 *
 * Sessions: every turn on starts a numbered session. The controller, not the writer, decides whether
 * the current session still saves ([sessionSaving]), so a turn on after a storage-full or failed
 * session starts the capture at once instead of reading the old session's state. Frames queued for
 * an earlier session are skipped. Session control (start, close) is never refused: only frame
 * batches are bounded ([MAX_PENDING_BATCHES]), the writer queue itself is not.
 */
class WifiRawController @JvmOverloads constructor(
    private val service: NetworkSurveyService,
    private val scoutManager: ScoutManager = ScoutManager.getInstance(service),
    private val writerExecutor: ExecutorService = newWriterExecutor(),
    private val writer: PcapngFileWriter = newWriter(service),
) {
    private val lock = Any()
    private val loggingEnabled = AtomicBoolean(false)
    private val droppedFrames = AtomicLong()
    private val pendingBatches = AtomicInteger()

    /** The session the controller is in; bumped (under [lock]) by every turn on. */
    private val session = AtomicInteger()

    /** The session the writer has started, so [progress] never reports an earlier session's state. */
    private val writerSession = AtomicInteger()

    /** False once the current session's writer stopped (storage full or a failed write). */
    private val sessionSaving = AtomicBoolean(false)
    private val sourceWatcher = ScoutSourceWatcher(scoutManager)
    private val source = ScoutRawSource(scoutManager, ::enqueue, ::onFramesLost)

    @Volatile
    private var destroyed = false

    /** True during a battery or backpressure pause; guarded by [lock]. */
    private var paused = false

    /** Starts following Wi-Fi source changes, so the capture starts and stops with the Scout. */
    fun start() {
        sourceWatcher.start { updateSource() }
    }

    /** Stops the capture and closes the file; the close runs after the frames already queued. */
    fun onDestroy() {
        synchronized(lock) {
            destroyed = true
            sourceWatcher.stop()
            source.stop()
            runOnWriter { writer.close() }
            writerExecutor.shutdown()
        }
    }

    /** @return True while raw frames are being saved to pcapng (or waiting for the first frame). */
    fun isLoggingEnabled(): Boolean = loggingEnabled.get()

    /**
     * @return What the raw frames file saved this survey, or null while raw frame logging is off.
     * Between a turn on and the writer starting the new session it is an empty, saving progress.
     */
    fun progress(): RawFramesProgress? = when {
        !loggingEnabled.get() -> null
        writerSession.get() != session.get() -> RawFramesProgress()
        else -> writer.progress.copy(framesDropped = droppedFrames.get())
    }

    /** @return The raw frames saved this survey, 0 while raw frame logging is off. */
    fun framesSaved(): Long = progress()?.framesSaved ?: 0L

    /**
     * Turns raw frame logging on or off. On starts a new session (counts from zero, a new file at
     * the first frame); off closes the file.
     *
     * @return The new logging state, or null when it could not be changed.
     */
    fun toggleLogging(enable: Boolean): Boolean? = synchronized(lock) {
        if (destroyed) return null
        if (enable == loggingEnabled.get()) return enable
        if (enable) {
            onRolloverSizeChanged()
            droppedFrames.set(0)
            val id = session.incrementAndGet()
            if (!runOnWriter {
                    writer.startSession()
                    writerSession.set(id)
                }) return null
            sessionSaving.set(true)
            loggingEnabled.set(true)
        } else {
            loggingEnabled.set(false)
            sessionSaving.set(false)
            runOnWriter {
                writer.close()
                if (droppedFrames.get() > 0) Timber.w("%d raw frames never reached a file", droppedFrames.get())
            }
        }
        updateSourceLocked()
        service.updateServiceNotification()
        service.notifyLoggingChangedListeners()
        enable
    }

    /** Battery or backpressure pause: the board stops sending raw frames. */
    fun pauseScanning() {
        synchronized(lock) {
            paused = true
            source.pause()
        }
    }

    /** Undoes [pauseScanning]: the board sends raw frames again. */
    fun resumeScanning() {
        synchronized(lock) {
            paused = false
            source.resume()
        }
    }

    /** Re-reads the log rollover size (a settings or managed configuration change). */
    fun onRolloverSizeChanged() {
        writer.rolloverLimitBytes = PreferenceUtils.getRolloverSizePreference(service).toLong() * BYTES_PER_MEGABYTE
    }

    private fun updateSource() {
        synchronized(lock) { updateSourceLocked() }
    }

    private fun updateSourceLocked() {
        if (destroyed) return
        val scoutIsSource = scoutManager.pairedScout.value != null && scoutManager.wifiSource.value == WifiSource.SCOUT
        val wanted = loggingEnabled.get() && scoutIsSource && sessionSaving.get()
        if (wanted && !source.isRunning) {
            source.start()
            if (paused) source.pause()
        } else if (!wanted && source.isRunning) {
            source.stop()
        }
    }

    /** Called on the Scout thread: hands the frames to the writer thread, or drops them when it is behind. */
    @VisibleForTesting
    internal fun enqueue(frames: List<StampedRawFrame>) {
        if (!loggingEnabled.get()) return
        val id = session.get()
        if (pendingBatches.incrementAndGet() > MAX_PENDING_BATCHES) {
            pendingBatches.decrementAndGet()
            onFramesLost(frames.size)
            return
        }
        val queued = runOnWriter {
            try {
                if (id == session.get()) writeLocked(id, frames)
            } finally {
                pendingBatches.decrementAndGet()
            }
        }
        if (!queued) {
            pendingBatches.decrementAndGet()
            onFramesLost(frames.size)
        }
    }

    /** Called on the Scout thread: counts frames that will never reach a file. */
    private fun onFramesLost(count: Int) {
        droppedFrames.addAndGet(count.toLong())
    }

    /** Writer thread: writes one batch of session [id], letting go of the capture if saving stopped. */
    private fun writeLocked(id: Int, frames: List<StampedRawFrame>) {
        val before = writer.progress.state
        writer.write(frames)
        // Storage ran out: let go of the capture, so the board stops spending the link on raw frames
        if (before == RawFramesProgress.State.SAVING && writer.progress.state != RawFramesProgress.State.SAVING) {
            synchronized(lock) {
                if (id == session.get()) sessionSaving.set(false)
                updateSourceLocked()
            }
            service.updateServiceNotification()
        }
    }

    /** @return False when the writer thread is gone (after [onDestroy]), so [block] will not run. */
    private fun runOnWriter(block: () -> Unit): Boolean = try {
        writerExecutor.execute {
            try {
                block()
            } catch (e: Exception) {
                Timber.e(e, "The raw frames writer failed a task")
            }
        }
        true
    } catch (e: RejectedExecutionException) {
        false
    }

    internal companion object {
        /** The pcapng Section Header names the app (shb_userappl), as iOS does with "Network Survey iOS". */
        private const val APPLICATION_PREFIX = "Network Survey Android "

        private const val WRITER_THREAD_NAME = "NS-Pcapng"

        /** Frame batches waiting for the writer; at the link's rate this is minutes of frames. */
        private const val MAX_PENDING_BATCHES = 4_096

        private const val BYTES_PER_MEGABYTE = 1_048_576L

        /** The writer for the shared log files folder, as the CSV files use. */
        fun newWriter(service: NetworkSurveyService) = PcapngFileWriter(
            directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME,
            ),
            application = APPLICATION_PREFIX + NsUtils.getAppVersionName(service),
            fileTime = { SurveyRecordProcessor.DATE_TIME_FORMATTER.format(LocalDateTime.now()) },
        )

        /** Unbounded, so a close or a session start is never refused; [enqueue] bounds the frames. */
        fun newWriterExecutor(): ExecutorService = ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(),
            { runnable -> Thread(runnable, WRITER_THREAD_NAME).apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )
    }
}
