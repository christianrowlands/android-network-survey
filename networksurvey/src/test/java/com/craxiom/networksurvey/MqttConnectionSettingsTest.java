package com.craxiom.networksurvey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.craxiom.mqttlibrary.MqttQos;
import com.craxiom.mqttlibrary.connection.BrokerConnectionInfo;
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings;

import org.junit.Test;

/**
 * Tests the {@link MqttConnectionSettings} class, particularly QoS configuration.
 *
 * @since 1.17.0
 */
public class MqttConnectionSettingsTest
{
    private static final String TEST_HOST = "mqtt.example.com";
    private static final int TEST_PORT = 8883;
    private static final String TEST_DEVICE_NAME = "TestDevice";
    private static final String TEST_USERNAME = "user";
    private static final String TEST_PASSWORD = "pass";
    private static final String TEST_TOPIC_PREFIX = "test/";

    @Test
    public void testBuildWithNullQos_usesDefault()
    {
        MqttConnectionSettings settings = new MqttConnectionSettings.Builder()
                .host(TEST_HOST)
                .port(TEST_PORT)
                .deviceName(TEST_DEVICE_NAME)
                .mqttQos(null)
                .build();

        assertEquals("Null QoS should default to 1", Integer.valueOf(1), settings.mqttQos());
    }

    @Test
    public void testBuildWithValidQos_usesProvidedValue()
    {
        MqttConnectionSettings settings0 = new MqttConnectionSettings.Builder()
                .host(TEST_HOST)
                .port(TEST_PORT)
                .mqttQos(0)
                .build();
        assertEquals("QoS 0 should be preserved", Integer.valueOf(0), settings0.mqttQos());

        MqttConnectionSettings settings1 = new MqttConnectionSettings.Builder()
                .host(TEST_HOST)
                .port(TEST_PORT)
                .mqttQos(1)
                .build();
        assertEquals("QoS 1 should be preserved", Integer.valueOf(1), settings1.mqttQos());

        MqttConnectionSettings settings2 = new MqttConnectionSettings.Builder()
                .host(TEST_HOST)
                .port(TEST_PORT)
                .mqttQos(2)
                .build();
        assertEquals("QoS 2 should be preserved", Integer.valueOf(2), settings2.mqttQos());
    }

    @Test
    public void testToMqttConnectionInfo_withNullQos()
    {
        // Create settings with null QoS using record constructor directly
        MqttConnectionSettings settings = new MqttConnectionSettings(
                TEST_HOST, TEST_PORT, true, TEST_DEVICE_NAME,
                TEST_USERNAME, TEST_PASSWORD, TEST_TOPIC_PREFIX,
                null, // mqttQos is null
                true, true, true, true, true
        );

        BrokerConnectionInfo connectionInfo = settings.toMqttConnectionInfo();

        assertNotNull("Connection info should not be null", connectionInfo);
        assertEquals("Null QoS should convert to default QoS 1",
                MqttQos.AT_LEAST_ONCE, connectionInfo.getMqttQos());
    }

    @Test
    public void testToMqttConnectionInfo_withValidQos()
    {
        MqttConnectionSettings settings = new MqttConnectionSettings.Builder()
                .host(TEST_HOST)
                .port(TEST_PORT)
                .tlsEnabled(true)
                .deviceName(TEST_DEVICE_NAME)
                .mqttUsername(TEST_USERNAME)
                .mqttPassword(TEST_PASSWORD)
                .mqttTopicPrefix(TEST_TOPIC_PREFIX)
                .mqttQos(2)
                .cellularStreamEnabled(true)
                .wifiStreamEnabled(true)
                .bluetoothStreamEnabled(true)
                .gnssStreamEnabled(true)
                .deviceStatusStreamEnabled(true)
                .build();

        BrokerConnectionInfo connectionInfo = settings.toMqttConnectionInfo();

        assertNotNull("Connection info should not be null", connectionInfo);
        assertEquals("QoS 2 should be preserved in conversion",
                MqttQos.EXACTLY_ONCE, connectionInfo.getMqttQos());
    }

    @Test
    public void testWithoutDeviceName_preservesQos()
    {
        MqttConnectionSettings original = new MqttConnectionSettings.Builder()
                .host(TEST_HOST)
                .port(TEST_PORT)
                .deviceName(TEST_DEVICE_NAME)
                .mqttQos(2)
                .build();

        MqttConnectionSettings withoutName = original.withoutDeviceName();

        assertEquals("QoS should be preserved after removing device name",
                original.mqttQos(), withoutName.mqttQos());
    }

    @Test
    public void testBuildWithInvalidQos_usesDefault()
    {
        // Test negative value
        MqttConnectionSettings settingsNegative = new MqttConnectionSettings.Builder()
                .host(TEST_HOST)
                .port(TEST_PORT)
                .mqttQos(-1)
                .build();
        assertEquals("Negative QoS should default to 1", Integer.valueOf(1), settingsNegative.mqttQos());

        // Test value > 2
        MqttConnectionSettings settings3 = new MqttConnectionSettings.Builder()
                .host(TEST_HOST)
                .port(TEST_PORT)
                .mqttQos(3)
                .build();
        assertEquals("QoS 3 should default to 1", Integer.valueOf(1), settings3.mqttQos());

        // Test large invalid value
        MqttConnectionSettings settings100 = new MqttConnectionSettings.Builder()
                .host(TEST_HOST)
                .port(TEST_PORT)
                .mqttQos(100)
                .build();
        assertEquals("QoS 100 should default to 1", Integer.valueOf(1), settings100.mqttQos());
    }

    @Test
    public void testToMqttConnectionInfo_withInvalidQos()
    {
        // Create settings with invalid QoS using record constructor directly (simulates bad JSON deserialization)
        MqttConnectionSettings settings = new MqttConnectionSettings(
                TEST_HOST, TEST_PORT, true, TEST_DEVICE_NAME,
                TEST_USERNAME, TEST_PASSWORD, TEST_TOPIC_PREFIX,
                5, // Invalid QoS value
                true, true, true, true, true
        );

        BrokerConnectionInfo connectionInfo = settings.toMqttConnectionInfo();

        assertNotNull("Connection info should not be null", connectionInfo);
        assertEquals("Invalid QoS should convert to default QoS 1",
                MqttQos.AT_LEAST_ONCE, connectionInfo.getMqttQos());
    }
}
