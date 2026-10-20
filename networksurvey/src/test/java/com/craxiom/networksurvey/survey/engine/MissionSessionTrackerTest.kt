package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the session and Mission ID rules in isolation, with explicit activity flags.
 */
class MissionSessionTrackerTest {

    private var anyActive = false
    private var missionActive = false
    private var counter = 0
    private val changes = mutableListOf<Pair<String?, Boolean>>()
    private val endedSessions = mutableListOf<LastSession>()
    private var now = 1_000L

    private val tracker = MissionSessionTracker(
        generateMissionId = { "m${++counter}" },
        isAnySurveyActive = { anyActive },
        isAnyMissionRelevantSurveyActive = { missionActive },
        onMissionIdChanged = { id, active -> changes += id to active },
        clock = { now },
        onSessionEnded = { endedSessions += it },
    )

    @Test
    fun `the first mission relevant start rolls once and later starts do not`() {
        tracker.onSurveyStarted(true)
        anyActive = true; missionActive = true
        tracker.onSurveyStarted(true)
        tracker.onSurveyStarted(true)
        assertEquals("m1", tracker.rolledMissionId())
        assertEquals(1, counter)
        assertEquals(listOf("m1" to true), changes)
    }

    @Test
    fun `a community only session never rolls`() {
        tracker.onSurveyStarted(false)
        assertNull(tracker.rolledMissionId())
        assertEquals("", tracker.missionIdForRecords())
        assertEquals(1_000L, tracker.sessionStartTimeMs())
    }

    @Test
    fun `stopping while something mission relevant still runs keeps the mission`() {
        tracker.onSurveyStarted(true)
        anyActive = true; missionActive = true
        tracker.onSurveyStopped()
        assertTrue(tracker.isMissionSessionActive())
    }

    @Test
    fun `the last stop ends the session but keeps the Mission ID for display`() {
        tracker.onSurveyStarted(true)
        anyActive = false; missionActive = false
        tracker.onSurveyStopped()
        assertFalse(tracker.isMissionSessionActive())
        assertNull(tracker.sessionStartTimeMs())
        assertEquals("m1", tracker.rolledMissionId())
    }

    @Test
    fun `endSession ends the mission even while something is still finishing`() {
        tracker.onSurveyStarted(true)
        anyActive = true; missionActive = true
        tracker.endSession()
        assertFalse(tracker.isMissionSessionActive())
        tracker.onSurveyStarted(true)
        assertEquals("m2", tracker.rolledMissionId())
    }

    @Test
    fun `undo restores the previous Mission ID after a failed optimistic start`() {
        tracker.onSurveyStarted(true)
        tracker.endSession()
        val before = tracker.snapshot()
        tracker.onSurveyStarted(true)
        assertEquals("m2", tracker.rolledMissionId())
        tracker.undoOptimisticStart(before)
        assertEquals("m1", tracker.rolledMissionId())
        assertFalse(tracker.isMissionSessionActive())
    }

    @Test
    fun `a session start resets the counts`() {
        tracker.recordCount.set(5)
        tracker.counters.increment(com.craxiom.networksurvey.survey.model.RecordType.WIFI, 3)
        tracker.communityCounters.increment(RecordType.CELLULAR, 2)
        tracker.onSurveyStarted(true)
        assertEquals(0, tracker.recordCount.get())
        assertEquals(0L, tracker.counters.total())
        assertEquals(0L, tracker.communityCounters.total())
    }

    @Test
    fun `a community only session records what was saved for upload`() {
        tracker.onSurveyStarted(false)
        anyActive = true
        tracker.counters.increment(RecordType.CELLULAR, 400)
        tracker.communityCounters.increment(RecordType.CELLULAR, 3)
        tracker.communityCounters.increment(RecordType.WIFI, 40)
        now = 61_000L

        anyActive = false
        tracker.onSurveyStopped()

        assertEquals(LastSession(60_000L, 43L, 61_000L), tracker.lastSession())
        assertEquals(0L, tracker.communityCounters.total())
    }

