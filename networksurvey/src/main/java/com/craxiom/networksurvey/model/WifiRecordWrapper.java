package com.craxiom.networksurvey.model;

import com.craxiom.messaging.WifiBeaconRecord;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wraps the {@link com.craxiom.messaging.WifiBeaconRecord} so that we can include the Android Specific
 * {@link android.net.wifi.ScanResult#capabilities} string.  This allow us to display the capabilities string in the UI.
 * <p>
 * A record heard by an NS Scout also carries {@link ScoutWifiDetails} for the screens, and says
 * whether it arrived late: buffered on the Scout while it was out of range and delivered after it
 * reconnected. Late records keep the time and place they were heard, but they are not uploaded to
 * the community databases and never raise a watchlist alert, since both treat a record as "now".
 *
 * @since 0.1.2
 */
public class WifiRecordWrapper implements Serializable
{
    private final WifiBeaconRecord wifiBeaconRecord;
    private final String capabilitiesString;
    private final boolean isExcluded;
    private final ScoutWifiDetails scoutDetails;
    private final boolean late;

    /**
     * @param wifiBeaconRecord   The protobuf defined Wi-Fi record object.
     * @param capabilitiesString The capabilities string from {@link android.net.wifi.ScanResult#capabilities}
     */
    public WifiRecordWrapper(WifiBeaconRecord wifiBeaconRecord, String capabilitiesString)
    {
        this(wifiBeaconRecord, capabilitiesString, false);
    }

    /**
     * @param wifiBeaconRecord   The protobuf defined Wi-Fi record object.
     * @param capabilitiesString The capabilities string from {@link android.net.wifi.ScanResult#capabilities}
     * @param isExcluded         True if this SSID is in the exclusion list
     */
    public WifiRecordWrapper(WifiBeaconRecord wifiBeaconRecord, String capabilitiesString, boolean isExcluded)
    {
        this(wifiBeaconRecord, capabilitiesString, isExcluded, null, false);
    }

    /**
     * @param wifiBeaconRecord   The protobuf defined Wi-Fi record object.
     * @param capabilitiesString The capabilities string, empty for a record from an NS Scout.
     * @param isExcluded         True if this SSID is in the exclusion list
     * @param scoutDetails       What the Scout heard beyond the record, null for the phone's own scans.
     * @param late               True for a record the Scout buffered while out of range.
     */
    public WifiRecordWrapper(WifiBeaconRecord wifiBeaconRecord, String capabilitiesString, boolean isExcluded,
                             ScoutWifiDetails scoutDetails, boolean late)
    {
        this.wifiBeaconRecord = wifiBeaconRecord;
        this.capabilitiesString = capabilitiesString;
        this.isExcluded = isExcluded;
        this.scoutDetails = scoutDetails;
        this.late = late;
    }

    public WifiBeaconRecord getWifiBeaconRecord()
    {
        return wifiBeaconRecord;
    }

    public String getCapabilitiesString()
    {
        return capabilitiesString;
    }

    public boolean isExcluded()
    {
        return isExcluded;
    }

    /**
     * @return What the NS Scout heard beyond the record, or null when the phone scanned it.
     */
    public ScoutWifiDetails getScoutDetails()
    {
        return scoutDetails;
    }

    /**
     * @return True when an NS Scout buffered this record while out of range and sent it later.
     */
    public boolean isLate()
    {
        return late;
    }

    /**
     * @return The records that are not {@link #isLate() late}, for the consumers that treat a
     * record as heard "now": BeaconDB uploads and watchlist alerts.
     */
    public static List<WifiRecordWrapper> withoutLate(List<WifiRecordWrapper> records)
    {
        if (records.stream().noneMatch(WifiRecordWrapper::isLate)) return records;
        return records.stream().filter(record -> !record.isLate()).collect(Collectors.toList());
    }
}
