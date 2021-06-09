package com.craxiom.networksurvey.constants;

import android.telephony.NetworkRegistrationInfo;

import com.craxiom.messaging.phonestate.Domain;

/**
 * The constants associated with the Device Status message.
 *
 * @since 0.2.0
 */
public class DeviceStatusMessageConstants extends MessageConstants
{
    private DeviceStatusMessageConstants()
    {
    }

    public static final String DEVICE_STATUS_MESSAGE_TYPE = "DeviceStatus";

    /**
     * The message type for the {@link com.craxiom.messaging.PhoneState} message as defined by the Network Survey
     * Messaging API.
     *
     * @since 1.4.0
     */
    public static final String PHONE_STATE_MESSAGE_TYPE = "PhoneState";

    /**
     * Given an Android defined Network Info Domain, return a Protocol Buffer defined {@link Domain}.
     *
     * @param domain The Android domain int to convert to protobuf.
     * @return The Protobuf defined {@link Domain} enum that lines up with the provided int.
     * @since 1.4.0
     */
    public static Domain convertDomain(int domain)
    {
        switch (domain)
        {
            case NetworkRegistrationInfo.DOMAIN_PS:
                return Domain.PS;

            case NetworkRegistrationInfo.DOMAIN_CS:
                return Domain.CS;

            default:
                return Domain.UNKNOWN;
        }
    }
}
