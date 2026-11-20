package com.craxiom.networksurvey.logging.db.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One tower the phone connected to (its serving cell) that the tower service did not have when it
 * was checked, during a survey that sends to the Community databases. Backs the "New towers" page.
 * <p>
 * There is one row per tower, identified by MCC, MNC, area, cell ID and radio (the same identity
 * as {@code TowerKey}). A later sighting only moves {@link #lastSeenAt}; everything else describes
 * the first find, so the page groups each tower under the session that found it. Rows are kept
 * until the user clears the list, capped at {@code NewTowerFindStore.MAX_ROWS}.
 * <p>
 * The location columns are where the phone was, never where the tower is. The tower's own
 * position is only known once the tower service has it ({@link #towerLatitude}).
 */
@Entity(tableName = "new_tower_find",
        indices = {@Index(value = {"mcc", "mnc", "area", "cellId", "radio"}, unique = true)})
public class NewTowerFindEntity
{
    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * Mobile Country Code, as a string so leading zeros survive.
     */
    @NonNull
    public String mcc = "";

    /**
     * Mobile Network Code, as a string so leading zeros survive ("01").
     */
    @NonNull
    public String mnc = "";

    /**
     * TAC for LTE and NR, LAC for GSM and UMTS.
     */
    public int area;

    /**
     * ECI, NCI, CI or CID.
     */
    public long cellId;

    /**
     * The radio technology name the tower service uses: "LTE", "NR", "GSM" or "UMTS".
     */
    @NonNull
    public String radio = "";

    /**
     * Epoch milliseconds of the first find.
     */
    public long firstSeenAt;

    /**
     * Epoch milliseconds of the latest sighting as the serving cell.
     */
    public long lastSeenAt;

    /**
     * Epoch milliseconds the Community upload session that first found the tower started. Never
     * updated by a later sighting.
     */
    public long sessionStartedAt;

    /**
     * Where the phone was at the first find, or null when the record had no fix.
     */
    public Double latitude;

    /**
     * Where the phone was at the first find, or null when the record had no fix.
     */
    public Double longitude;

    /**
     * Horizontal accuracy of the phone's fix in meters, or null when unknown.
     */
    public Float accuracy;

    /**
     * The carrier name on the record, or null when the phone reported none.
     */
    public String operatorName;

    /**
     * The band: the bare LTE band number ("66") or the NR band designators ("n78", or "n77/n78"
     * when the NARFCN is ambiguous). Null when unknown or for GSM and UMTS.
     */
    public String band;

    /**
     * The primary signal in dBm at the first find (RSRP, SS-RSRP, RSSI or RSCP), or null.
     */
    public Integer signalDbm;

    /**
     * EARFCN, NARFCN, UARFCN or ARFCN, or null when unknown.
     */
    public Integer channel;

    /**
     * PCI (LTE and NR), PSC (UMTS) or BSIC (GSM), or null when unknown.
     */
    public Integer pci;

    /**
     * True once a check found the tower in the tower database.
     */
    public boolean inDatabase;

    /**
     * Epoch milliseconds of the last check against the tower service.
     */
    public long lastCheckedAt;

    /**
     * The tower's position from the tower service, once {@link #inDatabase}; otherwise null.
     */
    public Double towerLatitude;

    /**
     * The tower's position from the tower service, once {@link #inDatabase}; otherwise null.
     */
    public Double towerLongitude;
}
