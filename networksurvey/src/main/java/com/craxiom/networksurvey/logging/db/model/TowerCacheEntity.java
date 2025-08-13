package com.craxiom.networksurvey.logging.db.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity for caching towers that have been seen before.
 * This prevents duplicate new tower alerts by tracking which towers we've already encountered,
 * and reduces backend API calls.
 *
 * @since 1.0.0
 */
@Entity(tableName = "tower_cache",
        indices = {@Index(value = {"mcc", "mnc", "area", "cid"}, unique = true)})
public class TowerCacheEntity
{

    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * Mobile Country Code
     */
    public int mcc;

    /**
     * Mobile Network Code
     */
    public int mnc;

    /**
     * Location Area Code (GSM/UMTS) or Tracking Area Code (LTE/NR)
     */
    public int area;

    /**
     * Cell ID
     */
    public long cid;

    /**
     * Timestamp when this cache entry was created (milliseconds since epoch)
     */
    public long timestamp;

    /**
     * Radio technology type (e.g., "LTE", "NR", "GSM", "UMTS")
     */
    public String radio;
}