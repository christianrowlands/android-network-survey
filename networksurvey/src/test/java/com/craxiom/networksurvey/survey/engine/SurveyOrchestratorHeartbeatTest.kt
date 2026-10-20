package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.Destination.LOG_FILES
import com.craxiom.networksurvey.survey.model.Destination.MQTT
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.RecordType.BLUETOOTH
import com.craxiom.networksurvey.survey.model.RecordType.CELLULAR
import com.craxiom.networksurvey.survey.model.RecordType.PHONE_STATE
import com.craxiom.networksurvey.survey.model.SurveySetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the heartbeat only MQTT survey: MQTT with no record types, which the organization, the
 * upgrade migration and the Intent API can ask for. It runs only when MQTT has a device status
 * heartbeat or watchlist to send.
 */
class SurveyOrchestratorHeartbeatTest {

    private val outputs = FakeSurveyOutputs()
    private val orchestrator = SurveyOrchestrator(outputs) { SurveyPolicyState.UNMANAGED }
    private val heartbeatOnly = SurveySetup.custom(emptySet(), setOf(MQTT))

    @Test
    fun `MQTT with no record types starts with none when it has a heartbeat to send`() {
        outputs.mqttSendsWithoutRecordTypes = true
        val result = orchestrator.startSurvey(heartbeatOnly, SurveyOrigin.ORGANIZATION)

        assertTrue(result.recording)
        assertEquals(DestinationResult.Pending(emptySet()), result.destinations[MQTT])
        assertEquals(emptySet<RecordType>(), outputs.mqttTypes)
        // Nothing but the connection: no listener is registered for a record type, no file opens
        assertEquals(listOf("mqtt start []"), outputs.calls)
        assertTrue(outputs.fileTypes.isEmpty())
        assertEquals(1, outputs.rolls)
        assertEquals(heartbeatOnly.destinations, orchestrator.requestedSetup!!.destinations)
        assertTrue(orchestrator.requestedSetup!!.recordTypes.isEmpty())
    }

    @Test
    fun `MQTT with no record types receives nothing without device status or the watchlist`() {
        outputs.mqttSendsWithoutRecordTypes = false
        val result = orchestrator.startSurvey(heartbeatOnly, SurveyOrigin.ORGANIZATION)

        assertFalse(result.recording)
        assertEquals(DestinationResult.Skipped(SkipReason.RECEIVES_NOTHING), result.destinations[MQTT])
        assertNull(outputs.mqttTypes)
        assertTrue(outputs.calls.isEmpty())
        assertEquals(0, outputs.rolls)
    }

    @Test
    fun `a heartbeat only intent from idle starts MQTT with no record types`() {
        outputs.mqttSendsWithoutRecordTypes = true
        val result = orchestrator.mergeIntoRunningSurvey(heartbeatOnly, SurveyOrigin.INTENT)

        assertTrue(result.recording)
        assertEquals(emptySet<RecordType>(), outputs.mqttTypes)
    }

    @Test
    fun `log files with no record types still receive nothing next to a heartbeat only MQTT`() {
        outputs.mqttSendsWithoutRecordTypes = true
        val result = orchestrator.startSurvey(SurveySetup.custom(emptySet(), setOf(LOG_FILES, MQTT)), SurveyOrigin.INTENT)

        assertEquals(DestinationResult.Skipped(SkipReason.RECEIVES_NOTHING), result.destinations[LOG_FILES])
        assertTrue(outputs.calls.none { it.startsWith("file ") })
        assertEquals(emptySet<RecordType>(), outputs.mqttTypes)
    }

    @Test
    fun `a survey whose record types were all left out does not fall back to heartbeat only`() {
        outputs.mqttSendsWithoutRecordTypes = true
        outputs.typeProblems[BLUETOOTH] = SkipReason.RADIO_OFF
        val result = orchestrator.startSurvey(SurveySetup.custom(setOf(BLUETOOTH), setOf(MQTT)), SurveyOrigin.USER)

        assertFalse(result.recording)
        assertEquals(DestinationResult.Skipped(SkipReason.RECEIVES_NOTHING), result.destinations[MQTT])
        assertEquals(mapOf(BLUETOOTH to SkipReason.RADIO_OFF), result.recordTypeProblems)
    }

    @Test
    fun `a retry of a heartbeat only survey leaves it as it is`() {
        outputs.mqttSendsWithoutRecordTypes = true
        orchestrator.startSurvey(heartbeatOnly, SurveyOrigin.ORGANIZATION)
        val result = orchestrator.retrySkipped()!!

        assertTrue(result.recording)
        assertEquals(DestinationResult.Unchanged, result.destinations[MQTT])
        assertEquals(1, outputs.rolls)
    }

    @Test
    fun `adding record types to a heartbeat only stream updates it in place`() {
        outputs.mqttSendsWithoutRecordTypes = true
        orchestrator.startSurvey(heartbeatOnly, SurveyOrigin.ORGANIZATION)
        val result = orchestrator.applySetup(SurveySetup.custom(setOf(CELLULAR), setOf(MQTT)), SurveyOrigin.ORGANIZATION)

        assertEquals(DestinationResult.Updated(setOf(CELLULAR, PHONE_STATE)), result.destinations[MQTT])
        assertTrue(outputs.calls.none { it == "mqtt stop" })
        assertEquals(1, outputs.rolls)
    }
}
