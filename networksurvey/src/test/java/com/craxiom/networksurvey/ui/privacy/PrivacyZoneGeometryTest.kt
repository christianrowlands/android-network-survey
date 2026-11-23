package com.craxiom.networksurvey.ui.privacy

import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.privacy.PrivacyZoneRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The radius slider's scale and snapping, and the ring that draws a zone's circle. */
class PrivacyZoneGeometryTest {

    @Test
    fun `the slider ends are the smallest and largest radius`() {
        assertEquals(PrivacyZone.MIN_RADIUS_METERS, PrivacyZoneGeometry.radiusForSlider(0f))
        assertEquals(PrivacyZone.MAX_RADIUS_METERS, PrivacyZoneGeometry.radiusForSlider(1f))
        assertEquals(PrivacyZone.MIN_RADIUS_METERS, PrivacyZoneGeometry.radiusForSlider(-1f))
        assertEquals(PrivacyZone.MAX_RADIUS_METERS, PrivacyZoneGeometry.radiusForSlider(2f))
    }

    @Test
    fun `the default radius sits on the track and comes back unchanged`() {
        val position = PrivacyZoneGeometry.sliderForRadius(PrivacyZone.DEFAULT_RADIUS_METERS)
        assertTrue(position > 0f && position < 0.5f)
        assertEquals(PrivacyZone.DEFAULT_RADIUS_METERS, PrivacyZoneGeometry.radiusForSlider(position))
    }

    @Test
    fun `small radii get most of the track`() {
        // The log scale puts 450 m (under a quarter of the range) about halfway along
        assertTrue(PrivacyZoneGeometry.radiusForSlider(0.5f) in 400..500)
    }

    @Test
    fun `a small accessibility step never gets stuck`() {
        // One percent up from 100 m rounds back to 100 m on the log scale; it still moves
        val fromMin = PrivacyZoneGeometry.sliderForRadius(PrivacyZone.MIN_RADIUS_METERS) + 0.01f
        assertEquals(110, PrivacyZoneGeometry.radiusForAdjustment(PrivacyZone.MIN_RADIUS_METERS, fromMin))
        val belowMax = PrivacyZoneGeometry.sliderForRadius(PrivacyZone.MAX_RADIUS_METERS) - 0.001f
        assertEquals(1950, PrivacyZoneGeometry.radiusForAdjustment(PrivacyZone.MAX_RADIUS_METERS, belowMax))
        // A big step goes where it points
        assertEquals(PrivacyZone.MAX_RADIUS_METERS, PrivacyZoneGeometry.radiusForAdjustment(250, 1f))
        // Every step up from the smallest radius reaches the largest
        var radius = PrivacyZone.MIN_RADIUS_METERS
        var steps = 0
        while (radius < PrivacyZone.MAX_RADIUS_METERS && steps < 500) {
            radius = PrivacyZoneGeometry.radiusForAdjustment(radius, PrivacyZoneGeometry.sliderForRadius(radius) + 0.01f)
            steps++
        }
        assertEquals(PrivacyZone.MAX_RADIUS_METERS, radius)
    }

    @Test
    fun `a step crosses the snap size boundaries both ways`() {
        assertEquals(500, PrivacyZoneGeometry.steppedRadius(490, up = true))
        assertEquals(525, PrivacyZoneGeometry.steppedRadius(500, up = true))
        assertEquals(490, PrivacyZoneGeometry.steppedRadius(500, up = false))
        assertEquals(975, PrivacyZoneGeometry.steppedRadius(1000, up = false))
        assertEquals(PrivacyZone.MIN_RADIUS_METERS, PrivacyZoneGeometry.steppedRadius(PrivacyZone.MIN_RADIUS_METERS, up = false))
        assertEquals(PrivacyZone.MAX_RADIUS_METERS, PrivacyZoneGeometry.steppedRadius(PrivacyZone.MAX_RADIUS_METERS, up = true))
    }

    @Test
    fun `radii snap to round numbers by size`() {
        assertEquals(250, PrivacyZoneGeometry.snapRadius(253))
        assertEquals(725, PrivacyZoneGeometry.snapRadius(733))
        assertEquals(1550, PrivacyZoneGeometry.snapRadius(1538))
        assertEquals(PrivacyZone.MIN_RADIUS_METERS, PrivacyZoneGeometry.snapRadius(12))
        assertEquals(PrivacyZone.MAX_RADIUS_METERS, PrivacyZoneGeometry.snapRadius(5_000))
    }

    @Test
    fun `every slider position gives a snapped radius in range`() {
        (0..100).map { it / 100f }.forEach { position ->
            val radius = PrivacyZoneGeometry.radiusForSlider(position)
            assertTrue(radius in PrivacyZone.MIN_RADIUS_METERS..PrivacyZone.MAX_RADIUS_METERS)
            assertEquals(radius, PrivacyZoneGeometry.snapRadius(radius))
        }
    }

    @Test
    fun `the ring is closed and every point is the radius from the center`() {
        listOf(0.0, 38.9, -33.9, 64.1).forEach { latitude ->
            val ring = PrivacyZoneGeometry.ring(latitude, -77.0, 500)
            assertEquals(PrivacyZoneGeometry.RING_SEGMENTS + 1, ring.size)
            assertEquals(ring.first(), ring.last())
            ring.forEach { point ->
                val distance = PrivacyZoneRules.distanceMeters(latitude, -77.0, point.latitude, point.longitude)
                assertTrue("$distance m at latitude $latitude", abs(distance - 500) < 1.0)
            }
        }
    }

    @Test
    fun `the ring wraps across the antimeridian`() {
        val ring = PrivacyZoneGeometry.ring(0.0, 179.999, 1000)
        ring.forEach { assertTrue(it.longitude in -180.0..180.0) }
    }

    @Test
    fun `the edges are the four compass points`() {
        val edges = PrivacyZoneGeometry.edges(10.0, 20.0, 1000)
        assertEquals(4, edges.size)
        assertTrue(edges[0].latitude > 10.0) // north
        assertTrue(edges[1].longitude > 20.0) // east
        assertTrue(edges[2].latitude < 10.0) // south
        assertTrue(edges[3].longitude < 20.0) // west
    }
}
