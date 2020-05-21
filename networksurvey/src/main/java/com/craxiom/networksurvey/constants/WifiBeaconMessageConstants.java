package com.craxiom.networksurvey.constants;

import com.craxiom.networksurvey.messaging.CipherSuite;
import com.craxiom.networksurvey.messaging.EncryptionType;

/**
 * The constants associated with the LTE table in the GeoPackage file.
 * <p>
 * Also contains any utility methods to help with converting any of the values.
 *
 * @since 0.1.2
 */
public class WifiBeaconMessageConstants extends MessageConstants
{
    private WifiBeaconMessageConstants()
    {
    }

    public static final String WIFI_BEACON_RECORDS_TABLE_NAME = "80211_BEACON_MESSAGE";

    public static final String SOURCE_ADDRESS_COLUMN = "Source_Address";
    public static final String DESTINATION_ADDRESS_COLUMN = "Destination_Address";
    public static final String BSSID_COLUMN = "BSSID";

    public static final String BEACON_INTERVAL_COLUMN = "Beacon_Interval";
    public static final String SERVICE_SET_TYPE_COLUMN = "Service_Set_Type";
    public static final String SSID_COLUMN = "SSID";
    public static final String SUPPORTED_RATES_COLUMN = "Supported_Rates";
    public static final String EXTENDED_SUPPORTED_RATES_COLUMN = "Extended_Supported_Rates";
    public static final String CIPHER_SUITES_COLUMN = "Cipher_Suites";
    public static final String AKM_SUITES_COLUMN = "AKM_Suites";
    public static final String ENCRYPTION_TYPE_COLUMN = "Encryption_Type";
    public static final String WPS_COLUMN = "WPS";

    public static final String CHANNEL_COLUMN = "Channel";
    public static final String FREQUENCY_MHZ_COLUMN = "Frequency";
    public static final String SIGNAL_STRENGTH_COLUMN = "Signal Strength";
    public static final String SNR_COLUMN = "SNR";
    public static final String NODE_TYPE_COLUMN = "Node Type";
    public static final String STANDARD_COLUMN = "Standard";

    public static final String HIDDEN_SSID_PLACEHOLDER = "<Hidden SSID>";

    /**
     * Takes a frequency in MHz and converts it to an 802.11 channel number.  For example, a frequency of 2417 would
     * return a channel number of 2.  This method was developed using IEEE Std 802.11-2012.
     *
     * @param frequency The frequency in MHz.
     * @return The corresponding channel number. If the corresponding channel number was not found, -1 is returned.
     */
    public static short convertFrequencyToChannelNumber(int frequency)
    {
        // For 2.4 GHz channels 1 to 13
        if (frequency >= 2412 && frequency <= 2472)
        {
            if ((frequency - 2407) % 5 != 0)
            {
                return -1;
            }

            return (short) ((frequency - 2407) / 5);
        }

        // For 2.4 GHz channel 14
        if (frequency == 2484)
        {
            return 14;
        }

        // Technically the following formula does not cover all the possible operating bands defined in the 802.11
        // specification.  However, it covers the most commonly utilized frequencies, and by returning -1, the operator
        // will have an indication that they need to do the conversion themselves.

        // For some of the 5 GHz channels
        if (frequency >= 5170 && frequency <= 5825) // Might want to use 5920 instead of 5825, but 5825 is more conservative
        {
            if (frequency % 5 != 0)
            {
                return -1;
            }

            return (short) ((frequency - 5000) / 5);
        }

        return -1;
    }

    /**
     * Given a Protocol Buffer defined 802.11 Encryption Type, return a user friendly string representation that follows
     * the ICD used for the GeoPackage logging.
     *
     * @param encryptionType The Encryption Type enum to convert.
     * @return The user friendly Encryption Type, or an empty String if it is unknown/could not be converted.
     */
    public static String getEncryptionTypeString(EncryptionType encryptionType)
    {
        switch (encryptionType)
        {
            case ENC_UNKNOWN:
                return "Unknown";

            case ENC_OPEN:
                return "Open";

            case ENC_WEP:
                return "WEP";

            case ENC_WPA:
                return "WPA";

            case ENC_WPA_WPA2:
                return "WPA/WPA2";

            case ENC_WPA2:
                return "WPA2";

            case ENC_WPA3:
                return "WPA3";

            case UNRECOGNIZED:
                break;
        }

        return "";
    }

    /**
     * Given a Protocol Buffer defined 802.11 Cipher Suite, return a user friendly string representation that follows
     * the ICD used for the GeoPackage logging.
     *
     * @param cipherSuite The Cipher Suite enum to convert.
     * @return The user friendly Cipher Suite, or an empty String if it is unknown/could not be converted.
     */
    public static String getCipherSuiteString(CipherSuite cipherSuite)
    {
        switch (cipherSuite)
        {
            case CIPHER_UNKNOWN:
                return "Unknown";

            case CIPHER_WEP_40:
                return "WEP-40";

            case CIPHER_TKIP:
                return "TKIP";

            case CIPHER_CCMP:
                return "CCMP";

            case CIPHER_WEP_104:
                return "WEP-104";

            case CIPHER_OPEN:
                return "Open";

            case CIPHER_WEP:
                return "WEP";

            case UNRECOGNIZED:
                break;
        }

        return "";
    }
}
