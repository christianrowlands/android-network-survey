package com.craxiom.networksurvey.scout.wifi

import androidx.annotation.VisibleForTesting
import com.craxiom.networksurvey.board.v1.ApObservation
import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.scout.ScoutCatchUpTracker
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutId
import com.craxiom.networksurvey.scout.model.ScoutRecords
import com.google.protobuf.ByteString

/**
 * The Scout's Wi-Fi record path, without threads so it can be tested: when and where each capture
 * was heard ([ScoutCaptureTiming]), one record per access point per window
 * ([ScoutRecordAggregator]) for the survey's sinks, and a live snapshot of every access point heard
 * in the last sweep for the live Wi-Fi screens.
 *
 * Observations captured before the source started are dropped as they arrive (the board can hold
 * a backlog from an earlier capture), and windowed records captured before the survey started are
 * dropped as they leave, so a Mission never holds Wi-Fi from before its start.
 *
 * Methods are synchronized: [ScoutWifiSource] mostly calls it from one thread, and a leaving sink peeks from another.
 */
class ScoutWifiPipeline(
    private val lateThresholdMs: Long = ScoutCatchUpTracker.DEFAULT_LAG_THRESHOLD_MS,
) {
    /** What the tick needs to know about the survey and the screens right now. */
    data class Settings(
        /** At most one record per access point per window. */
        val windowMs: Long,
        /** One channel sweep: the live snapshot keeps access points heard within it. */
        val sweepMs: Long,
        /** A capture with no phone fix closer than this is saved without a location. */
        val maxFixGapMs: Long,
        /** When the survey session started, null when none runs (only live screens). */
        val surveyStartMs: Long?,
        /** True while a live Wi-Fi screen is open. */
        val liveViews: Boolean,
        val snapshotIntervalMs: Long,
    )

    /** What one tick produced. */
    data class Output(val windows: List<List<ScoutBeacon>>, val snapshot: List<ScoutBeacon>?)

    /** One access point observation with the time it was heard. */
    private class Sighting(val observation: ApObservation, val captureTimeMs: Long, val late: Boolean)

    private val timing = ScoutCaptureTiming(lateThresholdMs)
    private val aggregator = ScoutRecordAggregator<ByteString, Sighting>(
        keyOf = { it.observation.bssid },
        strengthOf = { it.observation.rssiMax },
        timeOf = { it.captureTimeMs },
    )
    private val live = LinkedHashMap<ByteString, Sighting>()
    private var scout: PairedScout? = null
    private var windowMs = 0L
    private var lastSnapshotMs: Long? = null

    /** The access points the live snapshot could list right now. */
    @get:VisibleForTesting
    internal val liveCount: Int
        @Synchronized get() = live.size

    /** Starts a capture for [paired] at [nowMs]; anything heard before is dropped. */
    @Synchronized
    fun start(paired: PairedScout?, nowMs: Long) {
        scout = paired
        timing.start(nowMs)
        lastSnapshotMs = null
        aggregator.clear()
        live.clear()
    }

    /** Keeps the Scout's name and model current (Info can change them after a firmware update). */
    @Synchronized
    fun setScout(paired: PairedScout?) {
        scout = paired
    }

    @Synchronized
    fun onStatus(scoutId: ScoutId, status: BoardStatus, receivedAtMs: Long) {
        timing.onStatus(scoutId, status, receivedAtMs)
    }

    @Synchronized
    fun onFix(fix: PhoneFix) {
        timing.onFix(fix)
    }

    /** Adds a batch of access point observations. */
    @Synchronized
    fun onRecords(records: ScoutRecords, windowMs: Long) {
        if (scout?.scoutId != records.scoutId) return
        for (ap in records.accessPoints) {
            // No record can be built without a BSSID, so it never takes a record number
            if (!ScoutWifiFields.isMac(ap.bssid)) continue
            val time = timing.captureTimeMs(ap.boardTimeMs, records) ?: continue
            val sighting = Sighting(ap, time, late = timing.isLate(time, records))
            aggregator.add(sighting, windowMs)
            if (!sighting.late) {
                val previous = live[ap.bssid]
                if (previous == null || previous.captureTimeMs <= time) live[ap.bssid] = sighting
            }
        }
    }

    /** @return The windows that closed and, when a live screen wants one, a snapshot. */
    @Synchronized
    fun tick(nowMs: Long, settings: Settings): Output {
        noteSurveyStart(settings)
        val windows = ArrayList<List<ScoutBeacon>>()
        if (settings.windowMs != windowMs) {
            // A new scan rate cuts windows differently; emit what the old cut holds.
            if (windowMs != 0L) aggregator.drainAll().mapNotNullTo(windows) { beacons(it, settings) }
            windowMs = settings.windowMs
        }
        aggregator.drainClosed(nowMs).mapNotNullTo(windows) { beacons(it, settings) }
        // Every tick, not only for a snapshot: without a live screen it would grow for the whole survey
        live.values.removeAll { it.captureTimeMs < nowMs - settings.sweepMs }
        return Output(windows, snapshot(nowMs, settings))
    }

    /** @return Everything still waiting, for a pause or a stop (records from before the survey started stay out). */
    @Synchronized
    fun drainAll(settings: Settings): List<List<ScoutBeacon>> {
        noteSurveyStart(settings)
        return aggregator.drainAll().mapNotNull { beacons(it, settings) }
    }

    /**
     * @return Everything still waiting, left in place, for a sink that is about to leave while
     * others stay: it gets what has been heard so far, the rest still go out with the window.
     */
    @Synchronized
    fun peekAll(settings: Settings): List<List<ScoutBeacon>> {
        noteSurveyStart(settings)
        return aggregator.peekAll().mapNotNull { beacons(it, settings) }
    }

    private fun noteSurveyStart(settings: Settings) = timing.noteSurveyStart(settings)

    /** Forgets what was heard, for a stop. The clock and the fixes stay. */
    @Synchronized
    fun clear() {
        aggregator.clear()
        live.clear()
    }

    private fun beacons(window: ScoutRecordAggregator.Window<Sighting>, settings: Settings): List<ScoutBeacon>? {
        val floor = timing.floorMs(settings)
        return window.items.filter { it.captureTimeMs >= floor }
            .mapNotNull { beacon(it, settings, aggregator.slotOf(window, it)) }
            .takeIf { it.isNotEmpty() }
    }

    private fun snapshot(nowMs: Long, settings: Settings): List<ScoutBeacon>? {
        val last = lastSnapshotMs
        if (!settings.liveViews || (last != null && nowMs - last < settings.snapshotIntervalMs)) return null
        if (live.isEmpty()) return null
        lastSnapshotMs = nowMs
        return live.values.mapNotNull { beacon(it, settings) }
    }

    private fun beacon(sighting: Sighting, settings: Settings, slot: ScoutRecordSlot? = null): ScoutBeacon? {
        val paired = scout ?: return null
        return ScoutBeacon(
            observation = sighting.observation,
            scoutName = paired.name,
            captureTimeMs = sighting.captureTimeMs,
            fix = timing.fixFor(sighting.captureTimeMs, settings),
            late = sighting.late,
            slot = slot,
        )
    }
}
