package com.craxiom.networksurvey.constants;

/**
 * GeoPackage table constants generic to all cellular messages (i.e. GSM, CDMA, UMTS and LTE).
 *
 * @since 0.1.2
 */
public abstract class CellularMessageConstants extends MessageConstants
{
    public static final String GROUP_NUMBER_COLUMN = "GroupNumber";
    public static final String SERVING_CELL_COLUMN = "Serving Cell";

    public static final String PROVIDER_COLUMN = "Provider";
}
