package com.craxiom.networksurvey.listeners;

import com.craxiom.messaging.DeviceStatus;

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
}