    @Test
    fun `a session that ends with only the community databases still records everything it processed`() {
        tracker.onSurveyStarted(true)
        anyActive = true; missionActive = true
        tracker.counters.increment(RecordType.CELLULAR, 400)
        tracker.communityCounters.increment(RecordType.CELLULAR, 3)

        // Log files is removed, so only the community databases run until Stop
        missionActive = false
        tracker.onSurveyStopped()
        now = 61_000L
        anyActive = false
        tracker.onSurveyStopped()

        assertEquals(400L, tracker.lastSession()!!.totalRecords)
        // The Mission ID stays with the last session, so it can be copied after Stop
        assertNotNull(tracker.lastSession()!!.missionId)
        assertEquals(tracker.snapshot().missionId, tracker.lastSession()!!.missionId)
    }

    @Test
    fun `a session with a mission relevant destination records every record processed`() {
        tracker.onSurveyStarted(true)
        anyActive = true; missionActive = true
        tracker.counters.increment(RecordType.CELLULAR, 400)
        tracker.communityCounters.increment(RecordType.CELLULAR, 3)
        now = 11_000L

        tracker.endSession()

        assertEquals(LastSession(10_000L, 400L, 11_000L, "m1"), tracker.lastSession())
    }

    @Test
    fun `the last stop records the session before its counts are reset`() {
        tracker.onSurveyStarted(true)
        anyActive = true; missionActive = true
        tracker.counters.increment(RecordType.CELLULAR, 3)
        tracker.counters.increment(RecordType.WIFI, 2)
        now = 61_000L

        anyActive = false; missionActive = false
        tracker.onSurveyStopped()

        val expected = LastSession(durationMs = 60_000L, totalRecords = 5L, endedAtMs = 61_000L, missionId = "m1")
        assertEquals(expected, tracker.lastSession())
        assertEquals(listOf(expected), endedSessions)
        assertEquals(0L, tracker.counters.total())
    }

    @Test
    fun `endSession records the session even while a destination is still finishing`() {
        tracker.onSurveyStarted(true)
        anyActive = true; missionActive = true
        tracker.counters.increment(RecordType.GNSS, 7)
        now = 11_000L

        tracker.endSession()

        assertEquals(LastSession(10_000L, 7L, 11_000L, "m1"), tracker.lastSession())
        assertEquals(1, endedSessions.size)
    }

    @Test
    fun `a stop that does not end the session records nothing`() {
        tracker.onSurveyStarted(true)
        anyActive = true; missionActive = true
        tracker.onSurveyStopped()
        assertNull(tracker.lastSession())
        assertTrue(endedSessions.isEmpty())
    }

    @Test
    fun `a failed start inside a community only session keeps it reporting saved records`() {
        tracker.onSurveyStarted(false)
        anyActive = true
        val before = tracker.snapshot()
        tracker.onSurveyStarted(true)
        tracker.undoOptimisticStart(before)
        tracker.counters.increment(RecordType.CELLULAR, 400)
        tracker.communityCounters.increment(RecordType.CELLULAR, 3)

        anyActive = false
        tracker.onSurveyStopped()

        assertEquals(3L, tracker.lastSession()!!.totalRecords)
    }

    @Test
    fun `undoing a failed optimistic start is not a session`() {
        val before = tracker.snapshot()
        tracker.onSurveyStarted(true)
        tracker.undoOptimisticStart(before)
        assertNull(tracker.lastSession())
        assertTrue(endedSessions.isEmpty())
    }

    @Test
    fun `a restored last session is kept until a newer one ends`() {
        val restored = LastSession(5_000L, 9L, 500L)
        tracker.restoreLastSession(restored)
        assertEquals(restored, tracker.lastSession())

        tracker.onSurveyStarted(true)
        tracker.endSession()
        assertEquals(LastSession(0L, 0L, 1_000L, "m1"), tracker.lastSession())
    }
}
