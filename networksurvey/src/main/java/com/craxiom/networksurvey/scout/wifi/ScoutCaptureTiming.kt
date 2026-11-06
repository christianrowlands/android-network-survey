package com.craxiom.networksurvey.scout.wifi

import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.scout.ScoutCatchUpTracker
import com.craxiom.networksurvey.scout.model.ScoutId
import com.craxiom.networksurvey.scout.model.ScoutRecords

/**
 * One observation from a Scout, ready to become a record: when it was heard (phone wall clock), the
 * phone fix nearest that time, and whether it arrived late.
 *
 * @property fix Null when no phone fix was close enough to the capture time.
 * @property late True when the Scout buffered it while out of range.
 * @property slot The window record this is, for its record number; null in a live view snapshot.
 */
class ScoutHeard<O>(
    val observation: O,
    val scoutName: String,
    val captureTimeMs: Long,
    val fix: PhoneFix?,
    val late: Boolean,
    val slot: ScoutRecordSlot? = null,
)

/**
 * When and where a Scout capture was heard, shared by the access point and Wi-Fi client pipelines:
 * board time to wall time ([BoardClock]), the phone fix nearest a capture ([LocationHistory]), the
 * late flag for a backlog, and the time floor that keeps captures from before a start out.
 *
 * Observations captured before the pipeline started are dropped as they arrive (the board can hold
 * a backlog from an earlier capture), and records captured before the survey started are dropped as
 * they leave, so a Mission never holds records from before its start. Not thread safe; the owning
 * pipeline synchronizes.
 *
 * @param lateThresholdMs A capture that arrives more than this after it was heard is late.
 */
class ScoutCaptureTiming(private val lateThresholdMs: Long = ScoutCatchUpTracker.DEFAULT_LAG_THRESHOLD_MS) {
    private val clock = BoardClock()
    private val locations = LocationHistory()
    private var startedAtMs = 0L

    /** The newest survey start seen, so a flush as the survey ends still drops pre-start records. */
    private var lastSurveyStartMs: Long? = null

    /** Starts a capture at [nowMs]; anything heard before is dropped. The clock and fixes stay. */
    fun start(nowMs: Long) {
        startedAtMs = nowMs
        lastSurveyStartMs = null
    }

    fun onStatus(scoutId: ScoutId, status: BoardStatus, receivedAtMs: Long) {
        clock.noteStatus(status, scoutId, receivedAtMs)
    }

    fun onFix(fix: PhoneFix) {
        locations.append(fix)
    }

    /** @return The wall clock time [boardTimeMs] of [records] was heard, or null when it was heard before the start. */
    fun captureTimeMs(boardTimeMs: Long, records: ScoutRecords): Long? {
        val time = clock.wallTimeMs(boardTimeMs, records.timeSynced, records.scoutId, records.receivedAtMs)
        return time.takeIf { it >= startedAtMs }
    }

    /** @return True when a capture heard at [captureTimeMs] arrived late with [records] (a backlog). */
    fun isLate(captureTimeMs: Long, records: ScoutRecords): Boolean = records.receivedAtMs - captureTimeMs > lateThresholdMs

    /** Remembers the survey's start from [settings], for a flush after the survey ended. */
    fun noteSurveyStart(settings: ScoutWifiPipeline.Settings) {
        settings.surveyStartMs?.let { lastSurveyStartMs = it }
    }

    /** @return The earliest capture time a record may have to leave the pipeline. */
    fun floorMs(settings: ScoutWifiPipeline.Settings): Long = settings.surveyStartMs ?: lastSurveyStartMs ?: startedAtMs

    /** @return The phone fix nearest [captureTimeMs] within the settings' gap, or null. */
    fun fixFor(captureTimeMs: Long, settings: ScoutWifiPipeline.Settings): PhoneFix? =
        locations.nearestWithin(captureTimeMs, settings.maxFixGapMs)
}
