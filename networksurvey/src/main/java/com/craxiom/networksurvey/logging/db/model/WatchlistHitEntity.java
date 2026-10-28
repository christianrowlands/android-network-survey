package com.craxiom.networksurvey.logging.db.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity for a single Watchlist "Seen" event: a record that a watched network was detected at a
 * point in time and place. Rows are pruned by age (see the history retention preference).
 * <p>
 * The matched network's label is snapshotted onto the hit so the history screen is self-describing
 * without a join and so a row still shows the name the entry had when it was seen. The owning entry
 * is referenced by {@link #entryId} with a cascading delete, so removing a watched network also
 * removes its history.
 */
@Entity(tableName = "watchlist_hit",
        foreignKeys = @ForeignKey(entity = WatchlistEntryEntity.class,
                parentColumns = "id",
                childColumns = "entryId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"entryId", "timestamp"})})
public class WatchlistHitEntity
{
    /**
     * The value stored in {@link #matchedField} when the SSID was the field that matched.
     */
    public static final String MATCHED_FIELD_SSID = "ssid";

    /**
     * The value stored in {@link #matchedField} when the BSSID was the field that matched.
     */
    public static final String MATCHED_FIELD_BSSID = "bssid";

    /**
     * The value stored in {@link #rssi} when the access point reported no signal strength. A Wi-Fi
     * RSSI is always negative dBm, so 0 (and any other value that is not negative) never means a
     * measured signal, and history rows written before this constant existed already use 0 for a
     * missing signal. The {@link com.craxiom.networksurvey.logging.db.dao.WatchlistHitDao} signal
     * aggregates skip it, and the UI shows it as an unknown signal rather than 0 dBm.
     */
    public static final int RSSI_UNKNOWN = 0;

    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * The id of the {@link WatchlistEntryEntity} that produced this hit.
     */
    public long entryId;

    /**
     * Snapshot of the entry's label at the time of the hit.
     */
    public String label;

    /**
     * Which field caused the match: {@link #MATCHED_FIELD_SSID} or {@link #MATCHED_FIELD_BSSID}.
     */
    public String matchedField;

    /**
     * The SSID of the detected network (may be empty for a hidden network).
     */
    public String ssid;

    /**
     * The BSSID (MAC) of the detected network.
     */
    public String bssid;

    /**
     * The signal strength (RSSI, dBm) of the detection, or {@link #RSSI_UNKNOWN} when the access
     * point reported none. Read it through {@code WatchlistSignal} so an unknown signal is never shown
     * as 0 dBm.
     */
    public int rssi;

    /**
     * The latitude where the network was seen, or null when no location was available.
     */
    public Double latitude;

    /**
     * The longitude where the network was seen, or null when no location was available.
     */
    public Double longitude;

    /**
     * The time of the detection (milliseconds since epoch).
     */
    public long timestamp;
}
