package com.craxiom.networksurvey.logging.db.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity for caching tower lookup results from the backend API.
 * This helps reduce API calls by storing whether a tower has been checked.
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
     * Whether this tower exists in the backend database (true = known, false = new)
     */
    public boolean isKnown;

    /**
     * Timestamp when this cache entry was created (milliseconds since epoch)
     */
    public long timestamp;

    /**
     * Radio technology type (e.g., "LTE", "NR", "GSM", "UMTS")
     */
    public String radio;
}