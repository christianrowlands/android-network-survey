package com.craxiom.networksurvey.logging.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing watchlist entries (the networks the user has chosen to watch for).
 */
@Dao
interface WatchlistDao {
    @Insert
    suspend fun insert(entry: WatchlistEntryEntity): Long

    @Update
    suspend fun update(entry: WatchlistEntryEntity)

    @Delete
    suspend fun delete(entry: WatchlistEntryEntity)

    /**
     * Observe all entries, newest first, for the management screen.
     */
    @Query("SELECT * FROM watchlist_entry ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WatchlistEntryEntity>>

    /**
     * Observe only the enabled entries so the detection manager can keep its in-memory snapshot
     * current as the user adds, removes, enables, or disables entries.
     */
    @Query("SELECT * FROM watchlist_entry WHERE enabled = 1")
    fun observeEnabled(): Flow<List<WatchlistEntryEntity>>
}
