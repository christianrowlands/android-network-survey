package com.craxiom.networksurvey.mqtt;

import java.util.Objects;

/**
 * Holds all the information for an MQTT Broker connection.
 *
 * @since 0.1.1
 */
public class MqttBrokerConnectionInfo
{
    private static final String SSL_URI_PREFIX = "ssl://";
    private static final String TCP_URI_PREFIX = "tcp://";

    private final String mqttServerUri;
    private final String mqttClientId;
    private final String mqttUsername;
    private final String mqttPassword;

    private final int hashCode;

    /**
     * Constructs this info object with all the information needed to connect to an MQTT Broker.
     *
     * @param mqttBrokerHost The IP or hostname (hostname preferred) of the MQTT broker.
     * @param portNumber     The port number of the MQTT broker (typically 8883 for TLS, and 1883 for plaintext).
     * @param tlsEnabled     True if SSL/TLS should be used, false if the connection should be plaintext.
     * @param mqttClientId   The client ID that is used to represent this client to the server.
     * @param mqttUsername   The username used to authenticate to the MQTT Broker.
     * @param mqttPassword   The password used to authenticate to the MQTT Broker.
     * @since 0.1.3
     */
    public MqttBrokerConnectionInfo(String mqttBrokerHost, int portNumber, boolean tlsEnabled, String mqttClientId, String mqttUsername, String mqttPassword)
    {
        this(getMqttBrokerUriString(mqttBrokerHost, portNumber, tlsEnabled), mqttClientId, mqttUsername, mqttPassword);
    }

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

    /**
     * Given the host, port, and TLS setting, create and return the MQTT broker URI that can be used by the
     * {@link MqttConnection} client.
     *
     * @param mqttBrokerHost The IP or hostname (hostname preferred) of the MQTT broker.
     * @param portNumber     The port number of the MQTT broker (typically 8883 for TLS, and 1883 for plaintext).
     * @param tlsEnabled     True if SSL/TLS should be used, false if the connection should be plaintext.
     * @return The MQTT Broker URI String that can be use to connect to the MQTT broker.
     * @since 0.1.3
     */
    public static String getMqttBrokerUriString(String mqttBrokerHost, int portNumber, boolean tlsEnabled)
    {
        final String uriPrefix = tlsEnabled ? SSL_URI_PREFIX : TCP_URI_PREFIX;
        return uriPrefix + mqttBrokerHost + ":" + portNumber;
    }
}
