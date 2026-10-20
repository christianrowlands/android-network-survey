package com.craxiom.networksurvey.constants;

import android.location.GnssStatus;

import com.craxiom.messaging.gnss.Constellation;

/**
 * The constants associated with the GNSS record in the Network Survey Messaging API.
 * <p>
 * Also contains any utility methods to help with converting any of the values.
 *
 * @since 0.3.0
 */
public class GnssMessageConstants
{
    private GnssMessageConstants()
    {
    }

    public static final String GNSS_RECORD_MESSAGE_TYPE = "GnssRecord";

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
}
