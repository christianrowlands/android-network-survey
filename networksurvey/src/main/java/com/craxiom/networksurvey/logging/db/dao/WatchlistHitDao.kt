package com.craxiom.networksurvey.logging.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.craxiom.networksurvey.logging.db.model.WatchlistGroupSummary
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity
import com.craxiom.networksurvey.logging.db.model.WatchlistLastSeen
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the watchlist "Seen" history (the log of past detections).
 */
@Dao
interface WatchlistHitDao {
    @Insert
    suspend fun insert(hit: WatchlistHitEntity): Long

    /**
     * Observe the full history, newest first.
     */
    @Query("SELECT * FROM watchlist_hit ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<WatchlistHitEntity>>

    /**
     * Observe only the located hits (those with a recorded position), newest first, for the history
     * map. Filtering in SQL keeps the loaded rows and work down versus loading the whole table.
     */
    @Query("SELECT * FROM watchlist_hit WHERE latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY timestamp DESC")
    fun observeLocated(): Flow<List<WatchlistHitEntity>>

    /**
     * Observe the history for a single entry, newest first.
     */
    @Query("SELECT * FROM watchlist_hit WHERE entryId = :entryId ORDER BY timestamp DESC")
    fun observeForEntry(entryId: Long): Flow<List<WatchlistHitEntity>>

    /**
     * Observe the most recent "Seen" time (and that sighting's RSSI) for every entry in a single
     * grouped query, so the management screen avoids one query per row. The bare {@code rssi} column
     * is the value from the {@code MAX(timestamp)} row (a documented SQLite behavior for a single
     * min/max aggregate).
     */
    @Query("SELECT entryId AS entryId, MAX(timestamp) AS lastSeen, rssi AS lastRssi FROM watchlist_hit GROUP BY entryId")
    fun observeLastSeen(): Flow<List<WatchlistLastSeen>>

    /**
     * Observe a per-entry aggregate (sighting count, strongest signal, most recent time) in a single
     * grouped query. Every column is an aggregate, so count and time always come from the same
     * emission (no transient mismatch). A row exists only for entries that have at least one hit, which
     * the history screen relies on to omit never-seen networks.
     */
    @Query("SELECT entryId AS entryId, COUNT(*) AS sightings, MAX(rssi) AS bestRssi, MAX(timestamp) AS lastSeen FROM watchlist_hit GROUP BY entryId")
    fun observeGroupSummaries(): Flow<List<WatchlistGroupSummary>>

    @Query("DELETE FROM watchlist_hit")
    suspend fun clearAll()

    /**
     * Delete every hit for a single entry. Backs the "clear this network's history" action on the
     * sighting-details screen (leaves the watched entry in place).
     */
    @Query("DELETE FROM watchlist_hit WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: Long)

    /**
     * Delete history rows older than the given cutoff (milliseconds since epoch).
     *
     * @return the number of deleted rows.
     */
    @Query("DELETE FROM watchlist_hit WHERE timestamp < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long): Int
}
