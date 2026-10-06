package com.craxiom.networksurvey.survey.engine

import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns the survey session and the Mission ID rules, independent of the Android service so the
 * rules can be unit tested.
 *
 * - A **session** runs from the first destination starting until the last one stops. It carries the
 *   start time and the record counts.
 * - The **Mission ID** rolls when the first mission relevant destination starts (every destination
 *   except the community databases), stays fixed while destinations are added or removed, and is
 *   kept as the most recent value after the session ends.
 *
 * The caller supplies the activity checks. They must count destinations that are still connecting
 * (MQTT and gRPC), otherwise swapping one destination for a connecting one would end the session
 * in between and roll a second Mission ID when the connection completes.
 *
 * @param generateMissionId                Creates a new Mission ID value.
 * @param isAnySurveyActive                True while any destination (including community) runs.
 * @param isAnyMissionRelevantSurveyActive True while any mission relevant destination runs or is
 *                                         connecting.
 * @param onMissionIdChanged               Called (outside the lock) when the Mission ID rolls, is
 *                                         restored, or its session ends.
 */
class MissionSessionTracker(
    private val generateMissionId: () -> String,
    private val isAnySurveyActive: () -> Boolean,
    private val isAnyMissionRelevantSurveyActive: () -> Boolean,
    private val onMissionIdChanged: (missionId: String?, missionSessionActive: Boolean) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()

    /** The aggregate record count for the session; the stall watchdog watches it. */
    val recordCount = AtomicInteger(0)

    /** The count of records that passed the community upload filters this session. */
    val uploadRecordCount = AtomicInteger(0)

    /** Per record type counts for the session. */
    val counters = SurveySessionCounters(clock)

    @Volatile
    private var missionId: String? = null
    private var missionSessionActive = false
    private var sessionStartTimeMs: Long? = null

    /** A copy of the Mission ID state, used to undo an optimistic start. */
    data class Snapshot(val missionId: String?, val missionSessionActive: Boolean)

    /** @return The current Mission ID state. */
    fun snapshot(): Snapshot = synchronized(lock) { Snapshot(missionId, missionSessionActive) }

    /** @return When the session started, in epoch milliseconds, or null when no session is running. */
    fun sessionStartTimeMs(): Long? = synchronized(lock) { sessionStartTimeMs }

    /** @return True while at least one mission relevant destination is running. */
    fun isMissionSessionActive(): Boolean = synchronized(lock) { missionSessionActive }

    /** @return The rolled Mission ID for display, or null if none has rolled in this app session. */
    fun rolledMissionId(): String? = missionId

    /**
     * @return The Mission ID to stamp on records: the rolled value, or an empty string before the
     * first roll (only reachable when nothing but community scanning runs, which discards it).
     */
    fun missionIdForRecords(): String = missionId ?: ""

    /**
     * Called when a destination starts. Starts the session if none is running, and rolls the
     * Mission ID if [missionRelevant] and no mission session is active yet.
     *
     * Callers must call this before registering any listener that can emit a record immediately,
     * so that record cannot capture a pre-roll Mission ID.
     */
    fun onSurveyStarted(missionRelevant: Boolean) {
        val notify: Snapshot?
        synchronized(lock) {
            if (sessionStartTimeMs == null) {
                Timber.i("Starting new survey session")
                sessionStartTimeMs = clock()
                resetCounts()
            }

            notify = if (missionRelevant && !missionSessionActive) {
                missionSessionActive = true
                missionId = generateMissionId()
                Timber.i("Rolled to a new Mission ID: %s", missionId)
                Snapshot(missionId, true)
            } else {
                null
            }
        }
        notify?.let { onMissionIdChanged(it.missionId, it.missionSessionActive) }
    }

    /**
     * Called when a destination stops. Ends the session when nothing runs anymore, and ends the
     * mission session when no mission relevant destination runs anymore.
     */
    fun onSurveyStopped() {
        val notify: Snapshot?
        synchronized(lock) {
            if (sessionStartTimeMs != null && !isAnySurveyActive()) {
                Timber.i("All surveys stopped, ending survey session. Total records: %d", recordCount.get())
                sessionStartTimeMs = null
                resetCounts()
            }

            notify = if (missionSessionActive && !isAnyMissionRelevantSurveyActive()) {
                missionSessionActive = false
                Timber.i("Mission session ended. Most recent Mission ID: %s", missionId)
                Snapshot(missionId, false)
            } else {
                null
            }
        }
        notify?.let { onMissionIdChanged(it.missionId, it.missionSessionActive) }
    }

    /**
     * Ends the session and the mission session immediately, even if a destination is still
     * finishing an asynchronous stop (gRPC). Used by Stop, so a Start right after it always rolls a
     * fresh Mission ID instead of continuing the old one.
     */
    fun endSession() {
        val notify: Snapshot?
        synchronized(lock) {
            sessionStartTimeMs = null
            resetCounts()
            notify = if (missionSessionActive) {
                missionSessionActive = false
                Timber.i("Survey stopped. Most recent Mission ID: %s", missionId)
                Snapshot(missionId, false)
            } else {
                null
            }
        }
        notify?.let { onMissionIdChanged(it.missionId, it.missionSessionActive) }
    }

    /**
     * Undoes an optimistic [onSurveyStarted] whose start then failed, restoring the Mission ID that
     * was in place before (so a failed start never leaves behind a rolled ID no record carries).
     *
     * @param before The state captured with [snapshot] before the optimistic start.
     */
    fun undoOptimisticStart(before: Snapshot) {
        val notify: Snapshot?
        synchronized(lock) {
            notify = if (!before.missionSessionActive && missionSessionActive) {
                missionSessionActive = false
                missionId = before.missionId
                Snapshot(missionId, false)
            } else {
                null
            }

            if (!isAnySurveyActive()) {
                sessionStartTimeMs = null
                resetCounts()
            }
        }
        notify?.let { onMissionIdChanged(it.missionId, it.missionSessionActive) }
    }

    private fun resetCounts() {
        recordCount.set(0)
        uploadRecordCount.set(0)
        counters.reset()
    }
}
