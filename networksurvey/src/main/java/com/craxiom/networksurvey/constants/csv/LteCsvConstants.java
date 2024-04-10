package com.craxiom.networksurvey.constants.csv;

/**
 * The constants associated with the LTE CSV file headers.
 * <p>
 * The constants in this class are intended to match the constants defined in the
 * <a href="https://messaging.networksurvey.app/">Network Survey Messaging API</a>.
 */
public class LteCsvConstants extends CellularCsvConstants
{
    private LteCsvConstants()
    {
    }

    public static final String MCC = "mcc";
    public static final String MNC = "mnc";
    public static final String TAC = "tac";
    public static final String ECI = "eci";
    public static final String EARFCN = "earfcn";
    public static final String PCI = "pci";
    public static final String RSRP = "rsrp";
    public static final String RSRQ = "rsrq";
    public static final String TA = "ta";
    public static final String LTE_BANDWIDTH = "lteBandwidth";
    public static final String SIGNAL_STRENGTH = "signalStrength";
    public static final String CQI = "cqi";
    public static final String SNR = "snr";
}
