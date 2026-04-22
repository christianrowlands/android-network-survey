package com.craxiom.networksurvey.data.oui

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room DAO for the OUI cache. All operations are suspend so callers stay on Dispatchers.IO.
 */
@Dao
interface OuiCacheDao {

    @Query(
        "SELECT * FROM oui_cache_entry WHERE prefix_long = :prefixLong AND prefix_len = :prefixLen LIMIT 1"
    )
    suspend fun findByPrefix(prefixLong: Long, prefixLen: Int): OuiCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: OuiCacheEntity)

    @Query("UPDATE oui_cache_entry SET last_seen_ts = :ts WHERE prefix_long = :prefixLong AND prefix_len = :prefixLen")
    suspend fun touch(prefixLong: Long, prefixLen: Int, ts: Long)

    @Query("DELETE FROM oui_cache_entry")
    suspend fun evictAll()

    @Query("DELETE FROM oui_cache_entry WHERE dataset_version != :currentVersion")
    suspend fun evictStale(currentVersion: String): Int

    @Query("SELECT COUNT(*) FROM oui_cache_entry")
    suspend fun count(): Int

    @Query(
        "DELETE FROM oui_cache_entry WHERE ROWID IN (" +
                "SELECT ROWID FROM oui_cache_entry ORDER BY last_seen_ts ASC LIMIT :limit)"
    )
    suspend fun evictOldest(limit: Int)
}
