package com.craxiom.networksurvey.constants.csv;

/**
 * The column names of the Wi-Fi clients and probe request CSV files that only an NS Scout fills.
 * The shared columns come from {@link WifiCsvConstants} and {@link CsvConstants}.
 * <p>
 * Every column matches a field of the <a href="https://messaging.networksurvey.app/">Network Survey
 * Messaging API</a>.
 */
public final class WifiClientCsvConstants
{
    private WifiClientCsvConstants()
    {
    }

    public static final String FRAME_TYPES = "frameTypes";

    /** The format version of both files, written as a header comment. */
    public static final String CSV_VERSION_COMMENT = "CSV Version=0.1.0";
}
