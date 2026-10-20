package com.craxiom.networksurvey.ui.activesurvey

import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.TowerKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The towers the Map tags NEW: only during a Community upload, cleared when one starts or stops. */
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
        NewTowerRegistry.startSession()
        NewTowerRegistry.add(tower)
        NewTowerRegistry.add(another)

        assertEquals(setOf(tower, another), NewTowerRegistry.newTowers.value)
    }

    @Test
    fun nothingIsRecorded_whileNoUploadRuns() {
        NewTowerRegistry.add(tower)

        assertTrue(NewTowerRegistry.newTowers.value.isEmpty())
    }

    @Test
    fun aCheckThatFinishesAfterTheUploadStops_isIgnored() {
        NewTowerRegistry.startSession()
        NewTowerRegistry.endSession()
        NewTowerRegistry.add(tower)

        assertTrue(NewTowerRegistry.newTowers.value.isEmpty())
    }

    @Test
    fun startingAndStopping_clearTheTowers() {
        NewTowerRegistry.startSession()
        NewTowerRegistry.add(tower)
        NewTowerRegistry.startSession()
        assertTrue(NewTowerRegistry.newTowers.value.isEmpty())

        NewTowerRegistry.add(another)
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
