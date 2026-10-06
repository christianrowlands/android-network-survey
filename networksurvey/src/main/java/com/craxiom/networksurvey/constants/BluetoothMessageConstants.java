package com.craxiom.networksurvey.constants;

import android.bluetooth.BluetoothDevice;

import com.craxiom.messaging.bluetooth.AddressType;
import com.craxiom.messaging.bluetooth.SupportedTechnologies;
import com.craxiom.messaging.bluetooth.Technology;

import timber.log.Timber;

/**
 * The constants associated with the Bluetooth record in the Network Survey Messaging API.
 * <p>
 * Also contains any utility methods to help with converting any of the values.
 *
 * @since 1.0.0
 */
public class BluetoothMessageConstants
{
    private BluetoothMessageConstants()
    {
    }

    public static final String BLUETOOTH_RECORD_MESSAGE_TYPE = "BluetoothRecord";

    /**
     * Given the Android API Bluetooth Type associated with a scan result device, return the Network Survey Messaging
     * API defined Bluetooth Technology.
     *
     * @param androidBluetoothType The Android API Bluetooth Type associated with a remote Bluetooth device.
     * @return The Network Survey Messaging API Bluetooth Technology.
     */
    public static SupportedTechnologies getSupportedTechnologies(int androidBluetoothType)
    {
        switch (androidBluetoothType)
        {
            case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
                return SupportedTechnologies.UNKNOWN;

            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                return SupportedTechnologies.BR_EDR;

            case BluetoothDevice.DEVICE_TYPE_LE:
                return SupportedTechnologies.LE;

            case BluetoothDevice.DEVICE_TYPE_DUAL:
                return SupportedTechnologies.DUAL;

            default:
                return null;
        }
    }

    /**
     * Given a Protocol Buffer defined Bluetooth Technology enum value, return a user friendly string
     * representation.
     *
     * @param technology The Technology enum to convert.
     * @return The user friendly Technology, or an empty String if it is unknown/could not be converted.
     */
    public static String getTechnologyString(Technology technology)
    {
        switch (technology)
        {
            case UNKNOWN:
                return "Unknown";

            case BR_EDR:
                return "BR/EDR";

            case LE:
                return "LE";

            case UNRECOGNIZED:
                break;
        }

        return "";
    }

    /**
     * Given a Protocol Buffer defined Bluetooth Supported Technologies enum value, return a user friendly string
     * representation.
     *
     * @param supportedTechnologies The Supported Technologies enum to convert.
     * @return The user friendly Supported Technologies, or an empty String if it is unknown/could not be converted.
     */
    public static String getSupportedTechString(SupportedTechnologies supportedTechnologies)
    {
        switch (supportedTechnologies)
        {
            case UNKNOWN:
                return "Unknown";

            case BR_EDR:
                return "BR/EDR";

            case LE:
                return "LE";

            case DUAL:
                return "DUAL";

            case UNRECOGNIZED:
                break;
        }

        return "";
    }

    /**
     * Converts the Android OS-provided address type (int) to the protobuf-defined {@link AddressType}.
     *
     * @param osAddressType The integer value returned from {@link BluetoothDevice#getAddressType()}.
     * @return The corresponding {@link AddressType} enum.
     */
    public static AddressType mapOsAddressTypeToProto(int osAddressType)
    {
        switch (osAddressType)
        {
            case BluetoothDevice.ADDRESS_TYPE_PUBLIC:
                return AddressType.PUBLIC;
            case BluetoothDevice.ADDRESS_TYPE_RANDOM:
                return AddressType.RANDOM;
            case BluetoothDevice.ADDRESS_TYPE_ANONYMOUS:
                return AddressType.ANONYMOUS;
            case BluetoothDevice.ADDRESS_TYPE_UNKNOWN:
                return AddressType.UNKNOWN;
            default:
                Timber.w("Unknown OS address type: " + osAddressType + ", defaulting to UNKNOWN");
                return AddressType.UNKNOWN;
        }
    }
}
