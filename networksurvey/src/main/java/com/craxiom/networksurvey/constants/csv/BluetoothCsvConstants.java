package com.craxiom.networksurvey.constants.csv;

/**
 * The constants associated with the Bluetooth CSV file headers.
 * <p>
 * The constants in this class are intended to match the constants defined in the
 * <a href="https://messaging.networksurvey.app/">Network Survey Messaging API</a>.
 */
public class BluetoothCsvConstants extends SurveyCsvConstants
{
    private BluetoothCsvConstants()
    {
    }

    public static final String SOURCE_ADDRESS = "sourceAddress";
    public static final String DESTINATION_ADDRESS = "destinationAddress";
    public static final String SIGNAL_STRENGTH = "signalStrength";
    public static final String TX_POWER = "txPower";
    public static final String TECHNOLOGY = "technology";
    public static final String SUPPORTED_TECHNOLOGIES = "supportedTechnologies";
    public static final String OTA_DEVICE_NAME = "otaDeviceName";
    public static final String CHANNEL = "channel";
    public static final String ADDRESS_TYPE = "addressType";
    public static final String DEVICE_CLASS = "deviceClass";
    public static final String SERVICE_UUIDS = "serviceUuids";
    public static final String COMPANY_ID = "companyId";
}
