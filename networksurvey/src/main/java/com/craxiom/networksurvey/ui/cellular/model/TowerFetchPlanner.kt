package com.craxiom.networksurvey.ui.cellular.model

/**
 * When the tiles that need fetching cover more than this share of the viewport, request the
 * whole viewport instead of a ragged union that is nearly the same size anyway.
 */
private const val WHOLE_VIEWPORT_FRACTION = 0.8

/**
 * Expired tiles still have towers to show. Only refresh them when they make up at least this
 * share of the viewport, so a long session refreshes eventually without paying a request for
 * a sliver of stale data on every idle.
 */
private const val REFRESH_EXPIRED_FRACTION = 0.5

/**
 * Turns the tile cache's view of the viewport into at most one request box. Every box it
 * returns is tile aligned, so the tiles inside a successful response can be marked complete
 * and the next idle over the same area costs nothing.
 */
object TowerFetchPlanner {

    /**
     * @param viewportTiles every tile intersecting the visible region
     * @param uncovered the tiles the cache cannot serve, with the reason for each
     * @return the box to request, or null when no request is needed
     */
    fun plan(viewportTiles: Set<TileKey>, uncovered: Map<TileKey, UncoveredReason>): GeoBounds? {
        if (uncovered.isEmpty() || viewportTiles.isEmpty()) return null

        val viewportBounds = unionOf(viewportTiles)
        val viewportArea = viewportBounds.approxAreaSqMeters()

        val onlyExpired = uncovered.values.all { it == UncoveredReason.EXPIRED }
        if (onlyExpired) {
            val expiredArea = uncovered.keys.sumOf { it.bounds().approxAreaSqMeters() }
            if (expiredArea < viewportArea * REFRESH_EXPIRED_FRACTION) return null
        }

        val union = unionOf(uncovered.keys)
        return if (union.approxAreaSqMeters() > viewportArea * WHOLE_VIEWPORT_FRACTION) {
            viewportBounds
        } else {
            union
        }
    }

    private fun unionOf(tiles: Collection<TileKey>): GeoBounds =
        tiles.map { it.bounds() }.reduce { acc, bounds -> acc.union(bounds) }
}
