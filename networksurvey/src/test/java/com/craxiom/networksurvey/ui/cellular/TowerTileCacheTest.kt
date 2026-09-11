package com.craxiom.networksurvey.ui.cellular

import com.craxiom.networksurvey.data.api.Tower
import com.craxiom.networksurvey.ui.cellular.model.GeoBounds
import com.craxiom.networksurvey.ui.cellular.model.TileKey
import com.craxiom.networksurvey.ui.cellular.model.TowerTileCache
import com.craxiom.networksurvey.ui.cellular.model.TowerWrapper
import com.craxiom.networksurvey.ui.cellular.model.UncoveredReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TowerTileCache], the in-session store of fetched towers keyed by tile.
 */
class TowerTileCacheTest {

    private val ttlMs = 1_000L
    private val now = 10_000L

    private fun tower(
        lat: Double, lon: Double, cid: Long = 1L, mnc: String = "260", updatedAt: Long = 100L,
        samples: Int = 1, source: String = "OpenCelliD"
    ) = TowerWrapper(
        Tower(
            lat = lat, lon = lon, mcc = "310", mnc = mnc, area = 7, cid = cid, unit = 0,
            averageSignal = 0, range = 500, samples = samples, changeable = 1,
            createdAt = 1L, updatedAt = updatedAt, radio = "LTE", source = source
        )
    )

    /** A box exactly covering the tiles around a point, so requests are tile aligned. */
    private fun tileBox(lat: Double, lon: Double): GeoBounds = TileKey.fromLatLng(lat, lon).bounds()

    @Test
    fun `an ingested tower is bucketed by its own coordinates and returned in the snapshot`() {
        val cache = TowerTileCache(towerBudget = 100, ttlMs = ttlMs)
        val t = tower(38.8977, -77.0365)
        cache.ingest(tileBox(38.8977, -77.0365), listOf(t), truncated = false, now = now)

        assertEquals(listOf(t), cache.snapshot())
        assertEquals(t, cache.byId()[t.towerId])
    }

    @Test
    fun `a tile fully inside a complete request is no longer uncovered`() {
        val cache = TowerTileCache(towerBudget = 100, ttlMs = ttlMs)
        val tile = TileKey.fromLatLng(38.8977, -77.0365)
        cache.ingest(tile.bounds(), emptyList(), truncated = false, now = now)

        assertTrue(cache.uncovered(setOf(tile), viewportArea = 1.0, now = now).isEmpty())
    }

    @Test
    fun `a tile that was never fetched is reported absent`() {
        val cache = TowerTileCache(towerBudget = 100, ttlMs = ttlMs)
        val tile = TileKey.fromLatLng(38.8977, -77.0365)

        val uncovered = cache.uncovered(setOf(tile), viewportArea = 1.0, now = now)
        assertEquals(UncoveredReason.ABSENT, uncovered[tile])
    }

    @Test
    fun `a tile older than the ttl is reported expired but its towers are still shown`() {
        val cache = TowerTileCache(towerBudget = 100, ttlMs = ttlMs)
        val tile = TileKey.fromLatLng(38.8977, -77.0365)
        val t = tower(38.8977, -77.0365)
        cache.ingest(tile.bounds(), listOf(t), truncated = false, now = now)

        val later = now + ttlMs + 1
        assertEquals(UncoveredReason.EXPIRED, cache.uncovered(setOf(tile), 1.0, later)[tile])
        assertEquals(listOf(t), cache.snapshot())
    }

    @Test
    fun `a truncated request leaves its tiles incomplete only for a smaller request`() {
        val cache = TowerTileCache(towerBudget = 100, ttlMs = ttlMs)
        val tile = TileKey.fromLatLng(38.8977, -77.0365)
        val request = tile.bounds()
        cache.ingest(request, listOf(tower(38.8977, -77.0365)), truncated = true, now = now)
        val truncatedArea = request.approxAreaSqMeters()

        // Same size request again: do not loop on the server cap.
        assertTrue(cache.uncovered(setOf(tile), truncatedArea, now).isEmpty())
        // A request under half the truncated area is allowed to try again.
        assertEquals(
            UncoveredReason.INCOMPLETE,
            cache.uncovered(setOf(tile), truncatedArea * 0.4, now)[tile]
        )
        assertTrue(cache.anyIncomplete(setOf(tile)))
    }

    @Test
    fun `the same tower fetched twice is replaced when the newer copy has a newer update time`() {
        val cache = TowerTileCache(towerBudget = 100, ttlMs = ttlMs)
        val box = tileBox(38.8977, -77.0365)
        cache.ingest(box, listOf(tower(38.8977, -77.0365, updatedAt = 100, samples = 1)), false, now)
        cache.ingest(box, listOf(tower(38.8977, -77.0365, updatedAt = 200, samples = 9)), false, now)

        assertEquals(1, cache.snapshot().size)
        assertEquals(9, cache.snapshot().single().tower.samples)
    }

