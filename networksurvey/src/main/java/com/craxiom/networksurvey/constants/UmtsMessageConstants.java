package com.craxiom.networksurvey.constants;

/**
 * The constants associated with the UMTS table in the GeoPackage file.
 *
 * @since 0.0.5
 */
public final class UmtsMessageConstants extends CellularMessageConstants
{
    private UmtsMessageConstants()
    {
    }

    public static final String UMTS_RECORD_MESSAGE_TYPE = "UmtsRecord";
    public static final String UMTS_RECORDS_TABLE_NAME = "UMTS_MESSAGE";

    public static final String MCC_COLUMN = "MCC";
    public static final String MNC_COLUMN = "MNC";
    public static final String LAC_COLUMN = "LAC";
    public static final String CELL_ID_COLUMN = "Cell ID";
    public static final String UARFCN_COLUMN = "UARFCN";
    public static final String PSC_COLUMN = "PSC";
    public static final String SIGNAL_STRENGTH_COLUMN = "Signal Strength";
    public static final String RSCP_COLUMN = "RSCP";
}
