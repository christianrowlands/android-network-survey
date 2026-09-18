package com.craxiom.networksurvey.util.band;

/**
 * Downlink EARFCN ranges for the E-UTRA operating bands, and the lookup over them.
 * <p>
 * Split out of {@code CellularUtils} so the table and its long provenance notes do not crowd the
 * general cellular helpers. {@code CellularUtils} keeps the public entry points and delegates.
 */
public final class LteBandTable
{
    /**
     * Downlink EARFCN range per E-UTRA operating band, from 3GPP TS 36.101 Table 5.7.3-1
     * ("Range of NDL"). Verified row by row against release k00.
     * <p>
     * Band 64 is reserved in Table 5.5-1 and has no channel numbers, so it is absent by design.
     * Band 108 was previously listed here at 70706-70755; it appears in neither Table 5.5-1 nor
     * Table 5.7.3-1, and no band occupies that range, so it was removed. Band 111 was missing and
     * has been added.
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
            {53, 60140, 60254},
            {54, 60255, 60304},
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
            {111, 73386, 73485},
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
     * Downlink centre frequency in MHz for an EARFCN, or -1.0 when it falls in no band.
     * <p>
     * From 3GPP TS 36.101 Table 5.7.3-1: {@code F_DL = F_DL_low + 0.1 * (N_DL - N_Offs-DL)}. The
     * table below carries {@code N_Offs-DL} as each band's first EARFCN, and {@code F_DL_low} per
     * band, so both constants come from the band itself.
     */
    public static double downlinkEarfcnToFrequencyMhz(int earfcn)
    {
        for (int[] band : DOWNLINK_LTE_BANDS)
        {
            if (earfcn >= band[1] && earfcn <= band[2])
            {
                final double lowMhz = downlinkLowMhz(band[0]);
                if (lowMhz < 0) return -1.0;
                return lowMhz + 0.1 * (earfcn - band[1]);
            }
        }

        return -1.0;
    }

    /**
     * F_DL_low per E-UTRA band, from 3GPP TS 36.101 Table 5.7.3-1. Only the bands the downlink
     * table above resolves are listed, so anything else returns -1.
     */
    private static double downlinkLowMhz(int band)
    {
        return switch (band)
        {
            case 1 -> 2110.0; case 2 -> 1930.0; case 3 -> 1805.0; case 4 -> 2110.0;
            case 5 -> 869.0; case 6 -> 875.0; case 7 -> 2620.0; case 8 -> 925.0;
            case 9 -> 1844.9; case 10 -> 2110.0; case 11 -> 1475.9; case 12 -> 729.0;
            case 13 -> 746.0; case 14 -> 758.0; case 17 -> 734.0; case 18 -> 860.0;
            case 19 -> 875.0; case 20 -> 791.0; case 21 -> 1495.9; case 22 -> 3510.0;
            case 23 -> 2180.0; case 24 -> 1525.0; case 25 -> 1930.0; case 26 -> 859.0;
            case 27 -> 852.0; case 28 -> 758.0; case 29 -> 717.0; case 30 -> 2350.0;
            case 31 -> 462.5; case 32 -> 1452.0; case 33 -> 1900.0; case 34 -> 2010.0;
            case 35 -> 1850.0; case 36 -> 1930.0; case 37 -> 1910.0; case 38 -> 2570.0;
            case 39 -> 1880.0; case 40 -> 2300.0; case 41 -> 2496.0; case 42 -> 3400.0;
            case 43 -> 3600.0; case 44 -> 703.0; case 45 -> 1447.0; case 46 -> 5150.0;
            case 47 -> 5855.0; case 48 -> 3550.0; case 49 -> 3550.0; case 50 -> 1432.0;
            case 51 -> 1427.0; case 52 -> 3300.0; case 53 -> 2483.5; case 54 -> 1670.0;
            case 65 -> 2110.0; case 66 -> 2110.0; case 67 -> 738.0; case 68 -> 753.0;
            case 69 -> 2570.0; case 70 -> 1995.0; case 71 -> 617.0; case 72 -> 461.0;
            case 73 -> 460.0; case 74 -> 1475.0; case 75 -> 1432.0; case 76 -> 1427.0;
            case 85 -> 728.0; case 87 -> 420.0; case 88 -> 422.0; case 103 -> 757.0;
            case 106 -> 935.0; case 111 -> 1820.0;
            default -> -1.0;
        };
    }

    private LteBandTable()
    {
    }
}
