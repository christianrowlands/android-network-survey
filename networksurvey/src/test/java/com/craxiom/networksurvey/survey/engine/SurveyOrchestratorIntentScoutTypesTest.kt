package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.Destination.LOG_FILES
import com.craxiom.networksurvey.survey.model.Destination.MQTT
import com.craxiom.networksurvey.survey.model.RecordType.GNSS
import com.craxiom.networksurvey.survey.model.RecordType.WIFI
import com.craxiom.networksurvey.survey.model.RecordType.WIFI_CLIENTS
import com.craxiom.networksurvey.survey.model.RecordType.WIFI_RAW
import com.craxiom.networksurvey.survey.model.SurveySetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Another app can't start the record types only an NS Scout records. The Intent API's request loses
 * them with a reason and the rest of it starts; the person using the phone and the organization
 * still can, and a survey that already asked for them keeps them.
 */
class SurveyOrchestratorIntentScoutTypesTest {
    private val outputs = FakeSurveyOutputs()
    private var policy: SurveyPolicyState = SurveyPolicyState.UNMANAGED
    private val orchestrator = SurveyOrchestrator(outputs) { policy }

    private val scoutTypes = setOf(WIFI_CLIENTS, WIFI_RAW)
    private val refusedBoth = mapOf(
        WIFI_CLIENTS to SkipReason.NOT_ALLOWED_FROM_INTENT,
        WIFI_RAW to SkipReason.NOT_ALLOWED_FROM_INTENT,
    )

    private fun managed(values: Map<String, Any?>, overrideOn: Boolean = false) {
        policy = SurveyPolicy.evaluate(RestrictionsSnapshot(values), overrideOn)
    }

    private fun assertNoScoutFile() {
        assertFalse(outputs.calls.toString(), outputs.calls.any { it.startsWith("file wifi_clients") || it.startsWith("file wifi_raw") })
    }

    @Test
    fun `a start refuses the Scout types with a reason and starts the rest`() {
        val result = orchestrator.startSurvey(
            SurveySetup.custom(scoutTypes + WIFI, setOf(LOG_FILES, MQTT)), SurveyOrigin.INTENT,
        )

        assertTrue(result.recording)
        assertEquals(refusedBoth, result.recordTypeProblems)
        assertEquals(setOf(WIFI), outputs.fileTypes.toSet())
        assertEquals(setOf(WIFI), outputs.mqttTypes)
        assertNoScoutFile()
    }

    @Test
    fun `a merge from idle refuses them the same way`() {
        val result = orchestrator.mergeIntoRunningSurvey(
            SurveySetup.custom(scoutTypes + WIFI, setOf(LOG_FILES)), SurveyOrigin.INTENT,
        )

        assertEquals(refusedBoth, result.recordTypeProblems)
        assertEquals(setOf(WIFI), outputs.fileTypes.toSet())
    }

    @Test
    fun `a merge can't add them to a survey that didn't ask for them`() {
        orchestrator.startSurvey(SurveySetup.custom(setOf(WIFI), setOf(LOG_FILES)), SurveyOrigin.USER)

        val result = orchestrator.mergeIntoRunningSurvey(SurveySetup.custom(setOf(WIFI_RAW), setOf(LOG_FILES)), SurveyOrigin.INTENT)

        assertTrue(result.recording)
        assertEquals(mapOf(WIFI_RAW to SkipReason.NOT_ALLOWED_FROM_INTENT), result.recordTypeProblems)
        assertEquals(setOf(WIFI), outputs.fileTypes.toSet())
        assertNoScoutFile()
    }

    @Test
    fun `a merge keeps the ones the person's running survey asked for, and reports no refusal`() {
        val running = setOf(WIFI, WIFI_CLIENTS)
        orchestrator.startSurvey(SurveySetup.custom(running, setOf(LOG_FILES)), SurveyOrigin.USER)

        val result = orchestrator.mergeIntoRunningSurvey(SurveySetup.custom(setOf(WIFI_CLIENTS, GNSS), setOf(LOG_FILES)), SurveyOrigin.INTENT)

        assertTrue(result.recordTypeProblems.isEmpty())
        assertEquals(running + GNSS, outputs.fileTypes.toSet())
    }

    @Test
    fun `a retry doesn't bring back what was refused`() {
        orchestrator.startSurvey(SurveySetup.custom(scoutTypes + WIFI, setOf(LOG_FILES)), SurveyOrigin.INTENT)

        val retried = orchestrator.retrySkipped()!!

        assertEquals(setOf(WIFI), orchestrator.askedSetup!!.recordTypes)
        assertTrue(retried.recordTypeProblems.isEmpty())
        assertEquals(setOf(WIFI), outputs.fileTypes.toSet())
        assertNoScoutFile()
    }

    @Test
    fun `a request for only the Scout types starts nothing and says why`() {
        val result = orchestrator.startSurvey(SurveySetup.custom(scoutTypes, setOf(LOG_FILES)), SurveyOrigin.INTENT)

        assertFalse(result.recording)
        assertEquals(refusedBoth, result.recordTypeProblems)
        assertEquals(DestinationResult.Skipped(SkipReason.RECEIVES_NOTHING), result.destinations[LOG_FILES])
        assertEquals(0, outputs.rolls)
        assertNoScoutFile()
    }

