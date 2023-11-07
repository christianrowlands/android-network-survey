package com.craxiom.networksurvey.constants.csv;

/**
 * The constants associated with the GSM CSV file headers.
 * <p>
 * The constants in this class are intended to match the constants defined in the
 * <a href="https://messaging.networksurvey.app/">Network Survey Messaging API</a>.
 */
public class GsmCsvConstants extends CellularCsvConstants
{
    private GsmCsvConstants()
    {
    }

    public static final String MCC = "mcc";
    public static final String MNC = "mnc";
    public static final String LAC = "lac";
    public static final String CI = "ci";
    public static final String ARFCN = "arfcn";
    public static final String BSIC = "bsic";
    public static final String SIGNAL_STRENGTH = "signalStrength";
    public static final String TA = "ta";
}
