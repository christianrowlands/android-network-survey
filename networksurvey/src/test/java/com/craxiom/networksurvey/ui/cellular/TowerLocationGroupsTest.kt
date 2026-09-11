package com.craxiom.networksurvey.ui.cellular

import com.craxiom.networksurvey.data.api.Tower
import com.craxiom.networksurvey.ui.cellular.model.LocationBadge
import com.craxiom.networksurvey.ui.cellular.model.TowerLocationGroups
import com.craxiom.networksurvey.ui.cellular.model.TowerWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TowerLocationGroups], which decides which tower at a shared location carries
 * the count badges.
 */
class TowerLocationGroupsTest {

    private fun tower(lat: Double, lon: Double, cid: Long, mnc: String = "260") = TowerWrapper(
        Tower(
            lat = lat, lon = lon, mcc = "310", mnc = mnc, area = 7, cid = cid, unit = 0,
            averageSignal = 0, range = 500, samples = 1, changeable = 1,
            createdAt = 1L, updatedAt = 1L, radio = "LTE", source = "OpenCelliD"
        )
    )

    @Test
    fun `towers at distinct locations get no badge`() {
        val badges = TowerLocationGroups.compute(listOf(tower(1.0, 1.0, 1), tower(1.0, 2.0, 2)))
        assertTrue(badges.isEmpty())
    }

    @Test
    fun `two towers of one operator at the same spot badge the first tower only`() {
        val a = tower(1.0, 1.0, 1)
        val b = tower(1.0, 1.0, 2)
        val badges = TowerLocationGroups.compute(listOf(a, b))

        assertEquals(mapOf(a.towerId to LocationBadge(towerCount = 2, operatorCount = 1)), badges)
    }

    @Test
    fun `two operators at the same spot are counted as two operators`() {
        val a = tower(1.0, 1.0, 1, mnc = "260")
        val b = tower(1.0, 1.0, 2, mnc = "410")
        val badges = TowerLocationGroups.compute(listOf(a, b))

        assertEquals(LocationBadge(towerCount = 2, operatorCount = 2), badges[a.towerId])
    }
}
