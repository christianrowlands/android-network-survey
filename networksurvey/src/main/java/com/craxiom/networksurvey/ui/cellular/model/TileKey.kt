package com.craxiom.networksurvey.ui.cellular.model

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * The fixed slippy map zoom level used to partition the world for the tower tile cache. At
 * zoom 13 a tile is roughly 3 to 5 km across at mid latitudes, small enough that a street
 * level view only fetches a handful of tiles, and large enough that a zoom 9 viewport is
 * around a thousand tiles, which is trivial to enumerate.
 */
const val TOWER_TILE_ZOOM = 13

/** Web Mercator cannot represent latitudes beyond this, so tile math clamps to it. */
private const val MAX_MERCATOR_LATITUDE = 85.05112878

private const val METERS_PER_DEGREE = 111_320.0

/**
 * A plain latitude/longitude box with no Android or MapLibre dependency so the tile cache and
 * fetch planner can be unit tested on the JVM. Edges are inclusive south/west and exclusive
 * north/east for tile membership purposes.
 */
data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double) {

    /** True when the box has positive area and does not cross the antimeridian. */
    val isQueryable: Boolean
        get() = south < north && west < east

    fun union(other: GeoBounds): GeoBounds = GeoBounds(
        south = minOf(south, other.south),
        west = minOf(west, other.west),
        north = maxOf(north, other.north),
        east = maxOf(east, other.east)
    )

    /** True when [other] lies entirely inside this box. */
    fun contains(other: GeoBounds): Boolean =
        other.south >= south && other.west >= west && other.north <= north && other.east <= east

    /**
     * Width times height in square meters using an equirectangular approximation. Accurate
     * enough for comparing request sizes against each other, which is all the cache needs.
     */
    fun approxAreaSqMeters(): Double {
        val midLatRad = Math.toRadians((south + north) / 2.0)
        val heightMeters = (north - south) * METERS_PER_DEGREE
        val widthMeters = (east - west) * METERS_PER_DEGREE * cos(midLatRad)
        return (heightMeters * widthMeters).coerceAtLeast(0.0)
    }
}

/**
 * Identifies one slippy map tile at [TOWER_TILE_ZOOM]. Towers are bucketed by the tile that
 * contains their own coordinates, so each tower belongs to exactly one tile.
 */
data class TileKey(val x: Int, val y: Int) {

    /** The geographic box covered by this tile. */
    fun bounds(): GeoBounds {
        val n = 1 shl TOWER_TILE_ZOOM
        return GeoBounds(
            south = tileYToLat(y + 1, n),
            west = x.toDouble() / n * 360.0 - 180.0,
            north = tileYToLat(y, n),
            east = (x + 1).toDouble() / n * 360.0 - 180.0
        )
    }

    companion object {
        /** The tile containing the given point, with latitude clamped to the Mercator range. */
        fun fromLatLng(lat: Double, lon: Double): TileKey {
            val n = 1 shl TOWER_TILE_ZOOM
            val clampedLat = lat.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
            val latRad = Math.toRadians(clampedLat)
            val x = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
            val y = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n)
                .toInt().coerceIn(0, n - 1)
            return TileKey(x, y)
        }

        /**
         * Every tile that intersects [bounds]. Returns an empty set for a box that is not
         * queryable (zero area or crossing the antimeridian), mirroring [buildBboxParam].
         */
        fun tilesCovering(bounds: GeoBounds): Set<TileKey> {
            if (!bounds.isQueryable) return emptySet()
            val sw = fromLatLng(bounds.south, bounds.west)
            val ne = fromLatLng(bounds.north, bounds.east)
            val tiles = LinkedHashSet<TileKey>()
            for (x in sw.x..ne.x) {
                for (y in ne.y..sw.y) {
                    tiles.add(TileKey(x, y))
                }
            }
            return tiles
        }

        private fun tileYToLat(y: Int, n: Int): Double {
            val latRad = atan(sinh(PI * (1.0 - 2.0 * y.toDouble() / n)))
            return Math.toDegrees(latRad)
        }
    }
}
