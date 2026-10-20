package com.craxiom.networksurvey.survey.mdm

import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the one-release fallback that reads the deprecated MDM keys.
 */
class LegacyMdmMapperTest {

    private val broker = mapOf(
        MqttConstants.PROPERTY_MQTT_CONNECTION_HOST to "broker.example.com",
        MqttConstants.PROPERTY_MQTT_CLIENT_ID to "device-1",
    )

    private fun map(values: Map<String, Any?>, types: Boolean = false, destinations: Boolean = false, autoStart: Boolean = false) =
        LegacyMdmMapper.map(RestrictionsSnapshot(values), types, destinations, autoStart)

    @Test
    fun `nothing present maps to nothing`() {
        assertTrue(map(emptyMap()).isEmpty)
    }

    @Test
    fun `only present keys are mapped, so an absent auto start key never turns logging on`() {
        // A broker-only fleet must not suddenly start file logging of cellular.
        val result = map(broker)
        assertEquals(ManagedState.USER, result.autoStart)
        assertFalse(Destination.LOG_FILES in result.seedSetup!!.destinations)
    }

    @Test
    fun `a present true auto start key turns auto start on and seeds log files with that type`() {
        val result = map(mapOf(NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING to true))
        assertEquals(ManagedState.ON, result.autoStart)
        assertEquals(setOf(RecordType.WIFI), result.seedSetup!!.recordTypes)
        assertEquals(setOf(Destination.LOG_FILES), result.seedSetup!!.destinations)
    }

    @Test
    fun `a present false auto start key maps to user`() {
        val result = map(mapOf(NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING to false))
        assertEquals(ManagedState.USER, result.autoStart)
        assertNull(result.seedSetup)
    }

    @Test
    fun `an MDM broker turns MQTT on with the old default stream types`() {
        val result = map(broker)
        assertEquals(mapOf(Destination.MQTT to ManagedState.ON), result.destinationLocks)
        assertEquals(setOf(RecordType.CELLULAR, RecordType.PHONE_STATE, RecordType.WIFI), result.seedSetup!!.recordTypes)
    }

    @Test
    fun `stream flags seed types but never lock them`() {
        val result = map(
            broker + mapOf(
                NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED to true,
                NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED to false,
            )
        )
        assertEquals(setOf(RecordType.CELLULAR, RecordType.PHONE_STATE, RecordType.GNSS), result.seedSetup!!.recordTypes)
        val policy = SurveyPolicy.evaluate(
            RestrictionsSnapshot(broker + mapOf(NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED to true)), false,
        )
        assertTrue(policy.typeLocks.values.all { it == ManagedState.USER })
    }

    @Test
    fun `mqtt start on boot needs a broker to auto start`() {
        assertEquals(ManagedState.USER, map(mapOf(NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT to true)).autoStart)
        assertEquals(ManagedState.ON, map(broker + mapOf(NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT to true)).autoStart)
    }

    @Test
    fun `each dimension falls back independently`() {
        val values = broker + mapOf(NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING to true)
        val destinationsManaged = map(values, destinations = true)
        assertTrue(destinationsManaged.destinationLocks.isEmpty())
        assertEquals(ManagedState.ON, destinationsManaged.autoStart)

        val autoStartManaged = map(values, autoStart = true)
        assertEquals(ManagedState.USER, autoStartManaged.autoStart)
        assertEquals(mapOf(Destination.MQTT to ManagedState.ON), autoStartManaged.destinationLocks)
    }

    @Test
    fun `a v2 destination key wins over the legacy broker mapping`() {
        val policy = SurveyPolicy.evaluate(RestrictionsSnapshot(broker + mapOf(SurveyMdmKeys.DESTINATION_GRPC to "on")), false)
        assertEquals(ManagedState.USER, policy.lockFor(Destination.MQTT))
        assertEquals(ManagedState.ON, policy.lockFor(Destination.GRPC))
    }

    @Test
    fun `legacy keys make the phone count as having an organization setup`() {
        assertTrue(SurveyPolicy.hasOrganizationSetup(RestrictionsSnapshot(broker)))
    }
}
