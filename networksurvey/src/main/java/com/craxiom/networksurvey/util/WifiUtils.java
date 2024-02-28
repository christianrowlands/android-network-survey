package com.craxiom.networksurvey.util;

import com.craxiom.messaging.wifi.EncryptionType;
import com.craxiom.messaging.wifi.Standard;
import com.craxiom.messaging.wifi.WifiBandwidth;

/**
 * A few pieces of information come packaged in the {@link android.net.wifi.ScanResult#capabilities} string.  This class
 * offers utility methods to extract the relevant information from that capabilities string.
 * <p>
 * This class also has some other Wi-Fi related utility methods.
 *
 * @since 0.1.2
 */
public class WifiUtils
{

    public static final int START_OF_6_GHZ_RANGE = 5935;

    /**
     * Given the {@link android.net.wifi.ScanResult#capabilities} string, return the appropriate {@link EncryptionType}.
     * <p>
     * Example capability strings can be found in the unit tests.
     *
     * @param capabilities The capabilities string that contains the encryption type.
     * @return The Encryption Type enum.
     */
    public static EncryptionType getEncryptionType(String capabilities)
    {
        if (capabilities.contains("WEP"))
        {
            return EncryptionType.WEP;
        }
        if (capabilities.contains("WPA3") || capabilities.contains("SAE"))
        {
            if (capabilities.contains("WPA2"))
            {
                return EncryptionType.WPA2_WPA3;
            }
            return EncryptionType.WPA3;
        } else
        {
            final boolean containsWpa = capabilities.contains("WPA]") || capabilities.contains("WPA-");
            final boolean containsWpa2 = capabilities.contains("WPA2");
            if (containsWpa && containsWpa2)
            {
                return EncryptionType.WPA_WPA2;
            } else if (containsWpa2)
            {
                return EncryptionType.WPA2;
            } else if (containsWpa)
            {
                return EncryptionType.WPA;
            } else if (!capabilities.contains("RSN"))
            {
                // If RSN is not present then the network is open
                return EncryptionType.OPEN;
            } else
            {
                return EncryptionType.UNKNOWN;
            }
        }
    }

    /**
     * @param capabilities The capabilities string from {@link android.net.wifi.ScanResult#capabilities}.
     * @return True if the capabilities string contains "WPS", false otherwise.
     */
    public static boolean supportsWps(String capabilities)
    {
        return capabilities.contains("WPS");
    }

    /**
     * @return A string representation of the provided Wi-Fi bandwidth.
     */
    public static String formatBandwidth(WifiBandwidth bandwidth)
    {
        if (bandwidth == null) return "";
        return switch (bandwidth)
        {
            case MHZ_20 -> "20 MHz";
            case MHZ_40 -> "40 MHz";
            case MHZ_80 -> "80 MHz";
            case MHZ_80_PLUS -> "80+ MHz";
            case MHZ_160 -> "160 MHz";
            case MHZ_320 -> "320 MHz";
            default -> "";
        };
    }

    /**
     * @return A string representation of the provided Wi-Fi standard.
     */
    public static String formatStandard(Standard standard)
    {
        if (standard == null) return "";
        return switch (standard)
        {
            case IEEE80211 -> "802.11";
            case IEEE80211A -> "802.11a";
            case IEEE80211B -> "802.11b";
            case IEEE80211BG -> "802.11bg";
            case IEEE80211G -> "802.11g";
            case IEEE80211N -> "802.11n";
            case IEEE80211AC -> "802.11ac";
            case IEEE80211AX -> "802.11ax";
            case IEEE80211BE -> "802.11be";
            default -> "";
        };
    }

    /**
     * Gets the center channel frequency provided the channel number, bandwidth, and frequency.
     * <p>
     * This conversion is needed because Wi-Fi has some interesting channel usage as the bandwidth
     * grows. It seems that Wi-Fi 6E and 7 has fixed this in the 6 GHz range by making the chanel
     * and center channel equal to each other, but other channels need the conversion.
     */
    public static int getCenterChannel(int channelNumber, WifiBandwidth bandwidth, int frequencyMhz)
    {
        if (bandwidth == null) return channelNumber;

        if (frequencyMhz >= START_OF_6_GHZ_RANGE)
        {
            return switch (bandwidth)
            {
                case MHZ_20 -> channelNumber;
                case MHZ_40 -> get6GhzCenterChannelFor40Mhz(channelNumber);
                case MHZ_80, MHZ_80_PLUS -> get6GhzCenterChannelFor80Mhz(channelNumber);
                case MHZ_160 -> get6GhzCenterChannelFor160Mhz(channelNumber);
                case MHZ_320 -> channelNumber; // TODO It is unclear how to map the 320 MHz channels
                default -> channelNumber;
            };
            // https://www.rfwireless-world.com/calculators/WiFi-7-channel-number-to-center-frequency-conversion.html
        }

        return switch (bandwidth)
        {
            case MHZ_20 -> channelNumber;
            case MHZ_40 -> getF0IndexFor40MHz(channelNumber);
            case MHZ_80, MHZ_80_PLUS -> getF0IndexFor80MHz(channelNumber);
            case MHZ_160 -> getF0IndexFor160MHz(channelNumber);
            case MHZ_320 ->
                    channelNumber; // 320 MHz channels should only be supported in 6 GHz, which is handled above
            default -> channelNumber;
        };
    }

