package com.craxiom.networksurvey.constants;

/**
 * The constants associated with the CDMA table in the GeoPackage file.
 *
 * @since 0.0.5
 */
public final class CdmaMessageConstants extends CellularMessageConstants
{
    private CdmaMessageConstants()
    {
    }

    public static final String CDMA_RECORD_MESSAGE_TYPE = "CdmaRecord";
    public static final String CDMA_RECORDS_TABLE_NAME = "CDMA_MESSAGE";

    public static final String SID_COLUMN = "SID";
    public static final String NID_COLUMN = "NID";
    public static final String ZONE_COLUMN = "Zone";
    public static final String BSID_COLUMN = "BSID";
    public static final String BASE_LATITUDE = "Base Latitude";
    public static final String BASE_LONGITUDE = "Base Longitude";
    public static final String CHANNEL_COLUMN = "Channel";
    public static final String PN_OFFSET_COLUMN = "PN Offset";
    public static final String SIGNAL_STRENGTH_COLUMN = "Signal Strength";
    public static final String ECIO_COLUMN = "Ec/Io";
}
