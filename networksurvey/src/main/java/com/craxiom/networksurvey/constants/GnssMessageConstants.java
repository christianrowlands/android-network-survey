package com.craxiom.networksurvey.constants;

import android.location.GnssStatus;

import com.craxiom.messaging.gnss.Constellation;
import com.craxiom.networksurvey.model.GnssType;

/**
 * The constants associated with the GNSS table in the GeoPackage file.
 * <p>
 * Also contains any utility methods to help with converting any of the values.
 *
 * @since 0.3.0
 */
public class GnssMessageConstants extends MessageConstants
{
    private GnssMessageConstants()
    {
    }

    public static final String GNSS_RECORD_MESSAGE_TYPE = "GnssRecord";
    public static final String GNSS_RECORDS_TABLE_NAME = "GNSS_MESSAGE";

    public static final String GROUP_NUMBER_COLUMN = "GroupNumber";
    public static final String DEVICE_MODEL_COLUMN = "DeviceModel";

    public static final String CONSTELLATION = "Constellation";
    public static final String SPACE_VEHICLE_ID = "Space Vehicle Id";
    public static final String CARRIER_FREQUENCY_HZ = "Carrier Frequency Hz";
    public static final String CLOCK_OFFSET = "Clock Offset";
    public static final String USED_IN_SOLUTION = "Used in Solution";
    public static final String UNDULATION_M = "Undulation (m)";
    public static final String LATITUDE_STD_DEV_M = "Latitude Standard Deviation (m)";
    public static final String LONGITUDE_STD_DEV_M = "Longitude Standard Deviation (m)";
    public static final String ALTITUDE_STD_DEV_M = "Altitude Standard Deviation (m)";
    public static final String AGC_DB = "AGC dB";
    public static final String CARRIER_TO_NOISE_DENSITY_DB_HZ = "C/N0 (dB-Hz)";
    public static final String HDOP = "HDOP";
    public static final String VDOP = "VDOP";

    /**
     * Given an Android OS defined {@link android.location.GnssStatus}, return the Protobuf representation of the GNSS
     * Constellation.
     *
     * @param gnssConstellationType Constellation type provided by the
     *                              {@link android.location.GnssStatus#getConstellationType(int)} method.
     * @return The protobuf defined GNSS Constellation, or {@link Constellation#UNKNOWN} if the type could not be found.
     */
    public static Constellation getProtobufConstellation(int gnssConstellationType)
    {
        switch (gnssConstellationType)
        {
            case GnssStatus.CONSTELLATION_GPS:
                return Constellation.GPS;

            case GnssStatus.CONSTELLATION_GLONASS:
                return Constellation.GLONASS;

            case GnssStatus.CONSTELLATION_BEIDOU:
                return Constellation.BEIDOU;

            case GnssStatus.CONSTELLATION_QZSS:
                return Constellation.QZSS;

            case GnssStatus.CONSTELLATION_GALILEO:
                return Constellation.GALILEO;

            case 7:
                // FIX ME - We can't use the GnssStatus.CONSTELLATION_IRNSS Android SDK constant in
                // this switch statement until this Android bug is fixed - https://issuetracker.google.com/issues/134611316
                // For now, we define CONSTELLATION_IRNSS_TEMP to be the same value of 7 so we can
                // still support IRNSS.
                return Constellation.IRNSS;

            case GnssStatus.CONSTELLATION_SBAS:
                return Constellation.SBAS;

            default:
                return Constellation.UNKNOWN;
        }
    }

    /**
     * Given a locally defined {@link GnssType}, return the Protobuf representation of the GNSS Constellation.
     *
     * @param gnssType The GnssType enum to convert.
     * @return The protobuf defined GNSS Constellation, or {@link Constellation#UNKNOWN} if the type could not be found.
     */
    public static Constellation getProtobufConstellation(GnssType gnssType)
    {
        switch (gnssType)
        {
            case NAVSTAR:
                return Constellation.GPS;

            case GLONASS:
                return Constellation.GLONASS;

            case GALILEO:
                return Constellation.GALILEO;

            case QZSS:
                return Constellation.QZSS;

            case BEIDOU:
                return Constellation.BEIDOU;

            case IRNSS:
                return Constellation.IRNSS;

            case SBAS:
                return Constellation.SBAS;

            default:
                return Constellation.UNKNOWN;
        }
    }

    /**
     * Given a Protocol Buffer defined GNSS Constellation, return a user friendly string representation that follows
     * the ICD used for the GeoPackage logging.
     *
     * @param constellation The Encryption Type enum to convert.
     * @return The user friendly Encryption Type, or an empty String if it is unknown/could not be converted.
     */
    public static String getConstellationString(Constellation constellation)
    {
        switch (constellation)
        {
            case GPS:
                return "GPS";

            case GLONASS:
                return "GLONASS";

            case GALILEO:
                return "GALILEO";

            case BEIDOU:
                return "BEIDOU";

            case SBAS:
                return "SBAS";

            case QZSS:
                return "QZSS";

            case IRNSS:
                return "IRNSS";

            default:
                return "";
        }
    }
}
