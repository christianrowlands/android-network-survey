package com.craxiom.networksurvey.listeners;

import com.craxiom.messaging.DeviceStatus;
import com.craxiom.messaging.PhoneState;

/**
 * Listener interface for those interested in being notified whenever a new Device Status message is ready.
 *
 * @since 0.0.4
 */
public interface IDeviceStatusListener
{
    /**
     * Notification that a new Device Status message is ready.
     *
     * @param deviceStatus The Device Status message.
     */
    void onDeviceStatus(DeviceStatus deviceStatus);

    /**
     * Notification that a new Phone State message is ready. This typically indicates that the state of the phone
     * changed. For example, the serving cell changed.
     *
     * @param phoneState The Phone State message.
     * @since 1.4.0
     */
    default void onPhoneState(PhoneState phoneState)
    {
    }
}
