package com.craxiom.networksurvey.model;

import com.craxiom.messaging.WifiBeaconRecord;

import java.io.Serializable;

/**
 * Wraps the {@link com.craxiom.messaging.WifiBeaconRecord} so that we can include the Android Specific
 * {@link android.net.wifi.ScanResult#capabilities} string.  This allow us to display the capabilities string in the UI.
 *
 * @since 0.1.2
 */
public class WifiRecordWrapper implements Serializable
{
    private final WifiBeaconRecord wifiBeaconRecord;
    private final String capabilitiesString;

    /**
     * @param wifiBeaconRecord   The protobuf defined Wi-Fi record object.
     * @param capabilitiesString The capabilities string from {@link android.net.wifi.ScanResult#capabilities}
     */
    public WifiRecordWrapper(WifiBeaconRecord wifiBeaconRecord, String capabilitiesString)
    {
        this.wifiBeaconRecord = wifiBeaconRecord;
        this.capabilitiesString = capabilitiesString;
    }

    public WifiBeaconRecord getWifiBeaconRecord()
    {
        return wifiBeaconRecord;
    }

    public String getCapabilitiesString()
    {
        return capabilitiesString;
    }
}
