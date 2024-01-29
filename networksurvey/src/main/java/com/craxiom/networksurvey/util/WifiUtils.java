package com.craxiom.networksurvey.util;

import com.craxiom.messaging.wifi.EncryptionType;
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
}
