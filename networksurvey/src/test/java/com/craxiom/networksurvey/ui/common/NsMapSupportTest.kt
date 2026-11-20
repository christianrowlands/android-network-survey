package com.craxiom.networksurvey.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The camera framing and color helpers shared by the small MapLibre maps. */
class NsMapSupportTest {

    @Test
    fun noPoints_noFit() {
        assertNull(mapFitFor(emptyList(), MAX_ZOOM))
    }

    @Test
    fun onePoint_isCenteredAtTheMaxZoom() {
        val point = MapPoint(38.9, -77.0)
        assertEquals(MapFit.Center(point, MAX_ZOOM), mapFitFor(listOf(point), MAX_ZOOM))
    }

    @Test
    fun pointsAtOneSpot_areCenteredRatherThanFittedToAnEmptyBox() {
        val point = MapPoint(38.9, -77.0)
        assertEquals(MapFit.Center(point, MAX_ZOOM), mapFitFor(listOf(point, point.copy()), MAX_ZOOM))
    }

    @Test
    fun spreadPoints_fitTheBoxThatHoldsThemAll() {
        val fit = mapFitFor(listOf(MapPoint(38.9, -77.0), MapPoint(39.2, -77.4), MapPoint(38.7, -76.8)), MAX_ZOOM)
        assertEquals(MapFit.Bounds(south = 38.7, west = -77.4, north = 39.2, east = -76.8), fit)
    }

    @Test
    fun colorHex_dropsTheAlpha() {
        assertEquals("#1E88E5", mapColorHex(0xFF1E88E5.toInt()))
        assertEquals("#00000A", mapColorHex(0x8000000A.toInt()))
    }

    @Test
    fun theKeylessStyle_followsTheTheme() {
        assertEquals(NsMapStyles.OPENFREEMAP_FIORD, NsMapStyles.keyless(dark = true))
        assertEquals(NsMapStyles.OPENFREEMAP_LIBERTY, NsMapStyles.keyless(dark = false))
    }

    private companion object {
        const val MAX_ZOOM = 16.0
    }
}
