package com.craxiom.networksurvey.ui.cellular

import com.craxiom.networksurvey.ui.cellular.model.buildBboxParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [buildBboxParam].
 *
 * These cover the two invalid bounding box shapes that were observed reaching the NS Tower
 * Service in production and being rejected with a 400: the all zero region reported by a map
 * that has not been laid out, and a world scale region whose longitude runs past 180.
 */
class TowerBboxParamTest {

    @Test
    fun `a normal box is formatted southWest first`() {
        val result = buildBboxParam(7.32, 80.06, 7.33, 80.07)
        assertEquals("7.32,80.06,7.33,80.07", result)
    }

    @Test
    fun `an all zero box is rejected`() {
        // What MapLibre reports before the map surface has been laid out. This was the large
        // majority of the observed 400s.
        assertNull(buildBboxParam(0.0, 0.0, 0.0, 0.0))
    }

    @Test
    fun `a longitude past the antimeridian is rejected rather than clamped`() {
        // Observed in production on 2026-08-18. Clamping 227.15 to 180 would produce a box the
        // server accepts, silently answering a world scale view with a truncated slice.
        assertNull(
            buildBboxParam(
                -84.00991087257029,
                75.27771647985139,
                69.1583853624048,
                227.15271499427757
            )
        )
    }

    @Test
    fun `a box with zero height is rejected`() {
        assertNull(buildBboxParam(7.32, 80.06, 7.32, 80.07))
    }

    @Test
    fun `a box with zero width is rejected`() {
        assertNull(buildBboxParam(7.32, 80.06, 7.33, 80.06))
    }

    @Test
    fun `an inverted box is rejected`() {
        assertNull(buildBboxParam(7.33, 80.07, 7.32, 80.06))
    }

    @Test
    fun `out of range latitude is rejected`() {
        assertNull(buildBboxParam(-91.0, 80.06, 7.33, 80.07))
        assertNull(buildBboxParam(7.32, 80.06, 91.0, 80.07))
    }

    @Test
    fun `out of range longitude is rejected on either corner`() {
        assertNull(buildBboxParam(7.32, -180.1, 7.33, 80.07))
        assertNull(buildBboxParam(7.32, 80.06, 7.33, 180.1))
    }

    @Test
    fun `non finite values are rejected`() {
        assertNull(buildBboxParam(Double.NaN, 80.06, 7.33, 80.07))
        assertNull(buildBboxParam(7.32, Double.NEGATIVE_INFINITY, 7.33, 80.07))
        assertNull(buildBboxParam(7.32, 80.06, Double.POSITIVE_INFINITY, 80.07))
    }

    @Test
    fun `a zoomed in view crossing the antimeridian is rejected`() {
        // MapLibre reports unwrapped longitudes, so a small view sitting on the antimeridian
        // comes through as east > 180 rather than as a wrapped negative value. The tower
        // service cannot express this box either, so it is rejected. Known limitation:
        // users on the antimeridian see no towers. Supporting them needs the query split
        // into two boxes, which is a separate change.
        assertNull(buildBboxParam(-17.8, 179.9, -17.7, 180.1))
    }

    @Test
    fun `a wrapped antimeridian box is rejected`() {
        // The other shape the same view could arrive in, with east already wrapped negative.
        assertNull(buildBboxParam(-17.8, 179.9, -17.7, -179.9))
    }

    @Test
    fun `the extreme valid box is accepted`() {
        assertEquals("-90.0,-180.0,90.0,180.0", buildBboxParam(-90.0, -180.0, 90.0, 180.0))
    }
}
