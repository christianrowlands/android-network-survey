package com.craxiom.networksurvey.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure inside/outside rules: distance, usable fixes, the fail-closed test and several zones. */
class PrivacyZoneRulesTest {

    private val home = zone("home", 40.0, -105.0, 250)

    @Test
    fun `the haversine distance matches known values`() {
        assertEquals(0.0, PrivacyZoneRules.distanceMeters(40.0, -105.0, 40.0, -105.0), 1e-9)
        // One degree of latitude on the mean Earth radius
        assertEquals(111_195.08, PrivacyZoneRules.distanceMeters(0.0, 0.0, 1.0, 0.0), 0.01)
        // Denver to Boulder, about 39 km
        assertEquals(38_900.0, PrivacyZoneRules.distanceMeters(39.7392, -104.9903, 40.0150, -105.2705), 300.0)
        // Across the antimeridian is short, not most of the globe
        assertEquals(22_239.0, PrivacyZoneRules.distanceMeters(0.0, 179.9, 0.0, -179.9), 2.0)
    }

    @Test
    fun `a point inside the radius is inside and one just past it is not`() {
        assertTrue(contains(home, north(home, 249.0), 0f))
        assertFalse(contains(home, north(home, 251.0), 0f))
    }

    @Test
    fun `accuracy widens the test so a coarse fix near a zone fails closed`() {
        val outside = north(home, 400.0)
        assertFalse(contains(home, outside, 140f))
        assertTrue(contains(home, outside, 160f))
    }

    @Test
    fun `missing or negative accuracy does not widen the test`() {
        val justOutside = north(home, 260.0)
        assertFalse(contains(home, justOutside, null))
        assertFalse(contains(home, justOutside, -50f))
    }

    @Test
    fun `a usable fix needs an accuracy of 500 m or better`() {
        assertTrue(PrivacyZoneRules.isUsableFix(fix(home, accuracy = 500f, takenAt = NOW), NOW))
        assertFalse(PrivacyZoneRules.isUsableFix(fix(home, accuracy = 501f, takenAt = NOW), NOW))
        assertFalse(PrivacyZoneRules.isUsableFix(fix(home, accuracy = null, takenAt = NOW), NOW))
        assertFalse(PrivacyZoneRules.isUsableFix(fix(home, accuracy = Float.NaN, takenAt = NOW), NOW))
    }

    @Test
    fun `a usable fix is at most 60 s old`() {
        assertTrue(PrivacyZoneRules.isUsableFix(fix(home, takenAt = NOW - 60_000), NOW))
        assertFalse(PrivacyZoneRules.isUsableFix(fix(home, takenAt = NOW - 60_001), NOW))
        assertFalse(PrivacyZoneRules.isUsableFix(fix(home, takenAt = null), NOW))
    }

    @Test
    fun `no fix or an off-globe fix is not usable`() {
        assertFalse(PrivacyZoneRules.isUsableFix(null, NOW))
        assertFalse(PrivacyZoneRules.isUsableFix(PrivacyZoneFix(91.0, 0.0, 10f, NOW), NOW))
    }

    @Test
    fun `with several zones the nearest center names the zone`() {
        val park = zone("park", north(home, 300.0).first, -105.0, 250)
        // 140 m north of home is inside both; home's center (160 m from the park's) is nearer
        val between = north(home, 140.0)
        assertEquals("home", PrivacyZoneRules.zoneContaining(listOf(park, home), between.first, between.second, 0f)?.id)
        // 290 m north is only in the park
        val parkSide = north(home, 290.0)
        assertEquals("park", PrivacyZoneRules.zoneContaining(listOf(home, park), parkSide.first, parkSide.second, 0f)?.id)
        val farAway = north(home, 5_000.0)
        assertNull(PrivacyZoneRules.zoneContaining(listOf(home, park), farAway.first, farAway.second, 0f))
    }

    @Test
    fun `no zones means never inside`() {
        assertNull(PrivacyZoneRules.zoneContaining(emptyList(), 40.0, -105.0, 0f))
    }

    @Test
    fun `the radius is limited to 100 m to 2 km`() {
        assertEquals(100, PrivacyZone.clampRadius(25))
        assertEquals(250, PrivacyZone.clampRadius(250))
        assertEquals(2000, PrivacyZone.clampRadius(10_000))
    }

    private fun contains(zone: PrivacyZone, point: Pair<Double, Double>, accuracy: Float?) =
        PrivacyZoneRules.contains(zone, point.first, point.second, accuracy)

    companion object {
        const val NOW = 1_000_000L

        fun zone(id: String, lat: Double, lon: Double, radius: Int) = PrivacyZone(id, id, lat, lon, radius)

        /** @return The point [meters] due north of [zone]'s center. */
        fun north(zone: PrivacyZone, meters: Double): Pair<Double, Double> =
            zone.latitude + Math.toDegrees(meters / 6_371_008.8) to zone.longitude

        fun fix(zone: PrivacyZone, meters: Double = 0.0, accuracy: Float? = 10f, takenAt: Long? = NOW): PrivacyZoneFix {
            val (lat, lon) = north(zone, meters)
            return PrivacyZoneFix(lat, lon, accuracy, takenAt)
        }
    }
}
