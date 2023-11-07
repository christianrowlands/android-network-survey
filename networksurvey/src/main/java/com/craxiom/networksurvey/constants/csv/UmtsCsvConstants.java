package com.craxiom.networksurvey.constants.csv;

/**
 * The constants associated with the UMTS CSV file headers.
 * <p>
 * The constants in this class are intended to match the constants defined in the
 * <a href="https://messaging.networksurvey.app/">Network Survey Messaging API</a>.
 */
public class UmtsCsvConstants extends CellularCsvConstants
{
    private UmtsCsvConstants()
    {
    }

    public static final String MCC = "mcc";
    public static final String MNC = "mnc";
    public static final String LAC = "lac";
    public static final String CID = "cid";
    public static final String UARFCN = "uarfcn";
    public static final String PSC = "psc";
    public static final String RSCP = "rscp";
    public static final String ECNO = "ecno";
    public static final String SIGNAL_STRENGTH = "signalStrength";
}
