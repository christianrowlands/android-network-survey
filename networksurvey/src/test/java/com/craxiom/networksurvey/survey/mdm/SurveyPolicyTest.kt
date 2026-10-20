package com.craxiom.networksurvey.survey.mdm

import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests how the MDM survey policy is read: the three-way choice keys, the data policy gates, the
 * override and the configuration fingerprint.
 */
class SurveyPolicyTest {

    private fun policy(values: Map<String, Any?>, overrideOn: Boolean = false) =
        SurveyPolicy.evaluate(RestrictionsSnapshot(values), overrideOn)

    @Test
    fun `an unmanaged phone has no locks and allows every destination`() {
        val policy = policy(emptyMap())
        assertFalse(policy.isManaged)
        assertFalse(policy.hasSetupLocks)
        assertTrue(policy.blockedDestinations.isEmpty())
        assertFalse(policy.hasOrganizationSetup)
        assertNull(policy.managedSeedSetup(SurveySetup.empty()))
    }

    @Test
    fun `choice values lock items on and off`() {
        val policy = policy(
            mapOf(
                SurveyMdmKeys.SURVEY_CELLULAR to "on",
                SurveyMdmKeys.SURVEY_GNSS to "off",
                SurveyMdmKeys.DESTINATION_MQTT to "on",
                SurveyMdmKeys.DESTINATION_GRPC to "off",
            )
        )
        assertEquals(ManagedState.ON, policy.lockFor(RecordType.CELLULAR))
        assertEquals(ManagedState.OFF, policy.lockFor(RecordType.GNSS))
        assertEquals(ManagedState.USER, policy.lockFor(RecordType.WIFI))
        assertEquals(ManagedState.ON, policy.lockFor(Destination.MQTT))
        assertEquals(ManagedState.OFF, policy.lockFor(Destination.GRPC))
        assertTrue(policy.hasSetupLocks)
    }

    @Test
    fun `a full form push of every key as user locks nothing`() {
        val everyKeyUser = SurveyMdmKeys.ALL_SETUP_KEYS.associateWith { SurveyMdmKeys.VALUE_USER }
        val policy = policy(everyKeyUser)
        assertTrue(policy.isManaged)
        assertFalse(policy.hasSetupLocks)
        assertEquals(ManagedState.USER, policy.autoStart)
        assertFalse(policy.hasOrganizationSetup)
    }

    @Test
    fun `malformed values mean user, booleans map to on and off`() {
        assertEquals(ManagedState.USER, ManagedState.parse("sometimes"))
        assertEquals(ManagedState.USER, ManagedState.parse(7))
        assertEquals(ManagedState.USER, ManagedState.parse(null))
        assertEquals(ManagedState.ON, ManagedState.parse(true))
        assertEquals(ManagedState.OFF, ManagedState.parse(false))
        assertEquals(ManagedState.ON, ManagedState.parse(" ON "))
    }

    @Test
    fun `effective setup adds locked on items and removes locked off items`() {
        val policy = policy(
            mapOf(
                SurveyMdmKeys.SURVEY_CELLULAR to "on",
                SurveyMdmKeys.SURVEY_BLUETOOTH to "off",
                SurveyMdmKeys.DESTINATION_MQTT to "on",
                SurveyMdmKeys.DESTINATION_LOG_FILES to "off",
            )
        )
        val user = SurveySetup.custom(setOf(RecordType.WIFI, RecordType.BLUETOOTH), setOf(Destination.LOG_FILES, Destination.GRPC))
        val effective = policy.effectiveSetup(user)
        assertEquals(setOf(RecordType.WIFI, RecordType.CELLULAR), effective.recordTypes)
        assertEquals(setOf(Destination.GRPC, Destination.MQTT), effective.destinations)
    }

    @Test
    fun `a broker only bundle is managed and denies community uploads`() {
        val policy = policy(
            mapOf(
                MqttConstants.PROPERTY_MQTT_CONNECTION_HOST to "broker.example.com",
                MqttConstants.PROPERTY_MQTT_CLIENT_ID to "device-1",
            )
        )
        assertTrue(policy.isManaged)
        assertTrue(Destination.COMMUNITY in policy.blockedDestinations)
        assertFalse(SurveyPolicy.isExternalDataUploadAllowed(RestrictionsSnapshot(mapOf("anything" to 1))))
    }

    @Test
    fun `community is allowed on a managed phone only when the organization says so`() {
        val allowed = policy(mapOf(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_EXTERNAL_DATA_UPLOAD to true))
        assertFalse(Destination.COMMUNITY in allowed.blockedDestinations)
    }

    @Test
    fun `policy gates beat destination keys and survive the override`() {
        val values = mapOf(
            SurveyMdmKeys.DESTINATION_COMMUNITY to "on",
            NetworkSurveyConstants.MDM_PROPERTY_ALLOW_EXTERNAL_DATA_UPLOAD to false,
            NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_ANALYTICS to false,
        )
        for (overrideOn in listOf(false, true)) {
            val policy = policy(values, overrideOn)
            val effective = policy.effectiveSetup(SurveySetup.custom(setOf(RecordType.CELLULAR), setOf(Destination.NS_ANALYTICS)))
            assertEquals("override=$overrideOn", emptySet<Destination>(), effective.destinations)
            assertFalse(policy.isDestinationAvailable(Destination.COMMUNITY))
            assertFalse(policy.isDestinationAvailable(Destination.NS_ANALYTICS))
        }
    }

