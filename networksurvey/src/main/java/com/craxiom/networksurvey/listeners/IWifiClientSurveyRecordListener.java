package com.craxiom.networksurvey.listeners;

import com.craxiom.messaging.WifiClientRecord;
import com.craxiom.messaging.WifiProbeRequestRecord;

import java.util.List;

/**
 * Listener interface for those interested in the Wi-Fi clients record type: the clients
 * and probe requests an NS Scout heard, at most one record per client (and per client and network
 * name for probes) per window. Only the survey's sinks get these; live screens get a
 * {@link IWifiClientLiveListener} snapshot instead.
 */
public interface IWifiClientSurveyRecordListener
{
    /**
     * Called with one window of Wi-Fi client records.
     *
     * @param clientRecords The records, never empty.
     */
    void onWifiClientRecords(List<WifiClientRecord> clientRecords);

    /**
     * Called with one window of probe request records.
     *
     * @param probeRecords The records, never empty.
     */
    void onWifiProbeRequestRecords(List<WifiProbeRequestRecord> probeRecords);
}
