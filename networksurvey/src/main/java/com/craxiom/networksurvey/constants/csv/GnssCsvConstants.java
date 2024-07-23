package com.craxiom.networksurvey.constants.csv;

/**
 * The constants associated with the GNSS CSV file headers.
 * <p>
 * The constants in this class are intended to match the constants defined in the
 * <a href="https://messaging.networksurvey.app/">Network Survey Messaging API</a>.
 */
public class GnssCsvConstants extends SurveyCsvConstants
{
    private GnssCsvConstants()
    {
    }

    public static final String GROUP_NUMBER = "groupNumber";

    public static final String CONSTELLATION = "constellation";
    public static final String SPACE_VEHICLE_ID = "spaceVehicleId";
    public static final String CARRIER_FREQ_HZ = "carrierFreqHz";
    public static final String CLOCK_OFFSET = "clockOffset";
    public static final String USED_IN_SOLUTION = "usedInSolution";
    public static final String UNDULATION_M = "undulationM";
    public static final String LATITUDE_STD_DEV_M = "latitudeStdDevM";
    public static final String LONGITUDE_STD_DEV_M = "longitudeStdDevM";
    public static final String ALTITUDE_STD_DEV_M = "altitudeStdDevM";
    public static final String AGC_DB = "agcDb";
    public static final String CN0_DB_HZ = "cn0DbHz";
    public static final String HDOP = "hdop";
    public static final String VDOP = "vdop";
    public static final String DEVICE_MODEL = "deviceModel";
}
