package com.craxiom.networksurvey.constants.csv;

/**
 * The constants associated with the Wi-Fi CSV file headers.
 * <p>
 * The constants in this class are intended to match the constants defined in the
 * <a href="https://messaging.networksurvey.app/">Network Survey Messaging API</a>.
 */
public class WifiCsvConstants extends CellularCsvConstants
{
    private WifiCsvConstants()
    {
    }

    public static final String SOURCE_ADDRESS = "sourceAddress";
    public static final String DESTINATION_ADDRESS = "destinationAddress";
    public static final String BSSID = "bssid";
    public static final String BEACON_INTERVAL = "beaconInterval";
    public static final String SERVICE_SET_TYPE = "serviceSetType";
    public static final String SSID = "ssid";
    public static final String SUPPORTED_RATES = "supportedRates";
    public static final String EXT_SUPPORTED_RATES = "extendedSupportedRates";
    public static final String CIPHER_SUITES = "cipherSuites";
    public static final String AKM_SUITES = "akmSuites";
    public static final String ENCRYPTION_TYPE = "encryptionType";
    public static final String WPA = "wps";
    public static final String CHANNEL = "channel";
    public static final String FREQ_MHZ = "frequencyMhz";
    public static final String SIGNAL_STRENGTH = "signalStrength";
    public static final String SNR = "snr";
    public static final String NODE_TYPE = "nodeType";
    public static final String STANDARD = "standard";
    public static final String PASSPOINT = "passpoint";
    public static final String BANDWIDTH = "bandwidth";
}
