package com.craxiom.networksurvey.mqtt;

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
