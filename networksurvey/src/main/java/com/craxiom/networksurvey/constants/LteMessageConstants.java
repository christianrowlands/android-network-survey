package com.craxiom.networksurvey.constants;

import com.craxiom.messaging.LteBandwidth;

/**
 * The constants associated with the LTE record in the Network Survey Messaging API.
 * <p>
 * Also contains any utility methods to help with converting any of the values.
 *
 * @since 0.0.5
 */
public class LteMessageConstants
{
    private LteMessageConstants()
    {
    }

    public static final String LTE_RECORD_MESSAGE_TYPE = "LteRecord";

    /**
     * Given a Protocol Buffer defined LTE Bandwidth, return a user friendly string representation.
     *
     * @param lteBandwidth The LTE Bandwidth enum to convert.
     * @return The user friendly LTE Bandwidth, or an empty String if it is unknown/could not be converted.
     */
    public static String getLteBandwidth(LteBandwidth lteBandwidth)
    {
        switch (lteBandwidth)
        {
            case MHZ_1_4:
                return "1.4";

            case MHZ_3:
                return "3";

            case MHZ_5:
                return "5";

            case MHZ_10:
                return "10";

            case MHZ_15:
                return "15";

            case MHZ_20:
                return "20";

            default:
                return "";
        }
    }
}
