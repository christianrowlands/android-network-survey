package com.craxiom.networksurvey.constants.csv;

/**
 * Various CSV file constants.
 *
 * @since 1.11
 */
public abstract class CsvConstants
{
    // Column Titles. These should be in alignment with the MQTT fields defined at https://messaging.networksurvey.app/
    // Note that some of the schemas are not used in both CSV files and MQTT records, but if the message is used in
    // both then we want to keep the fields the same.
    public static final String DEVICE_SERIAL_NUMBER = "deviceSerialNumber";
    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";
    public static final String ALTITUDE = "altitude";
    public static final String SPEED = "speed";
    public static final String ACCURACY = "accuracy";
    public static final String LOCATION_AGE = "locationAge";
}
