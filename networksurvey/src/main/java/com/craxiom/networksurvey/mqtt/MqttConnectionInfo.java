package com.craxiom.networksurvey.mqtt;

import com.craxiom.mqttlibrary.connection.BrokerConnectionInfo;

public class MqttConnectionInfo extends BrokerConnectionInfo
{
    private final boolean isCellularStreamEnabled;
    private final boolean isWifiStreamEnabled;
    private final boolean isBluetoothStreamEnabled;
    private final boolean isGnssStreamEnabled;
    private final boolean isDeviceStatusStreamEnabled;

    public MqttConnectionInfo(String mqttBrokerHost, int portNumber, boolean tlsEnabled, String mqttClientId, String mqttUsername,
                              String mqttPassword, boolean isCellularStreamEnabled, boolean isWifiStreamEnabled,
                              boolean isBluetoothStreamEnabled, boolean isGnssStreamEnabled, boolean isDeviceStatusStreamEnabled,
                              String topicPrefix)
    {
        super(mqttBrokerHost, portNumber, tlsEnabled, mqttClientId, mqttUsername, mqttPassword, topicPrefix);
        this.isCellularStreamEnabled = isCellularStreamEnabled;
        this.isWifiStreamEnabled = isWifiStreamEnabled;
        this.isBluetoothStreamEnabled = isBluetoothStreamEnabled;
        this.isGnssStreamEnabled = isGnssStreamEnabled;
        this.isDeviceStatusStreamEnabled = isDeviceStatusStreamEnabled;
    }

    public boolean isCellularStreamEnabled()
    {
        return isCellularStreamEnabled;
    }

    public boolean isWifiStreamEnabled()
    {
        return isWifiStreamEnabled;
    }

    public boolean isBluetoothStreamEnabled()
    {
        return isBluetoothStreamEnabled;
    }

    public boolean isGnssStreamEnabled()
    {
        return isGnssStreamEnabled;
    }

    public boolean isDeviceStatusStreamEnabled()
    {
        return isDeviceStatusStreamEnabled;
    }
}