    @Test
    fun `an older copy of a tower does not overwrite the newer one`() {
        val cache = TowerTileCache(towerBudget = 100, ttlMs = ttlMs)
        val box = tileBox(38.8977, -77.0365)
        cache.ingest(box, listOf(tower(38.8977, -77.0365, updatedAt = 200, samples = 9)), false, now)
        cache.ingest(box, listOf(tower(38.8977, -77.0365, updatedAt = 100, samples = 1)), false, now)

        assertEquals(9, cache.snapshot().single().tower.samples)
    }

    @Test
    fun `least recently used tiles are evicted as whole tiles once the budget is exceeded`() {
        val cache = TowerTileCache(towerBudget = 3, ttlMs = ttlMs)
        // Two towers in a tile near DC, then two more in a tile near LA.
        cache.ingest(
            tileBox(38.8977, -77.0365),
            listOf(tower(38.8977, -77.0365, cid = 1), tower(38.8978, -77.0366, cid = 2)),
            false, now
        )
        cache.ingest(
            tileBox(34.0522, -118.2437),
            listOf(tower(34.0522, -118.2437, cid = 3), tower(34.0523, -118.2438, cid = 4)),
            false, now + 1
        )

        val remaining = cache.snapshot().map { it.tower.cid }.toSet()
        assertEquals(setOf(3L, 4L), remaining)
        val dcTile = TileKey.fromLatLng(38.8977, -77.0365)
        assertEquals(UncoveredReason.ABSENT, cache.uncovered(setOf(dcTile), 1.0, now + 2)[dcTile])
    }

    @Test
    fun `a viewport hit refreshes recency so the visible tile survives eviction`() {
        val cache = TowerTileCache(towerBudget = 3, ttlMs = ttlMs)
        cache.ingest(
            tileBox(38.8977, -77.0365),
            listOf(tower(38.8977, -77.0365, cid = 1), tower(38.8978, -77.0366, cid = 2)),
            false, now
        )
        cache.ingest(tileBox(40.7128, -74.0060), listOf(tower(40.7128, -74.0060, cid = 5)), false, now + 1)
        // Looking at the DC tile again makes it the most recently used.
        cache.uncovered(setOf(TileKey.fromLatLng(38.8977, -77.0365)), 1.0, now + 2)
        cache.ingest(
            tileBox(34.0522, -118.2437),
            listOf(tower(34.0522, -118.2437, cid = 3), tower(34.0523, -118.2438, cid = 4)),
            false, now + 3
        )

        val remaining = cache.snapshot().map { it.tower.cid }.toSet()
        assertFalse(remaining.contains(5L))
        assertTrue(remaining.containsAll(listOf(3L, 4L)))
    }

    @Test
    fun `clear drops every tile and tower`() {
        val cache = TowerTileCache(towerBudget = 100, ttlMs = ttlMs)
        val tile = TileKey.fromLatLng(38.8977, -77.0365)
        cache.ingest(tile.bounds(), listOf(tower(38.8977, -77.0365)), false, now)
        cache.clear()

        assertTrue(cache.snapshot().isEmpty())
        assertEquals(UncoveredReason.ABSENT, cache.uncovered(setOf(tile), 1.0, now)[tile])
    }

    @Test
    fun `the snapshot version only changes when the content changes`() {
        val cache = TowerTileCache(towerBudget = 100, ttlMs = ttlMs)
        val box = tileBox(38.8977, -77.0365)
        val t = tower(38.8977, -77.0365)
        cache.ingest(box, listOf(t), false, now)
        val afterFirst = cache.version
        cache.ingest(box, listOf(t), false, now + 1)

        assertEquals(afterFirst, cache.version)
        assertTrue(afterFirst > 0)
    }

    @Test
    fun `tower count in a set of tiles only counts those tiles`() {
        val cache = TowerTileCache(towerBudget = 100, ttlMs = ttlMs)
        cache.ingest(tileBox(38.8977, -77.0365), listOf(tower(38.8977, -77.0365, cid = 1)), false, now)
        cache.ingest(tileBox(34.0522, -118.2437), listOf(tower(34.0522, -118.2437, cid = 2)), false, now)

        val dcTile = TileKey.fromLatLng(38.8977, -77.0365)
        val emptyTile = TileKey.fromLatLng(45.0, -100.0)
        assertEquals(1, cache.towerCountIn(setOf(dcTile, emptyTile)))
        assertEquals(0, cache.towerCountIn(setOf(emptyTile)))
    }
}
