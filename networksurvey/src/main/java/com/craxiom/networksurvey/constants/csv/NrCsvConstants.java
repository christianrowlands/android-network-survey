package com.craxiom.networksurvey.constants.csv;

/**
 * The constants associated with the 5G NR CSV file headers.
 * <p>
 * The constants in this class are intended to match the constants defined in the
 * <a href="https://messaging.networksurvey.app/">Network Survey Messaging API</a>.
 */
public class NrCsvConstants extends CellularCsvConstants
{
    private NrCsvConstants()
    {
    }

    public static final String MCC = "mcc";
    public static final String MNC = "mnc";
    public static final String TAC = "tac";
    public static final String NCI = "nci";
    public static final String NARFCN = "narfcn";
    public static final String PCI = "pci";
    public static final String SS_RSRP = "ssRsrp";
    public static final String SS_RSRQ = "ssRsrq";
    public static final String SS_SINR = "ssSinr";
    public static final String CSI_RSRP = "csiRsrp";
    public static final String CSI_RSRQ = "csiRsrq";
    public static final String CSI_SINR = "csiSinr";
    public static final String TA = "ta";
}
