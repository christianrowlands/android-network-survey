package com.craxiom.networksurvey.mqtt;

import java.util.Objects;

/**
 * Holds all the information for an MQTT Broker connection.
 *
 * @since 0.1.1
 */
public class MqttBrokerConnectionInfo
{
    private final String mqttServerUri;
    private final String mqttClientId;
    private final String mqttUsername;
    private final String mqttPassword;

    private final int hashCode;

    /**
     * Constructs this info object with all the information needed to connect to an MQTT Broker.
     *
     * @param mqttServerUri The full URI including the connection protocol (e.g. "ssl://") and the port number at the end.
     * @param mqttClientId  The client ID that is used to represent this client to the server.
     * @param mqttUsername  The username used to authenticate to the MQTT Broker.
     * @param mqttPassword  The password used to authenticate to the MQTT Broker.
     */
    public MqttBrokerConnectionInfo(String mqttServerUri, String mqttClientId, String mqttUsername, String mqttPassword)
    {
        this.mqttServerUri = mqttServerUri;
        this.mqttClientId = mqttClientId;
        this.mqttUsername = mqttUsername;
        this.mqttPassword = mqttPassword;

        int result = mqttServerUri != null ? mqttServerUri.hashCode() : 0;
        result = 31 * result + (mqttClientId != null ? mqttClientId.hashCode() : 0);
        result = 31 * result + (mqttUsername != null ? mqttUsername.hashCode() : 0);
        result = 31 * result + (mqttPassword != null ? mqttPassword.hashCode() : 0);
        hashCode = result;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MqttBrokerConnectionInfo that = (MqttBrokerConnectionInfo) o;

        if (!Objects.equals(mqttServerUri, that.mqttServerUri)) return false;
        if (!Objects.equals(mqttClientId, that.mqttClientId)) return false;
        if (!Objects.equals(mqttUsername, that.mqttUsername)) return false;
        return Objects.equals(mqttPassword, that.mqttPassword);
    }

    @Override
    public int hashCode()
    {
        return hashCode;
    }

    public String getMqttServerUri()
    {
        return mqttServerUri;
    }

    public String getMqttClientId()
    {
        return mqttClientId;
    }

    public String getMqttUsername()
    {
        return mqttUsername;
    }

    public String getMqttPassword()
    {
        return mqttPassword;
    }
}
