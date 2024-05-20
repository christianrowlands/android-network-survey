package com.craxiom.networksurvey.util;

import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;

/**
 * Helper methods for working with cellular networks.
 */
public class CellularUtils
{
    /**
     * From 3GPP TS 36.101, Table E-UTRA Operating Bands
     */
    private static final int[][] DOWNLINK_LTE_BANDS = {
            // Band, Lower bound of EARFCN, Upper bound of EARFCN
            {1, 0, 599},
            {2, 600, 1199},
            {3, 1200, 1949},
            {4, 1950, 2399},
            {5, 2400, 2649},
            {6, 2650, 2749},
            {7, 2750, 3449},
            {8, 3450, 3799},
            {9, 3800, 4149},
            {10, 4150, 4749},
            {11, 4750, 4949},
            {12, 5010, 5179},
            {13, 5180, 5279},
            {14, 5280, 5379},
            {17, 5730, 5849},
            {18, 5850, 5999},
            {19, 6000, 6149},
            {20, 6150, 6449},
            {21, 6450, 6599},
            {22, 6600, 7399},
            {23, 7500, 7699},
            {24, 7700, 8039},
            {25, 8040, 8689},
            {26, 8690, 9039},
            {27, 9040, 9209},
            {28, 9210, 9659},
            {29, 9660, 9769},
            {30, 9770, 9869},
            {31, 9870, 9919},
            {32, 9920, 10359},
            {33, 36000, 36199},
            {34, 36200, 36349},
            {35, 36350, 36949},
            {36, 36950, 37549},
            {37, 37550, 37749},
            {38, 37750, 38249},
            {39, 38250, 38649},
            {40, 38650, 39649},
            {41, 39650, 41589},
            {42, 41590, 43589},
            {43, 43590, 45589},
            {44, 45590, 46589},
            {45, 46590, 46789},
            {46, 46790, 54539},
            {47, 54540, 55239},
            {48, 55240, 56739},
            {49, 56740, 58239},
            {50, 58240, 59089},
            {51, 59090, 59139},
            {52, 59140, 60139},
            {64, -1, -1}, // Reserved band
            {65, 65536, 66435},
            {66, 66436, 67335},
            {67, 67336, 67535},
            {68, 67536, 67835},
            {69, 67836, 68335},
            {70, 68336, 68585},
            {71, 68586, 68935},
            {72, 68936, 68985},
            {73, 68986, 69035},
            {74, 69036, 69465},
            {75, 69466, 70315},
            {76, 70316, 70365},
            {85, 70366, 70545},
            {87, 70546, 70595},
            {88, 70596, 70645},
            {103, 70646, 70655},
            {106, 70656, 70705},
    };

    /**
     * Returns the LTE band for a given EARFCN.
     *
     * @param earfcn The EARFCN to get the band for.
     * @return The LTE band for the given EARFCN, or -1 if the EARFCN is not in a known band.
     */
    public static int downlinkEarfcnToBand(int earfcn)
    {
        for (int[] band : DOWNLINK_LTE_BANDS)
        {
            if (earfcn >= band[1] && earfcn <= band[2])
            {
                return band[0];
            }
        }

        return -1;
    }

    /**
     * @return Returns true if the servingCell field is present and also set to true.
     */
    public static boolean isServingCell(GeneratedMessageV3 message)
    {
        try
        {
            Descriptors.Descriptor descriptor = message.getDescriptorForType();
            Descriptors.FieldDescriptor field = descriptor.findFieldByName("servingCell");
            return (boolean) message.getField(field);
        } catch (Exception e)
        {
            return false;
        }
    }
}