package com.craxiom.networksurvey.ui.cellular.model

/** Default maximum number of towers held across all tiles before whole tiles are evicted. */
const val TOWER_BUDGET = 20_000

/** How long a fetched tile is trusted before the map refreshes it on the next viewport hit. */
const val TILE_TTL_MS = 45L * 60L * 1000L

/**
 * A truncated tile is only worth requesting again when the new request is meaningfully smaller
 * than the one that hit the server cap, otherwise the same 5,000 rows come back.
 */
private const val RETRY_TRUNCATED_AREA_FRACTION = 0.5

/** Why a tile needs (or may need) a fetch. */
enum class UncoveredReason {
    /** Never fetched, or evicted. */
    ABSENT,

    /** Fetched successfully, but older than the TTL. Its towers are still shown. */
    EXPIRED,

    /** Fetched, but the response was truncated, and a smaller request could complete it. */
    INCOMPLETE,
}

/**
 * In-session cache of towers bucketed by [TileKey]. Each tower lives in exactly one tile, the
 * one containing its own coordinates, so merging repeated fetches is a map put and eviction
 * happens in whole tiles. Nothing here touches Android or MapLibre, so it is unit tested on
 * the JVM. All calls are expected on a single thread (the main thread in the ViewModel).
 */
class TowerTileCache(
    private val towerBudget: Int = TOWER_BUDGET,
    private val ttlMs: Long = TILE_TTL_MS,
) {
    private class TileEntry {
        val towers = LinkedHashMap<String, TowerWrapper>()
        var fetchedAt = 0L
        var complete = false
        var truncatedArea: Double? = null
        var lastUsed = 0L
    }

    private val tiles = HashMap<TileKey, TileEntry>()
    private var towerCount = 0

    /** Increments whenever the set of towers or their contents change. */
    var version: Int = 0
        private set

    private var snapshotVersion = -1
    private var snapshotList: List<TowerWrapper> = emptyList()
    private var snapshotById: Map<String, TowerWrapper> = emptyMap()

    /**
     * Records the result of one area request. Every tile fully inside [requestBounds] is marked
     * complete unless the response was [truncated], in which case the tiles keep their towers
     * but remember the request area so a same size request is not repeated.
     */
    fun ingest(requestBounds: GeoBounds, towers: List<TowerWrapper>, truncated: Boolean, now: Long) {
        var changed = false
        for (wrapper in towers) {
            val key = TileKey.fromLatLng(wrapper.tower.lat, wrapper.tower.lon)
            val entry = tiles.getOrPut(key) { TileEntry() }
            val existing = entry.towers[wrapper.towerId]
            if (existing == null) {
                entry.towers[wrapper.towerId] = wrapper
                towerCount++
                changed = true
            } else if (wrapper.tower.updatedAt >= existing.tower.updatedAt && wrapper.tower != existing.tower) {
                entry.towers[wrapper.towerId] = wrapper
                changed = true
            }
            entry.lastUsed = now
        }

        val requestArea = requestBounds.approxAreaSqMeters()
        for (key in TileKey.tilesCovering(requestBounds)) {
            if (!requestBounds.contains(key.bounds())) continue
            val entry = tiles.getOrPut(key) { TileEntry() }
            entry.fetchedAt = now
            entry.lastUsed = now
            if (truncated) {
                entry.complete = false
                entry.truncatedArea = requestArea
            } else {
                entry.complete = true
                entry.truncatedArea = null
            }
        }

        if (evictToBudget()) changed = true
        if (changed) version++
    }

    /**
     * Classifies each viewport tile that cannot be served from the cache as is. Touching a tile
     * here also marks it recently used so visible tiles survive eviction.
     *
     * @param viewportArea the approximate area of the request the caller would issue, compared
     * against the area that previously came back truncated.
     */
    fun uncovered(viewportTiles: Set<TileKey>, viewportArea: Double, now: Long): Map<TileKey, UncoveredReason> {
        val result = LinkedHashMap<TileKey, UncoveredReason>()
        for (key in viewportTiles) {
            val entry = tiles[key]
            if (entry == null) {
                result[key] = UncoveredReason.ABSENT
                continue
            }
            entry.lastUsed = now
            when {
                entry.fetchedAt == 0L -> result[key] = UncoveredReason.ABSENT
                now - entry.fetchedAt > ttlMs -> result[key] = UncoveredReason.EXPIRED
                !entry.complete -> {
                    val truncatedArea = entry.truncatedArea
                    if (truncatedArea == null || viewportArea < truncatedArea * RETRY_TRUNCATED_AREA_FRACTION) {
                        result[key] = UncoveredReason.INCOMPLETE
                    }
                }
            }
        }
        return result
    }

    /** Number of cached towers inside the given tiles, for the viewport empty state. */
    fun towerCountIn(viewportTiles: Set<TileKey>): Int =
        viewportTiles.sumOf { key -> tiles[key]?.towers?.size ?: 0 }

    /** True when any of the given tiles holds a truncated result, for the map banner. */
    fun anyIncomplete(viewportTiles: Set<TileKey>): Boolean =
        viewportTiles.any { key -> tiles[key]?.let { it.fetchedAt != 0L && !it.complete } == true }

    /** Drops everything. Called when a filter changes and the cached towers no longer apply. */
    fun clear() {
        if (tiles.isEmpty()) return
        tiles.clear()
        towerCount = 0
        version++
    }

    /** All cached towers. Recomputed only when the content has changed since the last call. */
    fun snapshot(): List<TowerWrapper> {
        refreshSnapshot()
        return snapshotList
    }

    /** All cached towers keyed by [TowerWrapper.towerId]. */
    fun byId(): Map<String, TowerWrapper> {
        refreshSnapshot()
        return snapshotById
    }

    private fun refreshSnapshot() {
        if (snapshotVersion == version) return
        val list = ArrayList<TowerWrapper>(towerCount)
        val byId = HashMap<String, TowerWrapper>(towerCount * 2)
        for (entry in tiles.values) {
            for (wrapper in entry.towers.values) {
                list.add(wrapper)
                byId[wrapper.towerId] = wrapper
            }
        }
        snapshotList = list
        snapshotById = byId
        snapshotVersion = version
    }

    /** Evicts least recently used tiles, whole tiles at a time, until under budget. */
    private fun evictToBudget(): Boolean {
        if (towerCount <= towerBudget) return false
        val byAge = tiles.entries.sortedBy { it.value.lastUsed }
        var evicted = false
        for ((key, entry) in byAge) {
            if (towerCount <= towerBudget) break
            towerCount -= entry.towers.size
            tiles.remove(key)
            evicted = true
        }
        return evicted
    }
}
