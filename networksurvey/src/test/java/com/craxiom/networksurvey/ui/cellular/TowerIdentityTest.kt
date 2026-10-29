package com.craxiom.networksurvey.ui.cellular

import com.craxiom.networksurvey.ui.cellular.model.TowerIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for [TowerIdentity]. The id used to be a bare concatenation of MCC, MNC, area and
 * cell id, which collides between two and three digit MNCs.
 */
class TowerIdentityTest {

    @Test
    fun `a two digit and a three digit mnc with shifted digits do not collide`() {
        val a = TowerIdentity.towerId("310", "41", 1, 123L)
        val b = TowerIdentity.towerId("310", "411", 1, 23L)
        assertNotEquals(a, b)
    }

    @Test
    fun `an area and cell id with shifted digits do not collide`() {
        val a = TowerIdentity.towerId("310", "410", 12, 345L)
        val b = TowerIdentity.towerId("310", "410", 123, 45L)
        assertNotEquals(a, b)
    }

    @Test
    fun `the map id matches the same network at either mnc width`() {
        assertEquals(TowerIdentity.mapId("440", "10", 1, 2L), TowerIdentity.mapId("440", "010", 1, 2L))
        assertEquals(TowerIdentity.mapId("460", "00", 1, 2L), TowerIdentity.mapId("460", "000", 1, 2L))
        assertEquals(TowerIdentity.mapId("315", "010", 1, 2L), TowerIdentity.mapId("315", "10", 1, 2L))
    }

    @Test
    fun `the map id keeps different networks in one mcc apart`() {
        assertNotEquals(TowerIdentity.mapId("310", "010", 1, 2L), TowerIdentity.mapId("310", "100", 1, 2L))
        assertNotEquals(TowerIdentity.mapId("310", "41", 1, 123L), TowerIdentity.mapId("310", "411", 1, 23L))
    }

    @Test
    fun `the stored tower id keeps the reported mnc`() {
        assertEquals("310-04-12345-67890123", TowerIdentity.towerId("310", "04", 12345, 67890123L))
    }
}
