package com.craxiom.networksurvey.survey.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val tracker = MissionSessionTracker(
        generateMissionId = { "m${++counter}" },
        isAnySurveyActive = { anyActive },
        isAnyMissionRelevantSurveyActive = { missionActive },
        onMissionIdChanged = { id, active -> changes += id to active },
        clock = { 1_000L },
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
        tracker.onSurveyStarted(true)
        assertEquals(0, tracker.recordCount.get())
        assertEquals(0L, tracker.counters.total())
    }
}
