package com.craxiom.networksurvey.survey.model

import com.craxiom.networksurvey.survey.model.Destination.COMMUNITY
import com.craxiom.networksurvey.survey.model.Destination.GRPC
import com.craxiom.networksurvey.survey.model.Destination.LOG_FILES
import com.craxiom.networksurvey.survey.model.Destination.MQTT
import com.craxiom.networksurvey.survey.model.Destination.NS_ANALYTICS
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
    fun `streaming destinations accept everything except CDR`() {
        val expected = RecordType.entries.toSet() - CDR
        for (destination in listOf(NS_ANALYTICS, MQTT, GRPC)) {
            assertEquals(destination.name, expected, DestinationCapabilities.accepts(destination))
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
