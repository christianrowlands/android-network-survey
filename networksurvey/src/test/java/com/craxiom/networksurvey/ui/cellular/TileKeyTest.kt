package com.craxiom.networksurvey.ui.cellular

import com.craxiom.networksurvey.ui.cellular.model.GeoBounds
import com.craxiom.networksurvey.ui.cellular.model.TOWER_TILE_ZOOM
import com.craxiom.networksurvey.ui.cellular.model.TileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TileKey] and [GeoBounds], the slippy tile math behind the tower tile cache.
 */
class TileKeyTest {

    @Test
    fun `the origin maps to the center tile of the zoom level`() {
        val half = 1 shl (TOWER_TILE_ZOOM - 1)
        assertEquals(TileKey(half, half), TileKey.fromLatLng(0.0, 0.0))
    }

    @Test
    fun `a point is inside the bounds of its own tile`() {
        val lat = 38.8977
        val lon = -77.0365
        val bounds = TileKey.fromLatLng(lat, lon).bounds()
        assertTrue(bounds.south <= lat && lat < bounds.north)
        assertTrue(bounds.west <= lon && lon < bounds.east)
    }

    @Test
    fun `tiles covering a box just inside one tile is that single tile`() {
        val tile = TileKey.fromLatLng(38.8977, -77.0365)
        val b = tile.bounds()
        val inner = GeoBounds(
            south = b.south + 0.0001, west = b.west + 0.0001,
            north = b.north - 0.0001, east = b.east - 0.0001
        )
        assertEquals(setOf(tile), TileKey.tilesCovering(inner))
    }

    @Test
    fun `tiles covering a box spanning two columns and three rows returns six tiles`() {
        val origin = TileKey.fromLatLng(38.8977, -77.0365)
        val sw = TileKey(origin.x, origin.y + 2).bounds()
        val ne = TileKey(origin.x + 1, origin.y).bounds()
        val box = GeoBounds(
            south = sw.south + 0.0001, west = sw.west + 0.0001,
            north = ne.north - 0.0001, east = ne.east - 0.0001
        )
        val tiles = TileKey.tilesCovering(box)
        assertEquals(6, tiles.size)
        assertTrue(tiles.contains(origin))
        assertTrue(tiles.contains(TileKey(origin.x + 1, origin.y + 2)))
    }

    @Test
    fun `a box crossing the antimeridian yields no tiles`() {
        val box = GeoBounds(south = 10.0, west = 170.0, north = 20.0, east = -170.0)
        assertTrue(TileKey.tilesCovering(box).isEmpty())
    }

    @Test
    fun `polar latitudes are clamped instead of producing out of range tiles`() {
        val max = (1 shl TOWER_TILE_ZOOM) - 1
        val north = TileKey.fromLatLng(89.9, 10.0)
        val south = TileKey.fromLatLng(-89.9, 10.0)
        assertTrue(north.y in 0..max)
        assertTrue(south.y in 0..max)
    }

    @Test
    fun `bounds union covers both inputs`() {
        val a = GeoBounds(south = 1.0, west = 1.0, north = 2.0, east = 2.0)
        val b = GeoBounds(south = 3.0, west = -1.0, north = 4.0, east = 0.5)
        assertEquals(GeoBounds(south = 1.0, west = -1.0, north = 4.0, east = 2.0), a.union(b))
    }

    @Test
    fun `approximate area of a one degree box at the equator is about 12400 square kilometers`() {
        val box = GeoBounds(south = -0.5, west = -0.5, north = 0.5, east = 0.5)
        val areaSqKm = box.approxAreaSqMeters() / 1_000_000.0
        assertTrue("was $areaSqKm", areaSqKm > 12_000 && areaSqKm < 12_800)
    }
}
