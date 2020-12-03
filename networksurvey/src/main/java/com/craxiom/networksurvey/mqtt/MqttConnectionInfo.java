package com.craxiom.networksurvey.mqtt;

import com.craxiom.mqttlibrary.connection.BrokerConnectionInfo;

public class MqttConnectionInfo extends BrokerConnectionInfo
{
    private final boolean isCellularStreamEnabled;
    private final boolean isWifiStreamEnabled;
    private final boolean isGnssStreamEnabled;

    public MqttConnectionInfo(String mqttBrokerHost, int portNumber, boolean tlsEnabled, String mqttClientId, String mqttUsername, String mqttPassword, boolean isCellularStreamEnabled, boolean isWifiStreamEnabled, boolean isGnssStreamEnabled)
    {
        super(mqttBrokerHost, portNumber, tlsEnabled, mqttClientId, mqttUsername, mqttPassword);
        this.isCellularStreamEnabled = isCellularStreamEnabled;
        this.isWifiStreamEnabled = isWifiStreamEnabled;
        this.isGnssStreamEnabled = isGnssStreamEnabled;
    }

    public boolean isCellularStreamEnabled()
    {
        return isCellularStreamEnabled;
    }

    public boolean isWifiStreamEnabled()
    {
        return isWifiStreamEnabled;
    }

    public boolean isGnssStreamEnabled()
    {
        return isGnssStreamEnabled;
    }
}
