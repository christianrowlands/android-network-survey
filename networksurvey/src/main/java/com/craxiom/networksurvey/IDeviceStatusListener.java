package com.craxiom.networksurvey;

import com.craxiom.networksurvey.messaging.DeviceStatus;

public interface IDeviceStatusListener
{
    void onDeviceStatus(DeviceStatus deviceStatus);
}
