package com.craxiom.networksurvey.logging.db.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity for a single Watchlist entry: a Wi-Fi network the user has chosen to watch for.
 * <p>
 * An entry is matched by SSID and/or BSSID. At least one of the two is required (this is enforced
 * by the app layer, not the database, because SQLite treats NULLs as distinct which makes a unique
 * index unreliable for optional columns). When both an SSID and a BSSID are set the match requires
 * both; when only one is set the match is on that field alone.
 */
@Entity(tableName = "watchlist_entry")
public class WatchlistEntryEntity
{
    /**
     * The match type stored when an entry uses an exact (case-insensitive, trimmed) SSID match.
     * Stored as a plain String so future match types (for example a "contains" match) can be added
     * without a database migration.
     */
    public static final String MATCH_TYPE_EXACT = "EXACT";

    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * The user-facing name for this entry (defaults to the SSID when added). Always non-null.
     */
    public String label;

    /**
     * The SSID to watch for, or null if this entry watches by BSSID only.
     */
    public String ssid;

    /**
     * The BSSID (MAC) to watch for, normalized to lower-case colon-delimited form, or null if this
     * entry watches by SSID only.
     */
    public String bssid;

    /**
     * The match strategy for the SSID. See {@link #MATCH_TYPE_EXACT}. Defaults to exact.
     */
    public String matchType;

    /**
     * True when this entry is actively watched. A disabled entry is muted without being deleted.
     */
    public boolean enabled;

    /**
     * The time this entry was created (milliseconds since epoch).
     */
    public long createdAt;

    /**
     * The minimum number of seconds between alerts for this entry. Acts as a hard backstop on top
     * of the presence-transition dedupe in the detection manager.
     */
    public int cooldownSeconds;
}
