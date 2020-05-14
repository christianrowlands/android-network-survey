package com.craxiom.networksurvey.util;

import com.craxiom.networksurvey.messaging.EncryptionType;

/**
 * A few pieces of information come packaged in the {@link android.net.wifi.ScanResult#capabilities} string.  This class
 * offers utility methods to extract the relevant information from that capabilities string.
 *
 * @since 0.1.2
 */
public class WifiCapabilitiesUtils
{
    /**
     * Given the {@link android.net.wifi.ScanResult#capabilities} string, return the appropriate {@link EncryptionType}.
     *
     * @param capabilities The capabilities string that contains the encryption type.
     * @return The Encryption Type enum.
     */
    public static EncryptionType getEncryptionType(String capabilities)
    {
        if (capabilities.contains("WEP"))
        {
            return EncryptionType.ENC_WEP;
        }
        if (capabilities.contains("WPA3"))
        {
            return EncryptionType.ENC_WPA3;
        } else
        {
            final boolean containsWpa = capabilities.contains("WPA]");
            final boolean containsWpa2 = capabilities.contains("WPA2");
            if (containsWpa && containsWpa2)
            {
                return EncryptionType.ENC_WPA_WPA2;
            } else if (containsWpa2)
            {
                return EncryptionType.ENC_WPA2;
            } else if (containsWpa)
            {
                return EncryptionType.ENC_WPA;
            } else if (!capabilities.contains("RSN"))
            {
                // If RSN is not present then the network is open
                return EncryptionType.ENC_OPEN;
            } else
            {
                return EncryptionType.ENC_UNKNOWN;
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
}
