package com.craxiom.networksurvey.notification

import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Drives the stall watchdog with Robolectric's paused looper so elapsed time is deterministic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SurveyStallWatchdogTest {

    private val recordCount = AtomicInteger(0)
    private val surveyActive = AtomicBoolean(true)
    private val paused = AtomicBoolean(false)
    private var stallChanges = 0
    private var ticks = 0

    private val handler = Handler(Looper.getMainLooper())

    private val watchdog = SurveyStallWatchdog(
        handler = handler,
        recordCount = { recordCount.get() },
        isSurveyActive = { surveyActive.get() },
        isPaused = { paused.get() },
        thresholdMs = { SurveyStallWatchdog.MIN_STALL_THRESHOLD_MS },
        onStallStateChanged = { stallChanges++ },
        onTick = { ticks++ }
    )

    private fun advanceMinutes(minutes: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(minutes))
    }

    @Test
    fun thresholdFor_usesFiveMinuteFloorForDefaultIntervals() {
        assertEquals(TimeUnit.MINUTES.toMillis(5), SurveyStallWatchdog.thresholdFor(listOf(5_000, 15_000, 30_000, 45_000)))
        assertEquals(TimeUnit.MINUTES.toMillis(5), SurveyStallWatchdog.thresholdFor(emptyList()))
    }

    @Test
    fun thresholdFor_scalesWithLongScanIntervals() {
        assertEquals(TimeUnit.MINUTES.toMillis(15), SurveyStallWatchdog.thresholdFor(listOf(5_000, 300_000)))
    }

    @Test
    fun recordsFlowing_neverReportsStall() {
        watchdog.start()
        for (i in 1..10) {
            recordCount.incrementAndGet()
            advanceMinutes(1)
        }

        assertNull(watchdog.stalledMinutes)
        assertEquals(0, stallChanges)
        assertEquals(10, ticks)
    }

    @Test
    fun quietSurvey_reportsStallOnceThresholdPassesAndClearsOnNextRecord() {
        watchdog.start()

        advanceMinutes(4)
        assertNull(watchdog.stalledMinutes)

        advanceMinutes(2)
        assertEquals(5, watchdog.stalledMinutes)
        assertEquals(1, stallChanges)

        // Minutes advance in five minute steps, so nothing changes until the next step.
        advanceMinutes(3)
        assertEquals(5, watchdog.stalledMinutes)
        assertEquals(1, stallChanges)

        advanceMinutes(2)
        assertEquals(10, watchdog.stalledMinutes)
        assertEquals(2, stallChanges)

        recordCount.incrementAndGet()
        advanceMinutes(1)
        assertNull(watchdog.stalledMinutes)
        assertEquals(3, stallChanges)
    }

    @Test
    fun pausedSurvey_isNotAStall() {
        paused.set(true)
        watchdog.start()

        advanceMinutes(30)

        assertNull(watchdog.stalledMinutes)
        assertEquals(0, stallChanges)
        assertEquals(0, ticks)
    }

    @Test
    fun inactiveSurvey_isNotAStallAndDoesNotTick() {
        surveyActive.set(false)
        watchdog.start()

        advanceMinutes(30)

        assertNull(watchdog.stalledMinutes)
        assertEquals(0, ticks)
    }

    @Test
    fun stop_clearsStallStateAndStopsChecking() {
        watchdog.start()
        advanceMinutes(6)
        assertEquals(5, watchdog.stalledMinutes)

        watchdog.stop()
        assertNull(watchdog.stalledMinutes)

        val ticksAtStop = ticks
        advanceMinutes(10)
        assertEquals(ticksAtStop, ticks)
    }
}