    @Test
    fun `NS Analytics is allowed by default on a managed phone`() {
        val policy = policy(mapOf(SurveyMdmKeys.SURVEY_CELLULAR to "on"))
        assertFalse(Destination.NS_ANALYTICS in policy.blockedDestinations)
    }

    @Test
    fun `the override lifts setup locks but keeps the organization setup visible`() {
        val values = mapOf(SurveyMdmKeys.SURVEY_CELLULAR to "on", SurveyMdmKeys.AUTO_START_SURVEY to "on")
        val policy = policy(values, overrideOn = true)
        assertFalse(policy.hasSetupLocks)
        assertEquals(ManagedState.USER, policy.autoStart)
        assertTrue(policy.hasOrganizationSetup)
        assertNull(policy.seedSetup)
    }

    @Test
    fun `auto start follows the organization and otherwise the user`() {
        assertTrue(policy(mapOf(SurveyMdmKeys.AUTO_START_SURVEY to "on")).isAutoStartEnabled(false))
        assertFalse(policy(mapOf(SurveyMdmKeys.AUTO_START_SURVEY to "off")).isAutoStartEnabled(true))
        assertTrue(policy(emptyMap()).isAutoStartEnabled(true))
        assertFalse(policy(emptyMap()).isAutoStartEnabled(false))
    }

    @Test
    fun `the CDR key is honored`() {
        val policy = policy(mapOf(SurveyMdmKeys.SURVEY_CDR to "off"))
        assertFalse(policy.isRecordTypeAvailable(RecordType.CDR))
    }

    @Test
    fun `seeding resets only the dimensions the organization manages`() {
        val user = SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES))

        val destinationsLocked = policy(mapOf(SurveyMdmKeys.DESTINATION_MQTT to "on")).managedSeedSetup(user)!!
        assertEquals(setOf(RecordType.WIFI), destinationsLocked.recordTypes)
        assertEquals(emptySet<Destination>(), destinationsLocked.destinations)

        val typesLocked = policy(mapOf(SurveyMdmKeys.SURVEY_GNSS to "on")).managedSeedSetup(user)!!
        assertEquals(emptySet<RecordType>(), typesLocked.recordTypes)
        assertEquals(setOf(Destination.LOG_FILES), typesLocked.destinations)

        assertNull(policy(emptyMap()).managedSeedSetup(user))
    }

    @Test
    fun `a heartbeat only MDM broker seeds MQTT with no record types instead of the user's`() {
        val user = SurveySetup.custom(setOf(RecordType.WIFI, RecordType.GNSS), setOf(Destination.LOG_FILES))
        val policy = policy(
            mapOf(
                MqttConstants.PROPERTY_MQTT_CONNECTION_HOST to "broker.example.com",
                MqttConstants.PROPERTY_MQTT_CLIENT_ID to "device-1",
                NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED to false,
                NetworkSurveyConstants.PROPERTY_MQTT_PHONE_STATE_STREAM_ENABLED to false,
                NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED to false,
            )
        )
        val seeded = policy.managedSeedSetup(user)!!
        assertEquals(emptySet<RecordType>(), seeded.recordTypes)
        assertEquals(setOf(Destination.MQTT), seeded.destinations)
        assertTrue(policy.shapesSetup)
    }

    @Test
    fun `a seed without record types of its own keeps the user's`() {
        val user = SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES))
        val destinationsOnly = SurveyPolicyState(
            isManaged = true, overrideOn = false, typeLocks = emptyMap(), destinationLocks = emptyMap(),
            blockedDestinations = emptySet(), autoStart = ManagedState.USER,
            seedSetup = OrganizationSeed(emptySet(), setOf(Destination.MQTT)), hasOrganizationSetup = true, fingerprint = "",
        )
        assertEquals(setOf(RecordType.WIFI), destinationsOnly.managedSeedSetup(user)!!.recordTypes)
    }

    @Test
    fun `an unmanaged or overridden policy does not shape the setup`() {
        assertFalse(policy(emptyMap()).shapesSetup)
        assertFalse(policy(mapOf(SurveyMdmKeys.SURVEY_CELLULAR to "on"), overrideOn = true).shapesSetup)
        assertTrue(policy(mapOf(SurveyMdmKeys.SURVEY_CELLULAR to "on")).shapesSetup)
    }

    @Test
    fun `the fingerprint ignores order and changes with values`() {
        val a = RestrictionsSnapshot(linkedMapOf("a" to 1, "b" to "x"))
        val b = RestrictionsSnapshot(linkedMapOf("b" to "x", "a" to 1))
        val c = RestrictionsSnapshot(linkedMapOf("a" to 2, "b" to "x"))
        assertEquals(a.fingerprint(), b.fingerprint())
        assertNotEquals(a.fingerprint(), c.fingerprint())
    }
}
