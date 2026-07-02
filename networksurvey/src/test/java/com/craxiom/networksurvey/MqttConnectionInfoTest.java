package com.craxiom.networksurvey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.craxiom.mqttlibrary.MqttQos;
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

        final MqttConnectionInfo mqttBrokerConnectionInfo = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, password, true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);

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

        final MqttConnectionInfo mqttBrokerConnectionInfo = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, password, true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);

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

        final MqttConnectionInfo mqttBrokerConnectionInfo1 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, password, true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);
        final MqttConnectionInfo mqttBrokerConnectionInfo2 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, password, true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);

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

        MqttConnectionInfo connectionInfo1 = new MqttConnectionInfo("mqtt.example.com", port, tlsEnabled, clientId, username, password, true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);
        MqttConnectionInfo connectionInfo2 = new MqttConnectionInfo("craxiom.com", port, tlsEnabled, clientId, username, password, true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);
        assertNotEquals(connectionInfo1, connectionInfo2);

        connectionInfo1 = new MqttConnectionInfo(host, 123, tlsEnabled, clientId, username, password, true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);
        connectionInfo2 = new MqttConnectionInfo(host, 1234, tlsEnabled, clientId, username, password, true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);
        assertNotEquals(connectionInfo1, connectionInfo2);

        connectionInfo1 = new MqttConnectionInfo(host, port, true, clientId, username, password, true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);
        connectionInfo2 = new MqttConnectionInfo(host, port, false, clientId, username, password, true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);
        assertNotEquals(connectionInfo1, connectionInfo2);

        connectionInfo1 = new MqttConnectionInfo(host, port, tlsEnabled, "Pixel4", username, password, true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);
        connectionInfo2 = new MqttConnectionInfo(host, port, tlsEnabled, "S20", username, password, true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);
        assertNotEquals(connectionInfo1, connectionInfo2);

        connectionInfo1 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, "john", password, true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);
        connectionInfo2 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, "steve", password, true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);
        assertNotEquals(connectionInfo1, connectionInfo2);

        connectionInfo1 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, "bob's password", true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);
        connectionInfo2 = new MqttConnectionInfo(host, port, tlsEnabled, clientId, username, "bob's burgers", true, true, true, true, true, true, true, "", null, MqttQos.AT_LEAST_ONCE);
        assertNotEquals(connectionInfo1, connectionInfo2);

        // Note that the stream flags are deliberately not part of equals/hashCode; equality comes from
        // BrokerConnectionInfo, which only compares the connection fields. The flag getters are instead
        // verified positionally in validateStreamFlagGettersMapToConstructorPositions.
    }

    @Test
    public void validateStreamFlagGettersMapToConstructorPositions()
    {
        // One-hot through each of the seven stream flags to catch a transposed boolean anywhere in the
        // constructor's long parameter list. Any single swapped pair of arguments fails one of these.
        for (int flagIndex = 0; flagIndex < 7; flagIndex++)
        {
            final boolean[] flags = new boolean[7];
            flags[flagIndex] = true;

            final MqttConnectionInfo info = new MqttConnectionInfo("mqtt.example.com", 8883, true,
                    "Pixel3a", "bob", "bob's password",
                    flags[0], flags[1], flags[2], flags[3], flags[4], flags[5], flags[6],
                    "", null, MqttQos.AT_LEAST_ONCE);

            assertEquals("cellular flag mismatch for one-hot index " + flagIndex, flags[0], info.isCellularStreamEnabled());
            assertEquals("wifi flag mismatch for one-hot index " + flagIndex, flags[1], info.isWifiStreamEnabled());
            assertEquals("bluetooth flag mismatch for one-hot index " + flagIndex, flags[2], info.isBluetoothStreamEnabled());
            assertEquals("gnss flag mismatch for one-hot index " + flagIndex, flags[3], info.isGnssStreamEnabled());
            assertEquals("device status flag mismatch for one-hot index " + flagIndex, flags[4], info.isDeviceStatusStreamEnabled());
            assertEquals("phone state flag mismatch for one-hot index " + flagIndex, flags[5], info.isPhoneStateStreamEnabled());
            assertEquals("watchlist flag mismatch for one-hot index " + flagIndex, flags[6], info.isWatchlistStreamEnabled());
        }
    }
}