    private static int getF0IndexFor40MHz(int channelNumber)
    {
        return switch (channelNumber)
        {
            case 1 -> 1;
            case 2 -> 4;
            case 3 -> 5;
            case 4 -> 6;
            case 5 -> 7;
            case 6 -> 8;
            case 7 -> 9;
            case 8 -> 10;
            case 36, 40 -> 38;
            case 44, 48 -> 46;
            case 52, 56 -> 54;
            case 60, 64 -> 62;
            case 68, 72 -> 70;
            case 76, 80 -> 78;
            case 84, 88 -> 86;
            case 92, 96 -> 94;
            case 100, 104 -> 102;
            case 108, 112 -> 110;
            case 116, 120 -> 118;
            case 124, 128 -> 126;
            case 132, 136 -> 134;
            case 140, 144 -> 138;
            case 149, 153 -> 151;
            case 157, 161 -> 159;
            case 165, 169 -> 167;
            case 173, 177 -> 175;
            default -> channelNumber;
        };
    }

    private static int getF0IndexFor80MHz(int channelNumber)
    {
        return switch (channelNumber)
        {
            case 36, 40, 44, 48 -> 42;
            case 52, 56, 60, 64 -> 58;
            case 68, 72, 76, 80 -> 74;
            case 84, 88, 92, 96 -> 90;
            case 100, 104, 108, 112 -> 106;
            case 116, 120, 124, 128 -> 122;
            case 132, 136, 140, 144 -> 138;
            case 149, 153, 157, 161 -> 155;
            case 165, 169, 173, 177 -> 171;
            default -> channelNumber;
        };
    }

    private static int getF0IndexFor160MHz(int channelNumber)
    {
        return switch (channelNumber)
        {
            case 36, 40, 44, 48, 52, 56, 60, 64 -> 50;
            case 68, 72, 76, 80, 84, 88, 92, 96 -> 82;
            case 100, 104, 108, 112, 116, 120, 124, 128 -> 114;
            case 149, 153, 157, 161, 165, 169, 173, 177 -> 163;
            default -> channelNumber;
        };
    }

    private static int getF0IndexFor320MHz(int channelNumber)
    {
        return switch (channelNumber)
        {
            case 1, 5, 9, 13, 17, 21, 25, 29, 33, 37, 41, 45, 49, 53, 57, 61 -> 31;
            default -> -1;
        };
    }

    private static int get6GhzCenterChannelFor40Mhz(int channelNumber)
    {
        return switch (channelNumber)
        {
            case 1, 5 -> 3;
            case 9, 13 -> 11;
            case 17, 21 -> 19;
            case 25, 29 -> 27;
            case 33, 37 -> 35;
            case 41, 45 -> 43;
            case 49, 53 -> 51;
            case 57, 61 -> 59;
            case 65, 69 -> 67;
            case 73, 77 -> 75;
            case 81, 85 -> 83;
            case 89, 93 -> 91;
            case 97, 101 -> 99;
            case 105, 109 -> 107;
            case 113, 117 -> 115;
            case 121, 125 -> 123;
            case 129, 133 -> 131;
            case 137, 141 -> 139;
            case 145, 149 -> 147;
            case 153, 157 -> 155;
            case 161, 165 -> 163;
            case 169, 173 -> 171;
            case 177, 181 -> 179;
            case 185, 189 -> 187;
            case 193, 197 -> 195;
            case 201, 205 -> 203;
            case 209, 213 -> 211;
            case 217, 221 -> 219;
            case 225, 229 -> 227;
            default -> channelNumber;
        };
    }

    private static int get6GhzCenterChannelFor80Mhz(int channelNumber)
    {
        return switch (channelNumber)
        {
            case 1, 5, 9, 13 -> 7;
            case 17, 21, 25, 29 -> 23;
            case 33, 37, 41, 45 -> 39;
            case 49, 53, 57, 61 -> 55;
            case 65, 69, 73, 77 -> 71;
            case 81, 85, 89, 93 -> 87;
            case 97, 101, 105, 109 -> 103;
            case 113, 117, 121, 125 -> 119;
            case 129, 133, 137, 141 -> 135;
            case 145, 149, 153, 157 -> 151;
            case 161, 165, 169, 173 -> 167;
            case 177, 181, 185, 189 -> 183;
            case 193, 197, 201, 205 -> 199;
            case 209, 213, 217, 221 -> 215;
            default -> channelNumber;
        };
    }

    private static int get6GhzCenterChannelFor160Mhz(int channelNumber)
    {
        return switch (channelNumber)
        {
            case 1, 5, 9, 13, 17, 21, 25, 29 -> 15;
            case 33, 37, 41, 45, 49, 53, 57, 61 -> 47;
            case 65, 69, 73, 77, 81, 85, 89, 93 -> 79;
            case 97, 101, 105, 109, 113, 117, 121, 125 -> 111;
            case 129, 133, 137, 141, 145, 149, 153, 157 -> 143;
            case 161, 165, 169, 173, 177, 181, 185, 189 -> 175;
            case 193, 197, 201, 205, 209, 213, 217, 221 -> 207;
            default -> channelNumber;
        };
    }
}
