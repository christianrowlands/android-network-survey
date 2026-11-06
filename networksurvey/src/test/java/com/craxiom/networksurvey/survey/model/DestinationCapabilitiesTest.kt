package com.craxiom.networksurvey.survey.model

import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.Destination.COMMUNITY
import com.craxiom.networksurvey.survey.model.Destination.GRPC
import com.craxiom.networksurvey.survey.model.Destination.LOG_FILES
import com.craxiom.networksurvey.survey.model.Destination.MQTT
import com.craxiom.networksurvey.survey.model.Destination.NS_ANALYTICS
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.RecordType.CDR
import com.craxiom.networksurvey.survey.model.RecordType.CELLULAR
import com.craxiom.networksurvey.survey.model.RecordType.GNSS
import com.craxiom.networksurvey.survey.model.RecordType.PHONE_STATE
import com.craxiom.networksurvey.survey.model.RecordType.WIFI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the record type x destination capability matrix.
 */
class DestinationCapabilitiesTest {

    private val bothTargets = setOf(CELLULAR, WIFI)

    @Test
    fun `log files accept every record type including CDR`() {
        assertEquals(RecordType.entries.toSet(), DestinationCapabilities.accepts(LOG_FILES))
    }

    @Test
    fun `MQTT and gRPC accept everything except CDR and Raw frames`() {
        val expected = RecordType.entries.toSet() - CDR - RecordType.WIFI_RAW
        for (destination in listOf(MQTT, GRPC)) {
            assertEquals(destination.name, expected, DestinationCapabilities.accepts(destination))
        }
    }

    @Test
    fun `NS Analytics accepts neither CDR, Wi-Fi clients nor Raw frames`() {
        assertEquals(
            RecordType.entries.toSet() - CDR - RecordType.WIFI_CLIENTS - RecordType.WIFI_RAW,
            DestinationCapabilities.accepts(NS_ANALYTICS),
        )
    }

    @Test
    fun `Raw frames go to log files only, and are the only type reported without them`() {
        val takers = Destination.entries.filter { RecordType.WIFI_RAW in DestinationCapabilities.accepts(it) }
        assertEquals(listOf(LOG_FILES), takers)
        assertEquals(setOf(RecordType.WIFI_RAW), DestinationCapabilities.LOG_FILES_ONLY_TYPES)
        for (destination in listOf(NS_ANALYTICS, MQTT, GRPC, COMMUNITY)) {
            assertTrue(destination.name, DestinationCapabilities.typesFor(destination, setOf(RecordType.WIFI_RAW), bothTargets).isEmpty())
        }
    }

    @Test
    fun `Wi-Fi clients go to log files, MQTT and gRPC only`() {
        val takers = Destination.entries.filter { RecordType.WIFI_CLIENTS in DestinationCapabilities.accepts(it) }
        assertEquals(listOf(LOG_FILES, MQTT, GRPC), takers)
    }

    @Test
    fun `a phone-only survey sends exactly what it sent before Wi-Fi clients existed`() {
        val phoneTypes = setOf(CELLULAR, PHONE_STATE, WIFI, RecordType.BLUETOOTH, GNSS, CDR)
        assertEquals(phoneTypes, DestinationCapabilities.accepts(LOG_FILES) - RecordType.WIFI_CLIENTS - RecordType.WIFI_RAW)
        for (destination in listOf(NS_ANALYTICS, MQTT, GRPC)) {
            assertEquals(destination.name, phoneTypes - CDR, DestinationCapabilities.typesFor(destination, phoneTypes, emptySet()))
        }
    }

    @Test
    fun `community accepts only cellular and wifi`() {
        assertEquals(setOf(CELLULAR, WIFI), DestinationCapabilities.accepts(COMMUNITY))
    }

    @Test
    fun `community is narrowed to the selected upload targets`() {
        // OpenCelliD only: cellular only.
        assertEquals(setOf(CELLULAR), DestinationCapabilities.typesFor(COMMUNITY, setOf(CELLULAR, WIFI, GNSS), setOf(CELLULAR)))
        // No targets: nothing.
        assertTrue(DestinationCapabilities.typesFor(COMMUNITY, setOf(CELLULAR, WIFI), emptySet()).isEmpty())
    }

    @Test
    fun `CDR only reaches log files`() {
        val types = setOf(CDR)
        assertEquals(setOf(CDR), DestinationCapabilities.typesFor(LOG_FILES, types, bothTargets))
        for (destination in listOf(NS_ANALYTICS, MQTT, GRPC, COMMUNITY)) {
            assertTrue(destination.name, DestinationCapabilities.typesFor(destination, types, bothTargets).isEmpty())
        }
    }

    @Test
    fun `phone state follows cellular when the auto include setting is on`() {
        assertEquals(setOf(CELLULAR, PHONE_STATE), DestinationCapabilities.effectiveRecordTypes(setOf(CELLULAR), true))
        assertEquals(setOf(CELLULAR), DestinationCapabilities.effectiveRecordTypes(setOf(CELLULAR), false))
        assertEquals(setOf(WIFI), DestinationCapabilities.effectiveRecordTypes(setOf(WIFI), true))
    }

    @Test
    fun `an organization turning phone state off beats the auto include rule`() {
        assertEquals(
            setOf(CELLULAR),
            DestinationCapabilities.effectiveRecordTypes(setOf(CELLULAR, PHONE_STATE), true, phoneStateForcedOff = true),
        )
    }

    @Test
    fun `wire names round trip and are case insensitive`() {
        for (type in RecordType.entries) assertEquals(type, RecordType.fromWireName(type.wireName.uppercase()))
        for (destination in Destination.entries) assertEquals(destination, Destination.fromWireName(" ${destination.wireName} "))
        assertEquals(null, RecordType.fromWireName("lte"))
    }

    @Test
    fun `community is the only destination that does not roll the Mission ID`() {
        assertEquals(listOf(COMMUNITY), Destination.entries.filterNot { it.isMissionRelevant })
    }
}
