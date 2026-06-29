package com.craxiom.networksurvey.logging.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
     * Observe the history for a single entry, newest first.
     */
    @Query("SELECT * FROM watchlist_hit WHERE entryId = :entryId ORDER BY timestamp DESC")
    fun observeForEntry(entryId: Long): Flow<List<WatchlistHitEntity>>

    /**
     * Observe the most recent "Seen" time for every entry in a single grouped query, so the
     * management screen avoids one query per row.
     */
    @Query("SELECT entryId AS entryId, MAX(timestamp) AS lastSeen FROM watchlist_hit GROUP BY entryId")
    fun observeLastSeen(): Flow<List<WatchlistLastSeen>>

    @Query("DELETE FROM watchlist_hit")
    suspend fun clearAll()

    /**
     * Delete history rows older than the given cutoff (milliseconds since epoch).
     *
     * @return the number of deleted rows.
     */
    @Query("DELETE FROM watchlist_hit WHERE timestamp < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long): Int
}
