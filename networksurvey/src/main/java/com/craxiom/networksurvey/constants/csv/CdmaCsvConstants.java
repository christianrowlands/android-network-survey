package com.craxiom.networksurvey.constants.csv;

/**
 * The constants associated with the CDMA CSV file headers.
 * <p>
 * The constants in this class are intended to match the constants defined in the
 * <a href="https://messaging.networksurvey.app/">Network Survey Messaging API</a>.
 */
public class CdmaCsvConstants extends CellularCsvConstants
{
    private CdmaCsvConstants()
    {
    }

    public static final String SID = "sid";
    public static final String NID = "nid";
    public static final String ZONE = "zone";
    public static final String BSID = "bsid";
    public static final String CHANNEL = "channel";
    public static final String PN_OFFSET = "pnOffset";
    public static final String SIGNAL_STRENGTH = "signalStrength";
    public static final String ECIO = "ecio";
}
