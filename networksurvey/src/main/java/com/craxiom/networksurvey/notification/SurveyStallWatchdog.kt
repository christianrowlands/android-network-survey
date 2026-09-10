package com.craxiom.networksurvey.notification

import android.os.Handler
import android.os.SystemClock
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Watches the survey session record counter and reports when records stop arriving.
 *
 * The survey notification shows an elapsed-time chronometer, but a running clock only proves the
 * notification exists, not that records are flowing. This watchdog runs a cheap check once a
 * minute on the service's own handler thread: if a survey is active, scanning is not deliberately
 * paused, and the record count has not moved for at least [thresholdMs], the survey is considered
 * stalled and [onStallStateChanged] fires so the notification title can flip to
 * "No records for N min". It fires again when the first record arrives so the title flips back.
 *
 * While stalled, the reported minute value only advances in [STALL_DISPLAY_STEP_MINUTES] steps so
 * the notification is re-posted a handful of times an hour rather than every minute.
 *
 * The same tick also calls [onTick] so the owner can re-post the ongoing notification if the user
 * swiped it away, which Android 14 and later allow for foreground services.
 *
 * @param handler The handler whose thread runs the checks (the service's handler thread).
 * @param recordCount Supplies the survey session record counter.
 * @param isSurveyActive True while any survey output is running.
 * @param isPaused True while scanning is deliberately paused (battery or queue backpressure).
 * @param thresholdMs Supplies how long records may be absent before the survey counts as stalled.
 * @param onStallStateChanged Called on the handler thread when [stalledMinutes] changes.
 * @param onTick Called on the handler thread on every check while a survey is active.
 */
class SurveyStallWatchdog(
    private val handler: Handler,
    private val recordCount: () -> Int,
    private val isSurveyActive: () -> Boolean,
    private val isPaused: () -> Boolean,
    private val thresholdMs: () -> Long,
    private val onStallStateChanged: () -> Unit,
    private val onTick: () -> Unit
) {
    /**
     * Minutes since the last record when stalled, or null while records are flowing.
     */
    @Volatile
    var stalledMinutes: Int? = null
        private set

    private var lastCount = 0
    private var lastChangeElapsedMs = 0L
    private var running = false

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                check()
            } catch (e: Exception) {
                Timber.e(e, "The survey stall watchdog check failed")
            }
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    /**
     * Start the periodic checks. Safe to call more than once.
     */
    fun start() {
        if (running) return
        running = true
        lastCount = recordCount()
        lastChangeElapsedMs = SystemClock.elapsedRealtime()
        handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS)
    }

    /**
     * Stop the periodic checks and clear any stall state.
     */
    fun stop() {
        running = false
        handler.removeCallbacks(checkRunnable)
        stalledMinutes = null
    }

    private fun check() {
        val now = SystemClock.elapsedRealtime()
        val count = recordCount()

        if (!isSurveyActive() || isPaused()) {
            // Nothing is expected to produce records right now, so this is not a stall.
            lastCount = count
            lastChangeElapsedMs = now
            updateStalledMinutes(null)
            return
        }

        onTick()

        if (count != lastCount) {
            lastCount = count
            lastChangeElapsedMs = now
            updateStalledMinutes(null)
            return
        }

        val quietMs = now - lastChangeElapsedMs
        if (quietMs < thresholdMs()) {
            updateStalledMinutes(null)
            return
        }

        val quietMinutes = TimeUnit.MILLISECONDS.toMinutes(quietMs).toInt()
        val displayMinutes = (quietMinutes / STALL_DISPLAY_STEP_MINUTES) * STALL_DISPLAY_STEP_MINUTES
        updateStalledMinutes(displayMinutes.coerceAtLeast(STALL_DISPLAY_STEP_MINUTES))
    }

    private fun updateStalledMinutes(minutes: Int?) {
        if (stalledMinutes == minutes) return
        stalledMinutes = minutes
        if (minutes == null) {
            Timber.i("Survey records are flowing again")
        } else {
            Timber.w("No survey records for %d minutes", minutes)
        }
        onStallStateChanged()
    }

    companion object {
        /** How often the watchdog checks the record counter. */
        val CHECK_INTERVAL_MS: Long = TimeUnit.MINUTES.toMillis(1)

        /** The shortest quiet period that counts as a stall, regardless of scan intervals. */
        val MIN_STALL_THRESHOLD_MS: Long = TimeUnit.MINUTES.toMillis(5)

        /** Multiplier applied to the longest scan interval when deriving the stall threshold. */
        const val SCAN_INTERVAL_MULTIPLIER = 3

        /** The stalled minute value only advances in steps of this size to limit re-posts. */
        const val STALL_DISPLAY_STEP_MINUTES = 5

        /**
         * Derive the stall threshold from the scan intervals that are currently in use: three
         * times the longest interval, but never less than [MIN_STALL_THRESHOLD_MS].
         */
        fun thresholdFor(scanIntervalsMs: Collection<Int>): Long {
            val longest = scanIntervalsMs.maxOrNull() ?: 0
            return maxOf(MIN_STALL_THRESHOLD_MS, longest.toLong() * SCAN_INTERVAL_MULTIPLIER)
        }
    }
}
