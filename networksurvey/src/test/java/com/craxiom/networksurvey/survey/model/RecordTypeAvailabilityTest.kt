package com.craxiom.networksurvey.survey.model

import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which record types are offered with and without a paired Scout, and under the organization's policy. */
class RecordTypeAvailabilityTest {

    @Test
    fun `without a Scout only the phone types are offered`() {
        val available = RecordTypeAvailability.available(SurveyPolicyState.UNMANAGED, scoutPaired = false)
        assertEquals(RecordType.entries.filter { !it.needsScout }, available)
        assertFalse(RecordType.WIFI_CLIENTS in available)
    }

    @Test
    fun `with a Scout Wi-Fi clients and Raw frames are offered after the phone types`() {
        val available = RecordTypeAvailability.available(SurveyPolicyState.UNMANAGED, scoutPaired = true)
        assertEquals(RecordType.entries, available)
        assertEquals(listOf(RecordType.WIFI_CLIENTS, RecordType.WIFI_RAW), available.takeLast(2))
    }

    @Test
    fun `Raw frames are a Scout-only type, hidden without a Scout and recorded only with it as the source`() {
        assertTrue(RecordType.WIFI_RAW.needsScout)
        assertFalse(RecordType.WIFI_RAW in RecordTypeAvailability.available(SurveyPolicyState.UNMANAGED, scoutPaired = false))
        assertFalse(RecordTypeAvailability.canRecord(RecordType.WIFI_RAW, scoutIsWifiSource = false))
        assertTrue(RecordTypeAvailability.canRecord(RecordType.WIFI_RAW, scoutIsWifiSource = true))
        assertEquals("wifi_raw", RecordType.WIFI_RAW.wireName)
        assertEquals(RecordType.WIFI_RAW, RecordType.fromWireName(" WIFI_RAW "))
    }

    @Test
    fun `the organization's off beats a paired Scout`() {
        val policy = SurveyPolicy.evaluate(RestrictionsSnapshot(mapOf(SurveyMdmKeys.SURVEY_WIFI_CLIENTS to SurveyMdmKeys.VALUE_OFF)), false)
        assertFalse(RecordTypeAvailability.isAvailable(RecordType.WIFI_CLIENTS, policy, scoutPaired = true))
        assertTrue(RecordTypeAvailability.isAvailable(RecordType.WIFI, policy, scoutPaired = true))
    }

    @Test
    fun `a Scout-only type records only while the Scout is the Wi-Fi source`() {
        assertTrue(RecordTypeAvailability.canRecord(RecordType.WIFI_CLIENTS, scoutIsWifiSource = true))
        assertFalse(RecordTypeAvailability.canRecord(RecordType.WIFI_CLIENTS, scoutIsWifiSource = false))
        assertTrue(RecordTypeAvailability.canRecord(RecordType.WIFI, scoutIsWifiSource = false))
    }

    @Test
    fun `another app can start the phone types but a Scout-only type only when the organization set it on`() {
        for (type in RecordType.entries) {
            assertEquals(type.wireName, !type.needsScout, RecordTypeAvailability.canIntentStart(type, SurveyPolicyState.UNMANAGED))
        }

        val policy = SurveyPolicy.evaluate(RestrictionsSnapshot(mapOf(SurveyMdmKeys.SURVEY_WIFI_CLIENTS to SurveyMdmKeys.VALUE_ON)), false)
        assertTrue(RecordTypeAvailability.canIntentStart(RecordType.WIFI_CLIENTS, policy))
        // Absent on a managed phone means off
        assertFalse(RecordTypeAvailability.canIntentStart(RecordType.WIFI_RAW, policy))
    }

    @Test
    fun `the wire name round trips for the Intent API and stored setups`() {
        assertEquals("wifi_clients", RecordType.WIFI_CLIENTS.wireName)
        assertEquals(RecordType.WIFI_CLIENTS, RecordType.fromWireName(" WIFI_CLIENTS "))
    }
}
