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
}
