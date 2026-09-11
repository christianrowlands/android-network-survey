package com.craxiom.networksurvey.ui.cellular

import com.craxiom.networksurvey.ui.cellular.model.TowerIdentity
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
}
