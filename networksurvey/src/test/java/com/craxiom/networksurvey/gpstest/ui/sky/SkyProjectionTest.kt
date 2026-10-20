package com.craxiom.networksurvey.gpstest.ui.sky

import com.craxiom.networksurvey.gpstest.model.GnssType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** The sky plot's projection, marks and C/N0 color scale. */
class SkyProjectionTest {

    private val tolerance = 1e-4f

    private fun assertPoint(expectedX: Float, expectedY: Float, point: SkyPoint?) {
        assertNotNull(point)
        assertEquals(expectedX, point!!.x, tolerance)
        assertEquals(expectedY, point.y, tolerance)
    }

    @Test
    fun theZenith_isTheCenter_atAnyAzimuth() {
        assertPoint(0f, 0f, SkyProjection.project(90f, 0f))
        assertPoint(0f, 0f, SkyProjection.project(90f, 217f))
    }

    @Test
    fun theHorizon_isTheEdge_northUpEastRight() {
        assertPoint(0f, -1f, SkyProjection.project(0f, 360f))
        assertPoint(1f, 0f, SkyProjection.project(0f, 90f))
        assertPoint(0f, 1f, SkyProjection.project(0f, 180f))
        assertPoint(-1f, 0f, SkyProjection.project(0f, 270f))
    }

    @Test
    fun eachQuadrant_atHalfElevation_isHalfwayOut() {
        val d = 0.5f / sqrt(2f)
        assertPoint(d, -d, SkyProjection.project(45f, 45f))
        assertPoint(d, d, SkyProjection.project(45f, 135f))
        assertPoint(-d, d, SkyProjection.project(45f, 225f))
        assertPoint(-d, -d, SkyProjection.project(45f, 315f))
    }

    @Test
    fun facingEast_northMovesToTheLeft() {
        assertPoint(-1f, 0f, SkyProjection.project(0f, 360f, headingDegrees = 90f))
        assertPoint(0f, -1f, SkyProjection.project(0f, 90f, headingDegrees = 90f))
    }

    @Test
    fun elevationsOutsideTheSky_areClamped() {
        assertPoint(0f, -1f, SkyProjection.project(-5f, 360f))
        assertPoint(0f, 0f, SkyProjection.project(95f, 10f))
    }

    @Test
    fun noAngles_hasNoPoint() {
        assertNull(SkyProjection.project(0f, 0f))
        assertNull(SkyProjection.project(Float.NaN, 10f))
        // One real angle of 0 is a real position.
        assertPoint(0f, -0.5f, SkyProjection.project(45f, 0f))
    }

    @Test
    fun eachConstellation_hasItsMark() {
        assertEquals(SatelliteShape.CIRCLE, SkyProjection.shape(GnssType.NAVSTAR))
        assertEquals(SatelliteShape.SQUARE, SkyProjection.shape(GnssType.GLONASS))
        assertEquals(SatelliteShape.TRIANGLE, SkyProjection.shape(GnssType.GALILEO))
        assertEquals(SatelliteShape.PENTAGON, SkyProjection.shape(GnssType.BEIDOU))
        assertEquals(SatelliteShape.HEXAGON, SkyProjection.shape(GnssType.QZSS))
        assertEquals(SatelliteShape.OVAL, SkyProjection.shape(GnssType.IRNSS))
        assertEquals(SatelliteShape.DIAMOND, SkyProjection.shape(GnssType.SBAS))
        assertEquals(SatelliteShape.NONE, SkyProjection.shape(GnssType.UNKNOWN))
    }

    @Test
    fun cn0_atAndBeyondTheEnds_isTheEndStop() {
        assertEquals(Cn0Stop(0, 0f), SkyProjection.cn0Stop(5f))
        assertEquals(Cn0Stop(0, 0f), SkyProjection.cn0Stop(10f))
        assertEquals(Cn0Stop(3, 0f), SkyProjection.cn0Stop(45f))
        assertEquals(Cn0Stop(3, 0f), SkyProjection.cn0Stop(52f))
    }

    @Test
    fun cn0_atEachInnerStop_startsItsSegment() {
        assertEquals(Cn0Stop(1, 0f), SkyProjection.cn0Stop(21.67f))
        assertEquals(Cn0Stop(2, 0f), SkyProjection.cn0Stop(33.3f))
    }

    @Test
    fun cn0_betweenStops_isTheFractionOfTheWay() {
        val stop = SkyProjection.cn0Stop(39.15f)
        assertEquals(2, stop.index)
        assertEquals(0.5f, stop.fraction, tolerance)
        val low = SkyProjection.cn0Stop(15.835f)
        assertEquals(0, low.index)
        assertEquals(0.5f, low.fraction, tolerance)
    }

    @Test
    fun theMeter_runsFrom10To45() {
        assertEquals(0f, SkyProjection.meterFraction(3f), tolerance)
        assertEquals(0f, SkyProjection.meterFraction(10f), tolerance)
        assertEquals(0.5f, SkyProjection.meterFraction(27.5f), tolerance)
        assertEquals(1f, SkyProjection.meterFraction(45f), tolerance)
        assertEquals(1f, SkyProjection.meterFraction(50f), tolerance)
    }

    @Test
    fun aSatellite_isInView_onlyWithASignal() {
        assertFalse(SkyProjection.inView(0f))
        assertTrue(SkyProjection.inView(0.5f))
    }
}
