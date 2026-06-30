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

    /**
     * Insert several entries in one transaction. Used by the deep-link import to add a batch of
     * networks after they have been deduped.
     */
    @Insert
    suspend fun insertAll(entries: List<WatchlistEntryEntity>)

    @Update
    suspend fun update(entry: WatchlistEntryEntity)

    @Delete
    suspend fun delete(entry: WatchlistEntryEntity)

    /**
     * One-shot read of every entry. The import flow uses this (rather than the [observeAll] Flow
     * snapshot, which may not have emitted yet on a cold-start deep link) to dedupe authoritatively.
     */
    @Query("SELECT * FROM watchlist_entry")
    suspend fun getAll(): List<WatchlistEntryEntity>

    /**
     * Delete every entry. Backs the "Clear all" action; the watchlist_hit table is cleared too via its
     * cascading foreign key.
     */
    @Query("DELETE FROM watchlist_entry")
    suspend fun deleteAll()

    /**
     * Observe all entries, newest first, for the management screen.
     */
    @Query("SELECT * FROM watchlist_entry ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WatchlistEntryEntity>>

    /**
     * Observe a single entry by id (null once it is deleted). Backs the sighting-details screen.
     */
    @Query("SELECT * FROM watchlist_entry WHERE id = :id")
    fun observeById(id: Long): Flow<WatchlistEntryEntity?>

    /**
     * Observe only the enabled entries so the detection manager can keep its in-memory snapshot
     * current as the user adds, removes, enables, or disables entries.
     */
    @Query("SELECT * FROM watchlist_entry WHERE enabled = 1")
    fun observeEnabled(): Flow<List<WatchlistEntryEntity>>
}
