package com.craxiom.networksurvey.ui.activesurvey

import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.TowerKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Community upload session and the towers the Map tags NEW: only during an upload, cleared when
 * one starts or stops.
 */
class NewTowerRegistryTest {

    private val tower = TowerKey("310", "260", 12345, 25_600_103L, CellularProtocol.LTE)
    private val another = TowerKey("310", "260", 12345, 25_600_104L, CellularProtocol.LTE)

    @Before
    fun noUploadRunning() {
        // The registry is process-wide; each test starts with no session
        NewTowerRegistry.endSession()
    }

    @Test
    fun aNewTower_isRecordedDuringAnUpload() {
        val session = NewTowerRegistry.startSession()
        assertTrue(NewTowerRegistry.add(tower, session))
        assertTrue(NewTowerRegistry.add(another, session))

        assertEquals(setOf(tower, another), NewTowerRegistry.newTowers.value)
    }

    @Test
    fun aTowerAlreadyRecorded_isNotAddedAgain() {
        val session = NewTowerRegistry.startSession()
        assertTrue(NewTowerRegistry.add(tower, session))
        assertFalse(NewTowerRegistry.add(tower, session))

        assertEquals(setOf(tower), NewTowerRegistry.newTowers.value)
    }

    @Test
    fun nothingIsRecorded_whileNoUploadRuns() {
        assertFalse(NewTowerRegistry.add(tower, 1_000L))

        assertTrue(NewTowerRegistry.newTowers.value.isEmpty())
    }

    @Test
    fun aCheckThatFinishesAfterTheUploadStops_isIgnored() {
        val session = NewTowerRegistry.startSession()
        NewTowerRegistry.endSession()
        assertFalse(NewTowerRegistry.add(tower, session))

        assertTrue(NewTowerRegistry.newTowers.value.isEmpty())
    }

    @Test
    fun aCheckFromAnEarlierSession_isIgnored_afterARestart() {
        val stopped = NewTowerRegistry.startSession(1_000L)
        val running = NewTowerRegistry.startSession(2_000L)
        assertFalse(NewTowerRegistry.add(tower, stopped))
        assertTrue(NewTowerRegistry.add(tower, running))

        NewTowerRegistry.remove(tower, stopped)
        assertEquals("Removing for another session does nothing", setOf(tower), NewTowerRegistry.newTowers.value)
        NewTowerRegistry.remove(tower, running)
        assertTrue(NewTowerRegistry.newTowers.value.isEmpty())
    }

    @Test
    fun eachSession_isKnownByWhenItStarted_untilItEnds() {
        assertNull(NewTowerRegistry.sessionStartedAt)

        assertEquals(1_234L, NewTowerRegistry.startSession(1_234L))
        assertEquals(1_234L, NewTowerRegistry.sessionStartedAt)

        assertEquals("Ending returns the ended session", 1_234L, NewTowerRegistry.endSession())
        assertNull(NewTowerRegistry.sessionStartedAt)
        assertNull("Ending again finds no session", NewTowerRegistry.endSession())
    }

    @Test
    fun startingAndStopping_clearTheTowers() {
        NewTowerRegistry.add(tower, NewTowerRegistry.startSession(1_000L))
        val restarted = NewTowerRegistry.startSession(2_000L)
        assertTrue(NewTowerRegistry.newTowers.value.isEmpty())

        NewTowerRegistry.add(another, restarted)
        NewTowerRegistry.endSession()
        assertTrue(NewTowerRegistry.newTowers.value.isEmpty())
    }

    @Test
    fun towersAreOnlyChecked_whileAlertsAreOnAndAnUploadRuns() {
        assertTrue(NewTowerRegistry.shouldCheck(alertsEnabled = true, uploadRunning = true))
        assertFalse(NewTowerRegistry.shouldCheck(alertsEnabled = false, uploadRunning = true))
        assertFalse(NewTowerRegistry.shouldCheck(alertsEnabled = true, uploadRunning = false))
        assertFalse(NewTowerRegistry.shouldCheck(alertsEnabled = false, uploadRunning = false))
    }
}