    @Test
    fun `a request for only the Scout types is not a heartbeat only MQTT stream`() {
        outputs.mqttSendsWithoutRecordTypes = true

        val result = orchestrator.startSurvey(SurveySetup.custom(setOf(WIFI_CLIENTS), setOf(MQTT)), SurveyOrigin.INTENT)

        assertFalse(result.recording)
        assertNull(outputs.mqttTypes)
        assertEquals(mapOf(WIFI_CLIENTS to SkipReason.NOT_ALLOWED_FROM_INTENT), result.recordTypeProblems)
    }

    @Test
    fun `every other origin still starts them`() {
        for (origin in SurveyOrigin.entries - SurveyOrigin.INTENT) {
            val outputs = FakeSurveyOutputs()
            val result = SurveyOrchestrator(outputs) { SurveyPolicyState.UNMANAGED }
                .startSurvey(SurveySetup.custom(scoutTypes + WIFI, setOf(LOG_FILES)), origin)

            assertEquals(origin.name, scoutTypes + WIFI, outputs.fileTypes.toSet())
            assertTrue(origin.name, result.recordTypeProblems.isEmpty())
        }
    }

    @Test
    fun `the organization's on still applies, whether or not the request names the type`() {
        managed(mapOf(SurveyMdmKeys.SURVEY_WIFI_CLIENTS to SurveyMdmKeys.VALUE_ON))

        val named = orchestrator.startSurvey(SurveySetup.custom(setOf(WIFI, WIFI_CLIENTS), setOf(LOG_FILES)), SurveyOrigin.INTENT)
        assertTrue(named.recordTypeProblems.isEmpty())
        assertEquals(setOf(WIFI, WIFI_CLIENTS), outputs.fileTypes.toSet())

        orchestrator.stopSurvey(SurveyOrigin.USER)
        val unnamed = orchestrator.startSurvey(SurveySetup.custom(setOf(WIFI), setOf(LOG_FILES)), SurveyOrigin.INTENT)
        assertTrue(unnamed.recordTypeProblems.isEmpty())
        assertEquals(setOf(WIFI, WIFI_CLIENTS), outputs.fileTypes.toSet())
    }

    @Test
    fun `the organization's on for one type doesn't open the other`() {
        managed(mapOf(SurveyMdmKeys.SURVEY_WIFI_CLIENTS to SurveyMdmKeys.VALUE_ON))

        val result = orchestrator.startSurvey(SurveySetup.custom(scoutTypes + WIFI, setOf(LOG_FILES)), SurveyOrigin.INTENT)

        assertEquals(mapOf(WIFI_RAW to SkipReason.NOT_ALLOWED_FROM_INTENT), result.recordTypeProblems)
        assertEquals(setOf(WIFI, WIFI_CLIENTS), outputs.fileTypes.toSet())
    }

    @Test
    fun `the organization's user choice doesn't let another app start them`() {
        managed(mapOf(SurveyMdmKeys.SURVEY_WIFI_RAW to SurveyMdmKeys.VALUE_USER))

        val result = orchestrator.startSurvey(SurveySetup.custom(setOf(WIFI, WIFI_RAW), setOf(LOG_FILES)), SurveyOrigin.INTENT)

        assertEquals(mapOf(WIFI_RAW to SkipReason.NOT_ALLOWED_FROM_INTENT), result.recordTypeProblems)
        assertNoScoutFile()
    }

    @Test
    fun `with the override on the organization's on no longer applies, so another app can't start them`() {
        managed(mapOf(SurveyMdmKeys.SURVEY_WIFI_CLIENTS to SurveyMdmKeys.VALUE_ON), overrideOn = true)

        val result = orchestrator.startSurvey(SurveySetup.custom(setOf(WIFI, WIFI_CLIENTS), setOf(LOG_FILES)), SurveyOrigin.INTENT)

        assertEquals(mapOf(WIFI_CLIENTS to SkipReason.NOT_ALLOWED_FROM_INTENT), result.recordTypeProblems)
        assertNoScoutFile()
    }

    @Test
    fun `what the organization turned on still follows the Scout and Log files rules`() {
        managed(mapOf(SurveyMdmKeys.SURVEY_WIFI_RAW to SurveyMdmKeys.VALUE_ON))
        val noLogFiles = orchestrator.startSurvey(SurveySetup.custom(setOf(WIFI), setOf(MQTT)), SurveyOrigin.INTENT)
        assertEquals(mapOf(WIFI_RAW to SkipReason.NEEDS_LOG_FILES), noLogFiles.recordTypeProblems)
        orchestrator.stopSurvey(SurveyOrigin.USER)

        outputs.typeProblems[WIFI_RAW] = SkipReason.NEEDS_SCOUT
        val noScout = orchestrator.startSurvey(SurveySetup.custom(setOf(WIFI), setOf(LOG_FILES)), SurveyOrigin.INTENT)
        assertEquals(mapOf(WIFI_RAW to SkipReason.NEEDS_SCOUT), noScout.recordTypeProblems)
    }
}
