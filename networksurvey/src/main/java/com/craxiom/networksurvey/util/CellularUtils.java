package com.craxiom.networksurvey.util;

import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.CdmaRecordData;
import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.GsmRecordData;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.LteRecordData;
import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.NrRecordData;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.UmtsRecordData;
import com.craxiom.networksurvey.data.api.Tower;
import com.craxiom.networksurvey.model.CellularProtocol;
import com.craxiom.networksurvey.model.CellularRecordWrapper;
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo;
import com.craxiom.networksurvey.ui.cellular.model.ServingSignalInfo;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessage;

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
     * Gets the band name for a given LTE band number.
     * The band names are based on common frequency designations and regional usage.
     *
     * @param bandNumber The LTE band number (e.g., 1, 2, 3, etc.).
     * @return The band name, or null if the band number is not recognized.
     */
    public static String getLteBandName(int bandNumber)
    {
        return switch (bandNumber)
        {
            case 1 -> "2100 MHz";
            case 2 -> "1900 PCS";
            case 3 -> "1800+";
            case 4 -> "AWS-1";
            case 5 -> "850";
            case 6 -> "850 Japan";
            case 7 -> "2600";
            case 8 -> "900 GSM";
            case 9 -> "1800";
            case 10 -> "AWS-3";
            case 11 -> "1500 Lower";
            case 12 -> "700 a";
            case 13 -> "700 c";
            case 14 -> "700 PS";
            case 17 -> "700 b";
            case 18 -> "800 Lower";
            case 19 -> "800 Upper";
            case 20 -> "800 DD";
            case 21 -> "1500 Upper";
            case 22 -> "3500";
            case 23 -> "2000 S-band";
            case 24 -> "1600 L-band";
            case 25 -> "1900+";
            case 26 -> "850+";
            case 27 -> "800 SMR";
            case 28 -> "700 APT";
            case 30 -> "2300 WCS";
            case 31 -> "450";
            case 32 -> "1500 L-band";
            case 33 -> "1900 TDD";
            case 34 -> "2000 TDD";
            case 35 -> "1900 TDD";
            case 36 -> "1900 TDD";
            case 37 -> "1900 TDD";
            case 38 -> "2600 TDD";
            case 39 -> "1900 TDD";
            case 40 -> "2300 TDD";
            case 41 -> "2500 TDD";
            case 42 -> "3400 TDD";
            case 43 -> "3600 TDD";
            case 44 -> "700 TDD";
            case 45 -> "1400 TDD";
            case 46 -> "5200 TDD";
            case 47 -> "5900 TDD";
            case 48 -> "3550 CBRS";
            case 49 -> "3550 TDD";
            case 50 -> "1500 TDD";
            case 51 -> "1500 TDD";
            case 52 -> "3300 TDD";
            case 53 -> "2300 TDD";
            case 65 -> "2100+";
            case 66 -> "AWS";
            case 67 -> "700 EU";
            case 68 -> "700 ME";
            case 70 -> "AWS-4";
            case 71 -> "600";
            case 72 -> "450 PMR/PAMR";
            case 73 -> "450 APAC";
            case 74 -> "L-band";
            case 85 -> "700 a+";
            case 87 -> "410";
            case 88 -> "410+";
            case 103 -> "NB-IoT";
            case 106 -> "900";
            case 111 -> "HD-1800";
            default -> null;
        };
    }

    /**
     * Gets the band name for a given 5G NR band number.
     * The band names are based on the frequency designations from RF wireless specifications.
     *
     * @param bandNumber The NR band number (e.g., 1, 2, 3, etc.).
     * @return The band name, or null if the band number is not recognized.
     */
    public static String getNrBandName(int bandNumber)
    {
        return switch (bandNumber)
        {
            case 1 -> "2100";
            case 2 -> "1900 PCS";
            case 3 -> "1800";
            case 5 -> "850";
            case 7 -> "2600";
            case 8 -> "900 GSM";
            case 12 -> "700 a";
            case 13 -> "700 c";
            case 14 -> "700 PS";
            case 18 -> "800 Lower";
            case 20 -> "800";
            case 24 -> "1600 L";
            case 25 -> "1900+";
            case 26 -> "850+";
            case 28 -> "700 APT";
            case 29 -> "700 d";
            case 30 -> "2300 WCS";
            case 31 -> "450";
            case 34 -> "TD 2000";
            case 38 -> "TD 2600";
            case 39 -> "TD 1900+";
            case 40 -> "TD 2300";
            case 41 -> "TD 2600+";
            case 46 -> "TD Unlicensed";
            case 47 -> "TD V2X";
            case 48 -> "TD 3600";
            case 50 -> "TD 1500+";
            case 51 -> "TD 1500-";
            case 53 -> "TD 2500";
            case 54 -> "TD 1700";
            case 65 -> "2100+";
            case 66 -> "AWS";
            case 67 -> "700 EU";
            case 68 -> "700 ME";
            case 70 -> "AWS-4";
            case 71 -> "600";
            case 72 -> "450 PMR/PAMR";
            case 74 -> "L-band";
            case 75 -> "DL 1500+";
            case 76 -> "DL 1500-";
            case 77 -> "TD 3700";
            case 78 -> "TD 3500";
            case 79 -> "TD 4700";
            case 80 -> "SUL 1800";
            case 81 -> "SUL 900";
            case 82 -> "SUL 800";
            case 83 -> "SUL 700";
            case 84 -> "SUL 2100";
            case 85 -> "SUL 700 a";
            case 86 -> "SUL 1700";
            case 89 -> "SUL 850";
            case 90 -> "TD 2500";
            case 91 -> "L-band 1500";
            case 92 -> "L-band 1500";
            case 93 -> "L-band 1500";
            case 94 -> "L-band 1500";
            case 95 -> "DL 2100";
            case 96 -> "TD L-band";
            case 97 -> "S-band 2300";
            case 98 -> "S-band 1900";
            case 99 -> "L-band 1600";
            case 100 -> "TD 900";
            case 101 -> "1900";
            case 102 -> "TD 5900+";
            case 104 -> "TD 6400+";
            case 105 -> "TD 600";
            case 106 -> "TD 900";
            case 109 -> "1900";
            case 110 -> "TD 700";
            case 256 -> "NTN 2100";
            case 257 -> "28 GHz";
            case 258 -> "26 GHz";
            case 259 -> "41 GHz";
            case 260 -> "39 GHz";
            case 261 -> "28 GHz";
            case 262 -> "47 GHz";
            default -> null;
        };
    }

    /**
     * Converts 5G NR ARFCN to frequency in MHz according to 3GPP TS 38.104 specification.
     * The formula is: F_REF = F_REF-Offs + (N_REF - N_REF-Offs) * Δf
     * <p>
     * Resource: <a href="https://5g-tools.com/5g-nr-arfcn-calculator/">5G NARFCN Calculator</a>
     *
     * @param narfcn The NR-ARFCN (Absolute Radio Frequency Channel Number) to convert.
     * @return The frequency in MHz, or -1.0 if the NARFCN is not in a valid range.
     */
    public static double narfcnToFrequencyMhz(int narfcn)
    {
        if (narfcn < 0)
        {
            return -1.0;
        }

        // 3GPP TS 38.104 Table 5.4.2.1-1: Global frequency raster parameters for NR
        if (narfcn <= 599999)
        {
            // Range 1: 0 ≤ ARFCN ≤ 599,999
            // Δf = 5 kHz, F_REF-Offs = 0, N_REF-Offs = 0
            return narfcn * 0.005; // 5 kHz = 0.005 MHz
        } else if (narfcn <= 2016666)
        {
            // Range 2: 600,000 ≤ ARFCN ≤ 2,016,666
            // Δf = 15 kHz, F_REF-Offs = 3000 MHz, N_REF-Offs = 600,000
            return 3000.0 + (narfcn - 600000) * 0.015; // 15 kHz = 0.015 MHz
        } else if (narfcn <= 3279165)
        {
            // Range 3: 2,016,667 ≤ ARFCN ≤ 3,279,165
            // Δf = 60 kHz, F_REF-Offs = 24,250.08 MHz, N_REF-Offs = 2,016,667
            return 24250.08 + (narfcn - 2016667) * 0.060; // 60 kHz = 0.060 MHz
        } else
        {
            // NARFCN is outside the valid range
            return -1.0;
        }
    }

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
    public static boolean isServingCell(GeneratedMessage message)
    {
        try
        {
            // Get the descriptor for the top-level message
            Descriptors.Descriptor descriptor = message.getDescriptorForType();

            // Get the descriptor for the 'data' field
            Descriptors.FieldDescriptor dataField = descriptor.findFieldByName("data");
            if (dataField == null)
            {
                return false;
            }

            // Get the value of the 'data' field
            GeneratedMessage dataMessage = (GeneratedMessage) message.getField(dataField);

            // Get the descriptor for the 'servingCell' field within the 'data' field
            Descriptors.Descriptor dataDescriptor = dataMessage.getDescriptorForType();
            Descriptors.FieldDescriptor servingCellField = dataDescriptor.findFieldByName("servingCell");
            if (servingCellField == null)
            {
                return false;
            }

            // Get the value of the 'servingCell' field
            return ((BoolValue) dataMessage.getField(servingCellField)).getValue();
        } catch (Exception e)
        {
            return false;
        }
    }

    /**
     * Get the ID used to identify a tower on the map. This is NOT the CGI because I wanted to
     * include the TAC for LTE and NR, but the CGI doesn't include the TAC.
     */
    public static String getTowerId(Tower tower)
    {
        if (tower == null)
        {
            return "";
        }

        return "" + tower.getMcc() + tower.getMnc() + tower.getArea() + tower.getCid();
    }

    /**
     * Get the ID used to identify a tower on the map. This is NOT the CGI because I wanted to
     * include the TAC for LTE and NR, but the CGI doesn't include the TAC.
     *
     * @param servingCellInfo The ServingCellInfo to get the ID from.
     * @return The ID, or an empty string if the ServingCellInfo is null or the ServingCell is null.
     */
    public static String getTowerId(ServingCellInfo servingCellInfo)
    {
        if (servingCellInfo == null)
        {
            return "";
        }

        CellularRecordWrapper cellularRecord = servingCellInfo.getServingCell();
        if (cellularRecord == null)
        {
            return "";
        }

        switch (cellularRecord.cellularProtocol)
        {
            case NONE:
                return "";

            case GSM:
                final GsmRecordData gsmData = ((GsmRecord) cellularRecord.cellularRecord).getData();
                return "" + gsmData.getMcc().getValue() + gsmData.getMnc().getValue() + gsmData.getLac().getValue() + gsmData.getCi().getValue();

            case CDMA:
                // We don't support CDMA since it is pretty much gone
                break;

            case UMTS:
                final UmtsRecordData umtsData = ((UmtsRecord) cellularRecord.cellularRecord).getData();
                return "" + umtsData.getMcc().getValue() + umtsData.getMnc().getValue() + umtsData.getLac().getValue() + umtsData.getCid().getValue();

            case LTE:
                final LteRecordData lteData = ((LteRecord) cellularRecord.cellularRecord).getData();
                return "" + lteData.getMcc().getValue() + lteData.getMnc().getValue() + lteData.getTac().getValue() + lteData.getEci().getValue();

            case NR:
                final NrRecordData nrData = ((NrRecord) cellularRecord.cellularRecord).getData();
                return "" + nrData.getMcc().getValue() + nrData.getMnc().getValue() + nrData.getTac().getValue() + nrData.getNci().getValue();
        }

        return "";
    }

    public static ServingSignalInfo getSignalInfo(CellularRecordWrapper cellularRecord)
    {
        if (cellularRecord == null || cellularRecord.cellularProtocol == null)
        {
            return null;
        }

        return switch (cellularRecord.cellularProtocol)
        {
            case NONE -> null;
            case GSM ->
            {
                final GsmRecordData gsmData = ((GsmRecord) cellularRecord.cellularRecord).getData();
                yield new ServingSignalInfo(CellularProtocol.GSM, (int) gsmData.getSignalStrength().getValue(), -1);
            }
            case CDMA ->
            {
                final CdmaRecordData cdmaData = ((CdmaRecord) cellularRecord.cellularRecord).getData();
                yield new ServingSignalInfo(CellularProtocol.CDMA, ((int) cdmaData.getEcio().getValue()), -1);
            }
            case UMTS ->
            {
                final UmtsRecordData umtsData = ((UmtsRecord) cellularRecord.cellularRecord).getData();
                yield new ServingSignalInfo(CellularProtocol.UMTS, ((int) umtsData.getSignalStrength().getValue()), (int) umtsData.getRscp().getValue());
            }
            case LTE ->
            {
                final LteRecordData lteData = ((LteRecord) cellularRecord.cellularRecord).getData();
                yield new ServingSignalInfo(CellularProtocol.LTE, (int) lteData.getRsrp().getValue(), (int) lteData.getRsrq().getValue());
            }
            case NR ->
            {
                final NrRecordData nrData = ((NrRecord) cellularRecord.cellularRecord).getData();
                yield new ServingSignalInfo(CellularProtocol.NR, (int) nrData.getSsRsrp().getValue(), (int) nrData.getSsRsrq().getValue());
            }
        };
    }
}