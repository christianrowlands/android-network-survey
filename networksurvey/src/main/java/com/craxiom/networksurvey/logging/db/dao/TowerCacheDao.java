package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.craxiom.networksurvey.logging.db.model.TowerCacheEntity;

import java.util.List;

/**
 * DAO for tower cache operations.
 *
 * @since 1.0.0
 */
@Dao
public interface TowerCacheDao
{

    /**
     * Insert or update a tower cache entry.
     *
     * @param tower The tower cache entry to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TowerCacheEntity tower);

    /**
     * Check if a tower exists in the cache.
     *
     * @param mcc  Mobile Country Code
     * @param mnc  Mobile Network Code
     * @param area Location/Tracking Area Code
     * @param cid  Cell ID
     * @return The cached tower entry if found, null otherwise
     */
    @Query("SELECT * FROM tower_cache WHERE mcc = :mcc AND mnc = :mnc AND area = :area AND cid = :cid LIMIT 1")
    TowerCacheEntity getTower(int mcc, int mnc, int area, long cid);

    /**
     * Get all cached towers for a specific area.
     *
     * @param mcc  Mobile Country Code
     * @param mnc  Mobile Network Code
     * @param area Location/Tracking Area Code
     * @return List of cached towers in the area
     */
    @Query("SELECT * FROM tower_cache WHERE mcc = :mcc AND mnc = :mnc AND area = :area")
    List<TowerCacheEntity> getTowersInArea(int mcc, int mnc, int area);

    /**
     * Delete old cache entries.
     *
     * @param cutoffTimestamp Delete entries older than this timestamp
     * @return Number of deleted entries
     */
    @Query("DELETE FROM tower_cache WHERE timestamp < :cutoffTimestamp")
    int deleteOldEntries(long cutoffTimestamp);

    /**
     * Get total number of cached entries.
     *
     * @return Count of cached tower entries
     */
    @Query("SELECT COUNT(*) FROM tower_cache")
    int getCacheSize();

    /**
     * Clear all cache entries.
     */
    @Query("DELETE FROM tower_cache")
    void clearCache();
}