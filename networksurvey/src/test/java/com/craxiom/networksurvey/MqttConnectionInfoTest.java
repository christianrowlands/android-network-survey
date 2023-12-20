package com.craxiom.networksurvey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.craxiom.networksurvey.mqtt.MqttConnectionInfo;

import org.junit.Test;

/**
 * Tests the {@link com.craxiom.networksurvey.mqtt.MqttConnectionInfo} class.
 *
 * @since 0.1.3
 */
public class MqttConnectionInfoTest
{
    @Test
    public void validateTlsEnableMqttConnectionUri()
    {
        final String host = "mqtt.example.com";
        final int port = 8883;
        final boolean tlsEnabled = true;
        final String clientId = "Pixel3a";
        final String username = "bob";
        final String password = "bob's password";

        final MqttConnectionInfo mqttBrokerConnectionInfo = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, password, true, true, true, true, true, "");

        assertEquals(clientId, mqttBrokerConnectionInfo.getMqttClientId());
        assertEquals(username, mqttBrokerConnectionInfo.getMqttUsername());
        assertEquals(password, mqttBrokerConnectionInfo.getMqttPassword());
        assertTrue(mqttBrokerConnectionInfo.isCellularStreamEnabled());
        assertTrue(mqttBrokerConnectionInfo.isWifiStreamEnabled());
        assertTrue(mqttBrokerConnectionInfo.isGnssStreamEnabled());
    }

    @Test
    public void validatePlaintextMqttConnectionUri()
    {
        final String host = "mqtt.example.com";
        final int port = 1883;
        final boolean tlsEnabled = false;
        final String clientId = "Pixel3a";
        final String username = "bob";
        final String password = "bob's password";

        final MqttConnectionInfo mqttBrokerConnectionInfo = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, password, true, true, true, true, true, "");

        assertEquals(clientId, mqttBrokerConnectionInfo.getMqttClientId());
        assertEquals(username, mqttBrokerConnectionInfo.getMqttUsername());
        assertEquals(password, mqttBrokerConnectionInfo.getMqttPassword());
        assertTrue(mqttBrokerConnectionInfo.isCellularStreamEnabled());
        assertTrue(mqttBrokerConnectionInfo.isWifiStreamEnabled());
        assertTrue(mqttBrokerConnectionInfo.isGnssStreamEnabled());
    }

    @Test
    public void validateMqttConnectionInfoEquals_correct()
    {
        final String host = "mqtt.example.com";
        final int port = 1883;
        final boolean tlsEnabled = false;
        final String clientId = "iPhone";
        final String username = "bob";
        final String password = "bob's password";

        final MqttConnectionInfo mqttBrokerConnectionInfo1 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, password, true, true, true, true, true, "");
        final MqttConnectionInfo mqttBrokerConnectionInfo2 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, password, true, true, true, true, true, "");

        assertEquals(mqttBrokerConnectionInfo1, mqttBrokerConnectionInfo2);
    }

    @Test
    public void validateMqttConnectionInfoEquals_invalid()
    {
        final String host = "mqtt.example.com";
        final int port = 1883;
        final boolean tlsEnabled = false;
        final String clientId = "iPhone";
        final String username = "bob";
        final String password = "bob's password";

        MqttConnectionInfo connectionInfo1 = new MqttConnectionInfo("mqtt.example.com", port, tlsEnabled, clientId, username, password, true, true, true, true, true, "");
        MqttConnectionInfo connectionInfo2 = new MqttConnectionInfo("craxiom.com", port, tlsEnabled, clientId, username, password, true, true, true, true, true, "");
        assertNotEquals(connectionInfo1, connectionInfo2);

        connectionInfo1 = new MqttConnectionInfo(host, 123, tlsEnabled, clientId, username, password, true, true, true, true, true, "");
        connectionInfo2 = new MqttConnectionInfo(host, 1234, tlsEnabled, clientId, username, password, true, true, true, true, true, "");
        assertNotEquals(connectionInfo1, connectionInfo2);

        connectionInfo1 = new MqttConnectionInfo(host, port, true, clientId, username, password, true, true, true, true, true, "");
        connectionInfo2 = new MqttConnectionInfo(host, port, false, clientId, username, password, true, true, true, true, true, "");
        assertNotEquals(connectionInfo1, connectionInfo2);

        connectionInfo1 = new MqttConnectionInfo(host, port, tlsEnabled, "Pixel4", username, password, true, true, true, true, true, "");
        connectionInfo2 = new MqttConnectionInfo(host, port, tlsEnabled, "S20", username, password, true, true, true, true, true, "");
        assertNotEquals(connectionInfo1, connectionInfo2);

        connectionInfo1 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, "john", password, true, true, true, true, true, "");
        connectionInfo2 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, "steve", password, true, true, true, true, true, "");
        assertNotEquals(connectionInfo1, connectionInfo2);

        connectionInfo1 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, "bob's password", true, true, true, true, true, "");
        connectionInfo2 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, "bob's burgers", true, true, true, true, true, "");
        assertNotEquals(connectionInfo1, connectionInfo2);

        // Test cellularStreamEnabled inequality
        connectionInfo1 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, "bob's password", false, true, true, true, true, "");
        connectionInfo2 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, "bob's burgers", true, true, true, true, true, "");
        assertNotEquals(connectionInfo1, connectionInfo2);

        // Test wifiStreamEnabled inequality
        connectionInfo1 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, "bob's password", true, false, true, true, true, "");
        connectionInfo2 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, "bob's burgers", true, true, true, true, true, "");
        assertNotEquals(connectionInfo1, connectionInfo2);

        // Test gnssStreamEnabled inequality
        connectionInfo1 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, "bob's password", true, true, true, false, true, "");
        connectionInfo2 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, "bob's burgers", true, true, true, true, true, "");
        assertNotEquals(connectionInfo1, connectionInfo2);
    }
}
