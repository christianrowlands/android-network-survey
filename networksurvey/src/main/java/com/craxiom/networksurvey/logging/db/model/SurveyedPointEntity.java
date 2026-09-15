package com.craxiom.networksurvey.logging.db.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One place a running survey has covered. Rows are thinned by movement (see
 * {@code SurveyedPointGate}) so the table holds a sparse breadcrumb of where the device has
 * surveyed, not every record. Rows survive the upload pipelines' own cleanup so the map can keep
 * showing survey points after the raw records are gone.
 * <p>
 * {@link #source} names the pipeline that wrote the row, {@link #observedMask} the kind of record
 * that was seen at that step (one bit per row), and {@link #uploadedMask} the destinations that
 * have since accepted the data from that step. The remaining columns describe what was heard at
 * that step so the map can color by it and the tap sheet can explain it. Every one of them has a
 * default so rows written before they existed read as "unknown".
 */
@Entity(tableName = "surveyed_point",
        indices = {@Index({"latitude", "longitude"}), @Index({"source", "time"})})
public class SurveyedPointEntity
{
    /**
     * The community upload pipeline (OpenCelliD and BeaconDB).
     */
    public static final int SOURCE_COMMUNITY = 1;
    /**
     * The NS Analytics upload pipeline.
     */
    public static final int SOURCE_NS_ANALYTICS = 2;
    /**
     * Matches every source; used as the filter when both pipelines should be shown.
     */
    public static final int SOURCE_ANY = SOURCE_COMMUNITY | SOURCE_NS_ANALYTICS;

    public static final int OBSERVED_CELLULAR = 1;
    public static final int OBSERVED_WIFI = 2;
    public static final int OBSERVED_BLUETOOTH = 4;
    /**
     * Matches every observed kind; used as the filter when a destination accepts all of them.
     */
    public static final int OBSERVED_ANY = OBSERVED_CELLULAR | OBSERVED_WIFI | OBSERVED_BLUETOOTH;

    public static final int UPLOADED_OCID = 1;
    public static final int UPLOADED_BEACONDB = 2;
    public static final int UPLOADED_NS_ANALYTICS = 4;

    /**
     * Values of {@link #protocol}. Ordered by generation so the newer one wins a tie.
     */
    public static final int PROTOCOL_NONE = 0;
    public static final int PROTOCOL_GSM = 1;
    public static final int PROTOCOL_CDMA = 2;
    public static final int PROTOCOL_UMTS = 3;
    public static final int PROTOCOL_LTE = 4;
    public static final int PROTOCOL_NR = 5;

    @PrimaryKey(autoGenerate = true)
    public long id;

    public double latitude;
    public double longitude;

    /**
     * Epoch milliseconds from the system clock at the time the batch was observed.
     */
    public long time;

    public int source;
    public int observedMask;
    public int uploadedMask;

    /**
     * The serving cell's technology, one of the {@code PROTOCOL_} constants; 0 when unknown.
     */
    @ColumnInfo(defaultValue = "0")
    public int protocol;

    /**
     * "mcc-mnc" with leading zeros preserved, or null when unknown.
     */
    public String plmn;

    /**
     * Carrier name as reported on the record, or null.
     */
    public String provider;

    /**
     * Raw TAC or LAC of the serving cell; 0 when unknown.
     */
    @ColumnInfo(defaultValue = "0")
    public int area;

    /**
     * Raw CI, CID, ECI, or NCI of the serving cell; 0 when unknown.
     */
    @ColumnInfo(defaultValue = "0")
    public long cid;

    /**
     * The serving cell's map identity ("mcc-mnc-area-cid"), or null when unknown.
     */
    public String cellId;

    /**
     * Primary signal in dBm (RSSI, RSCP, RSRP, SS-RSRP, or the strongest Wi-Fi/BT RSSI); 0 when unknown.
     */
    @ColumnInfo(defaultValue = "0")
    public int signal;

    /**
     * Secondary signal (RSRQ, SS-RSRQ, or RSSI for UMTS); 0 when unknown. Shown in the tap sheet only.
     */
    @ColumnInfo(defaultValue = "0")
    public int signal2;

    /**
     * {@code SignalBuckets} value: 0 unknown, 1 very weak up to 5 strong, so MAX() is "best".
     */
    @ColumnInfo(defaultValue = "0")
    public int signalBucket;

    /**
     * 1 when an NR cell reported SECONDARY_SERVING in the batch (5G NSA data leg).
     */
    @ColumnInfo(defaultValue = "0")
    public int nrScg;

    /**
     * Number of Wi-Fi access points or Bluetooth devices heard in the batch.
     */
    @ColumnInfo(defaultValue = "0")
    public int deviceCount;

    /**
     * Strongest SSID (or BSSID) or Bluetooth device name (or address), or null.
     */
    public String label;

    /**
     * Mission id of the survey, or null when the record carried none.
     */
    public String missionId;
}
