package com.craxiom.networksurvey.mqtt;

import com.craxiom.mqttlibrary.MqttQos;
import com.craxiom.mqttlibrary.connection.BrokerConnectionInfo;

/**
 * Holds the MQTT connection information including per-protocol stream enable flags.
 */
public class MqttConnectionInfo extends BrokerConnectionInfo
{
    private final boolean isCellularStreamEnabled;
    private final boolean isPhoneStateStreamEnabled;
    private final boolean isWifiStreamEnabled;
    private final boolean isBluetoothStreamEnabled;
    private final boolean isGnssStreamEnabled;
    private final boolean isDeviceStatusStreamEnabled;
    private final boolean isWatchlistStreamEnabled;
    private final String deviceName;

    public MqttConnectionInfo(String mqttBrokerHost, int portNumber, boolean tlsEnabled, String mqttClientId, String mqttUsername,
                              String mqttPassword, boolean isCellularStreamEnabled, boolean isWifiStreamEnabled,
                              boolean isBluetoothStreamEnabled, boolean isGnssStreamEnabled, boolean isDeviceStatusStreamEnabled,
                              boolean isPhoneStateStreamEnabled, boolean isWatchlistStreamEnabled,
                              String topicPrefix, String deviceName, MqttQos mqttQos)
    {
        super(mqttBrokerHost, portNumber, tlsEnabled, mqttClientId, mqttUsername, mqttPassword, topicPrefix, mqttQos);
        this.isCellularStreamEnabled = isCellularStreamEnabled;
        this.isPhoneStateStreamEnabled = isPhoneStateStreamEnabled;
        this.isWifiStreamEnabled = isWifiStreamEnabled;
        this.isBluetoothStreamEnabled = isBluetoothStreamEnabled;
        this.isGnssStreamEnabled = isGnssStreamEnabled;
        this.isDeviceStatusStreamEnabled = isDeviceStatusStreamEnabled;
        this.isWatchlistStreamEnabled = isWatchlistStreamEnabled;
        this.deviceName = deviceName;
    }

    public boolean isCellularStreamEnabled()
    {
        return isCellularStreamEnabled;
    }

    public boolean isPhoneStateStreamEnabled()
    {
        return isPhoneStateStreamEnabled;
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

    public boolean isWatchlistStreamEnabled()
    {
        return isWatchlistStreamEnabled;
    }

    public String getDeviceName()
    {
        return deviceName;
    }
}
