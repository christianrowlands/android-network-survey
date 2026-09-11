package com.craxiom.networksurvey.ui.cellular

import com.craxiom.networksurvey.ui.cellular.model.GeoBounds
import com.craxiom.networksurvey.ui.cellular.model.TileKey
import com.craxiom.networksurvey.ui.cellular.model.TowerFetchPlanner
import com.craxiom.networksurvey.ui.cellular.model.UncoveredReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [TowerFetchPlanner], which turns the cache's view of the viewport into at most
 * one request box.
 */
class TowerFetchPlannerTest {

    private val origin = TileKey.fromLatLng(38.8977, -77.0365)

    /** A 3 by 3 grid of tiles with [origin] at the top left. */
    private val grid: Set<TileKey> =
        (0..2).flatMap { dx -> (0..2).map { dy -> TileKey(origin.x + dx, origin.y + dy) } }.toSet()

    private fun unionOf(tiles: Collection<TileKey>): GeoBounds =
        tiles.map { it.bounds() }.reduce { acc, b -> acc.union(b) }

    @Test
    fun `a fully covered viewport needs no request`() {
        assertNull(TowerFetchPlanner.plan(grid, emptyMap()))
    }

    @Test
    fun `an uncovered column after a pan is requested as a strip`() {
        val column = grid.filter { it.x == origin.x + 2 }
        val uncovered = column.associateWith { UncoveredReason.ABSENT }

        assertEquals(unionOf(column), TowerFetchPlanner.plan(grid, uncovered))
    }

    @Test
    fun `when most of the viewport is uncovered the whole viewport is requested`() {
        val eight = grid.filterNot { it == origin }
        val uncovered = eight.associateWith { UncoveredReason.ABSENT }

        assertEquals(unionOf(grid), TowerFetchPlanner.plan(grid, uncovered))
    }

    @Test
    fun `a few expired tiles keep showing stale data without a request`() {
        val two = grid.take(2)
        val uncovered = two.associateWith { UncoveredReason.EXPIRED }

        assertNull(TowerFetchPlanner.plan(grid, uncovered))
    }

    @Test
    fun `mostly expired tiles are refreshed`() {
        val six = grid.take(6)
        val uncovered = six.associateWith { UncoveredReason.EXPIRED }

        assertEquals(unionOf(six), TowerFetchPlanner.plan(grid, uncovered))
    }

    @Test
    fun `expired tiles ride along when an absent tile forces a request anyway`() {
        val absent = grid.first { it.x == origin.x + 2 && it.y == origin.y + 2 }
        val expired = grid.first { it.x == origin.x && it.y == origin.y }
        val uncovered = mapOf(absent to UncoveredReason.ABSENT, expired to UncoveredReason.EXPIRED)

        // The union of the two opposite corners is the whole grid, which trips the 80% rule.
        assertEquals(unionOf(grid), TowerFetchPlanner.plan(grid, uncovered))
    }

    @Test
    fun `an incomplete tile is requested on its own`() {
        val one = grid.first { it.x == origin.x + 1 && it.y == origin.y + 1 }

        assertEquals(one.bounds(), TowerFetchPlanner.plan(grid, mapOf(one to UncoveredReason.INCOMPLETE)))
    }
}
