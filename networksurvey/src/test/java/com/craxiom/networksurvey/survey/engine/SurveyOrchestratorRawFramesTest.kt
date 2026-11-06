package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Raw frames through the orchestrator: Log files is the only destination that takes them, a start
 * without Log files says so and never opens a file, and a start without a usable Scout leaves them
 * out with the Scout reason. (Another app can't start them: SurveyOrchestratorIntentScoutTypesTest.)
 */
class SurveyOrchestratorRawFramesTest {
    private val outputs = FakeSurveyOutputs()
    private val orchestrator = SurveyOrchestrator(outputs) { SurveyPolicyState.UNMANAGED }

    private fun start(types: Set<RecordType>, vararg destinations: Destination) =
        orchestrator.startSurvey(SurveySetup.custom(types, destinations.toSet()), SurveyOrigin.USER)

    @Test
    fun `with Log files raw frames are saved to files and nowhere else`() {
        val result = start(setOf(RecordType.WIFI, RecordType.WIFI_RAW), Destination.LOG_FILES, Destination.MQTT)

        assertTrue(RecordType.WIFI_RAW in outputs.fileTypes)
        assertEquals(setOf(RecordType.WIFI), outputs.mqttTypes)
        assertTrue(result.recordTypeProblems.isEmpty())
    }

    @Test
    fun `MQTT only reports raw frames as needing Log files and writes no file`() {
        val result = start(setOf(RecordType.WIFI, RecordType.WIFI_RAW), Destination.MQTT)

        assertEquals(mapOf(RecordType.WIFI_RAW to SkipReason.NEEDS_LOG_FILES), result.recordTypeProblems)
        assertTrue(outputs.fileTypes.isEmpty())
        assertFalse(outputs.calls.any { it.startsWith("file ") })
        assertEquals(setOf(RecordType.WIFI), outputs.mqttTypes)
    }

    @Test
    fun `raw frames alone with MQTT only start nothing and say why`() {
        val result = start(setOf(RecordType.WIFI_RAW), Destination.MQTT)

        assertFalse(result.recording)
        assertEquals(SkipReason.NEEDS_LOG_FILES, result.recordTypeProblems[RecordType.WIFI_RAW])
        assertTrue(outputs.fileTypes.isEmpty())
    }

    @Test
    fun `without a usable Scout raw frames are left out with the Scout reason`() {
        outputs.typeProblems[RecordType.WIFI_RAW] = SkipReason.NEEDS_SCOUT
        val result = start(setOf(RecordType.WIFI, RecordType.WIFI_RAW), Destination.LOG_FILES)

        assertEquals(mapOf(RecordType.WIFI_RAW to SkipReason.NEEDS_SCOUT), result.recordTypeProblems)
        assertEquals(setOf(RecordType.WIFI), outputs.fileTypes.toSet())
    }

    @Test
    fun `without a Scout and without Log files the Log files reason wins, as it must come first`() {
        outputs.typeProblems[RecordType.WIFI_RAW] = SkipReason.NEEDS_SCOUT
        val result = start(setOf(RecordType.WIFI, RecordType.WIFI_RAW), Destination.MQTT)

        assertEquals(mapOf(RecordType.WIFI_RAW to SkipReason.NEEDS_LOG_FILES), result.recordTypeProblems)
    }

    @Test
    fun `a survey without raw frames reports nothing about them`() {
        val result = start(setOf(RecordType.WIFI), Destination.MQTT)
        assertNull(result.recordTypeProblems[RecordType.WIFI_RAW])
    }

    @Test
    fun `CDR without Log files is still left out quietly`() {
        val result = start(setOf(RecordType.WIFI, RecordType.CDR), Destination.MQTT)
        assertNull(result.recordTypeProblems[RecordType.CDR])
    }

    @Test
    fun `turning Log files off mid survey closes the raw frames file`() {
        start(setOf(RecordType.WIFI, RecordType.WIFI_RAW), Destination.LOG_FILES, Destination.MQTT)
        orchestrator.applySetup(SurveySetup.custom(setOf(RecordType.WIFI, RecordType.WIFI_RAW), setOf(Destination.MQTT)), SurveyOrigin.USER)

        assertTrue(outputs.calls.contains("file wifi_raw false"))
        assertTrue(outputs.fileTypes.isEmpty())
    }

    @Test
    fun `the organization disallowing NS Scout mid survey drops only the Scout types, and they come back once allowed`() {
        val scoutSetup = setOf(RecordType.WIFI, RecordType.WIFI_CLIENTS, RecordType.WIFI_RAW)
        start(scoutSetup, Destination.LOG_FILES, Destination.MQTT)
        assertEquals(scoutSetup, outputs.fileTypes.toSet())

        // allow_ns_scout turns false: the probe says the Scout types need the Scout, and the
        // service retries what the survey asked for
        outputs.typeProblems[RecordType.WIFI_CLIENTS] = SkipReason.NEEDS_SCOUT
        outputs.typeProblems[RecordType.WIFI_RAW] = SkipReason.NEEDS_SCOUT
        val result = orchestrator.retrySkipped()!!

        assertTrue(result.recording)
        assertEquals(setOf(RecordType.WIFI), outputs.fileTypes.toSet())
        assertEquals(setOf(RecordType.WIFI), outputs.mqttTypes)
        assertTrue(outputs.calls.contains("file wifi_clients false"))
        assertTrue(outputs.calls.contains("file wifi_raw false"))
        assertEquals(
            mapOf(RecordType.WIFI_CLIENTS to SkipReason.NEEDS_SCOUT, RecordType.WIFI_RAW to SkipReason.NEEDS_SCOUT),
            result.recordTypeProblems,
        )
        assertEquals(1, outputs.rolls)

        // Allowed again: a later retry brings them back without a new Mission ID
        outputs.typeProblems.clear()
        orchestrator.retrySkipped()
        assertEquals(scoutSetup, outputs.fileTypes.toSet())
        assertEquals(1, outputs.rolls)
    }
}
