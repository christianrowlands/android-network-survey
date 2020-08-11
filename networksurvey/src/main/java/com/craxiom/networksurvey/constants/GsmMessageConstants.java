package com.craxiom.networksurvey.constants;

/**
 * The constants associated with the GSM table in the GeoPackage file.
 *
 * @since 0.0.5
 */
public final class GsmMessageConstants extends CellularMessageConstants
{
    private GsmMessageConstants()
    {
    }

    public static final String GSM_RECORD_MESSAGE_TYPE = "GsmRecord";
    public static final String GSM_RECORDS_TABLE_NAME = "GSM_MESSAGE";

    public static final String MCC_COLUMN = "MCC";
    public static final String MNC_COLUMN = "MNC";
    public static final String LAC_COLUMN = "LAC";
    public static final String CID_COLUMN = "CID";
    public static final String ARFCN_COLUMN = "ARFCN";
    public static final String BSIC_COLUMN = "BSIC";
    public static final String SIGNAL_STRENGTH_COLUMN = "Signal Strength";
    public static final String TA_COLUMN = "TA";
}
