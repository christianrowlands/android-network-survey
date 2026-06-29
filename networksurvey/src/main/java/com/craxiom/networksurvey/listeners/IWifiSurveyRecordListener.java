package com.craxiom.networksurvey.listeners;

import com.craxiom.networksurvey.model.WifiRecordWrapper;

import java.util.List;

/**
 * Listener interface for those interested in being notified when a new collection of Wi-Fi Survey Records are ready.
 *
 * @since 0.1.2
 */
public interface IWifiSurveyRecordListener
{
    /**
     * Called when a new collection of 802.11 Beacon Survey Records are ready.
     *
     * @param wifiBeaconRecords the list of 802.11 Beacon Records.
     */
    void onWifiBeaconSurveyRecords(List<WifiRecordWrapper> wifiBeaconRecords);

    /**
     * Indicates whether this listener wants to receive records for SSIDs in the user's exclusion
     * list. Logging and streaming consumers leave this false so excluded SSIDs are not persisted or
     * sent off device. UI display consumers and the Watchlist detector return true: the UI needs to
     * show excluded networks, and the Watchlist must be able to alert on a network even when the
     * user has excluded it from logging (choosing to watch a network is a clear signal they care
     * about it).
     *
     * @return true to receive all records including excluded SSIDs, false to receive only
     * non-excluded records. Defaults to false.
     */
    default boolean wantsExcludedRecords()
    {
        return false;
    }
}
