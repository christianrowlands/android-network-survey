package com.craxiom.networksurvey.constants.csv;

/**
 * The constants associated with the Device Status CSV file headers.
 * <p>
 * The constants in this class are intended to match the constants defined in the
 * <a href="https://messaging.networksurvey.app/">Network Survey Messaging API</a>.
 */
public class DeviceStatusCsvConstants extends CsvConstants
{
    private DeviceStatusCsvConstants()
    {
    }

    public static final String DEVICE_TIME = "deviceTime";
    public static final String BATTERY_LEVEL_PERCENT = "batteryLevelPercent";

    public static final String GNSS_LATITUDE = "gnssLatitude";
    public static final String GNSS_LONGITUDE = "gnssLongitude";
    public static final String GNSS_ALTITUDE = "gnssAltitude";
    public static final String GNSS_ACCURACY = "gnssAccuracy";

    public static final String NETWORK_LATITUDE = "networkLatitude";
    public static final String NETWORK_LONGITUDE = "networkLongitude";
    public static final String NETWORK_ALTITUDE = "networkAltitude";
    public static final String NETWORK_ACCURACY = "networkAccuracy";
}
