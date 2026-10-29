package com.craxiom.networksurvey.model;

/**
 * Defines the possible Cellular protocols that we handle in this app. The thresholds that color each
 * protocol's signal values live in {@link com.craxiom.networksurvey.util.SignalBuckets}.
 *
 * @since 1.6.0
 */
public enum CellularProtocol
{
    NONE,

    /**
     * Signal 1 is RSSI
     */
    GSM,
    CDMA,

    /**
     * Signal 1 is RSSI
     * Signal 2 is RSCP
     */
    UMTS,

    /**
     * Signal 1 is RSRP
     * Signal 2 is RSRQ
     * Signal 3 is SNR
     */
    LTE,

    /**
     * Signal 1 is SS_RSRP
     * Signal 2 is SS_RSRQ
     * Signal 3 is SS_SINR
     */
    NR
}
