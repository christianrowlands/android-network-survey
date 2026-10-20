package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.Destination.COMMUNITY
import com.craxiom.networksurvey.survey.model.Destination.GRPC
import com.craxiom.networksurvey.survey.model.Destination.LOG_FILES
import com.craxiom.networksurvey.survey.model.Destination.MQTT
import com.craxiom.networksurvey.survey.model.Destination.NS_ANALYTICS
import com.craxiom.networksurvey.survey.model.RecordType.BLUETOOTH
import com.craxiom.networksurvey.survey.model.RecordType.CDR
import com.craxiom.networksurvey.survey.model.RecordType.CELLULAR
import com.craxiom.networksurvey.survey.model.RecordType.GNSS
import com.craxiom.networksurvey.survey.model.RecordType.PHONE_STATE
import com.craxiom.networksurvey.survey.model.RecordType.WIFI
import com.craxiom.networksurvey.survey.model.SurveySetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the survey orchestrator against a fake service that drives a real Mission ID tracker.
 */
class SurveyOrchestratorTest {

    private val outputs = FakeSurveyOutputs()
    private var policy: SurveyPolicyState = SurveyPolicyState.UNMANAGED
    private val orchestrator = SurveyOrchestrator(outputs) { policy }

    private fun setup(vararg destinations: Destination, types: Set<com.craxiom.networksurvey.survey.model.RecordType> = setOf(CELLULAR, WIFI)) =
        SurveySetup.custom(types, destinations.toSet())

    private fun managed(values: Map<String, Any?>) {
        policy = SurveyPolicy.evaluate(RestrictionsSnapshot(values), overrideOn = false)
    }

    @Test
    fun `a five destination start rolls the Mission ID exactly once`() {
        val result = orchestrator.startSurvey(setup(LOG_FILES, NS_ANALYTICS, MQTT, GRPC, COMMUNITY), SurveyOrigin.USER)
        outputs.completeGrpcConnection()

        assertTrue(result.recording)
        assertEquals(1, outputs.rolls)
        assertEquals(DestinationResult.Pending(setOf(CELLULAR, PHONE_STATE, WIFI)), result.destinations[GRPC])
        assertEquals(DestinationResult.Started(setOf(CELLULAR, WIFI)), result.destinations[COMMUNITY])
    }

    @Test
    fun `a start whose only destination gives up at once keeps what it asked for`() {
        outputs.mqttGivesUpAtOnce = true

        val result = orchestrator.startSurvey(setup(MQTT), SurveyOrigin.INTENT)

        // Ready then says why it stopped, as for a stream that gave up later
        assertFalse(result.recording)
        assertEquals(setup(MQTT).destinations, orchestrator.requestedSetup?.destinations)
        assertNull(orchestrator.retrySkipped())
    }

    @Test
    fun `a start where nothing can run forgets the one that gave up before it`() {
        outputs.mqttGivesUpAtOnce = true
        orchestrator.startSurvey(setup(MQTT), SurveyOrigin.INTENT)
        outputs.destinationProblems[MQTT] = SkipReason.NOT_CONFIGURED

        orchestrator.startSurvey(setup(MQTT), SurveyOrigin.USER)

        // Ready no longer says the earlier survey stopped: the latest start is what it reports on
        assertNull(orchestrator.requestedSetup)
    }

    @Test
    fun `a community only start never rolls the Mission ID`() {
        val result = orchestrator.startSurvey(setup(COMMUNITY), SurveyOrigin.USER)
        assertTrue(result.recording)
        assertEquals(0, outputs.rolls)
        assertNull(outputs.tracker.rolledMissionId())
    }

    @Test
    fun `every destination gets everything the survey records, minus its limits`() {
        orchestrator.startSurvey(setup(LOG_FILES, MQTT, NS_ANALYTICS, types = setOf(CELLULAR, BLUETOOTH, CDR)), SurveyOrigin.USER)
        assertEquals(setOf(CELLULAR, PHONE_STATE, BLUETOOTH, CDR), outputs.fileTypes)
        assertEquals(setOf(CELLULAR, PHONE_STATE, BLUETOOTH), outputs.mqttTypes)
        assertEquals(setOf(CELLULAR, PHONE_STATE, BLUETOOTH), outputs.nsAnalyticsTypes)
    }

    @Test
    fun `a partial failure starts the rest and reports why each one was skipped`() {
        outputs.destinationProblems[MQTT] = SkipReason.NOT_CONFIGURED
        outputs.typeProblems[WIFI] = SkipReason.RADIO_OFF
        val result = orchestrator.startSurvey(setup(LOG_FILES, MQTT), SurveyOrigin.USER)

        assertTrue(result.recording)
        assertEquals(mapOf(MQTT to SkipReason.NOT_CONFIGURED), result.skipped)
        assertEquals(mapOf(WIFI to SkipReason.RADIO_OFF), result.recordTypeProblems)
        assertEquals(setOf(CELLULAR, PHONE_STATE), outputs.fileTypes)
        assertNull(outputs.mqttTypes)
    }

    @Test
    fun `when nothing can start, nothing starts and the Mission ID does not roll`() {
        outputs.destinationProblems[NS_ANALYTICS] = SkipReason.NOT_REGISTERED
        outputs.destinationProblems[MQTT] = SkipReason.NOT_CONFIGURED
        val result = orchestrator.startSurvey(setup(NS_ANALYTICS, MQTT), SurveyOrigin.USER)

        assertFalse(result.recording)
        assertEquals(0, outputs.rolls)
        assertTrue(outputs.calls.isEmpty())
    }

    @Test
    fun `a destination that would receive nothing is skipped but the survey still starts`() {
        val result = orchestrator.startSurvey(setup(LOG_FILES, COMMUNITY, types = setOf(GNSS)), SurveyOrigin.USER)
        assertTrue(result.recording)
        assertEquals(DestinationResult.Skipped(SkipReason.RECEIVES_NOTHING), result.destinations[COMMUNITY])
    }

    @Test
    fun `a problem with a type no destination takes is not reported`() {
        outputs.typeProblems[BLUETOOTH] = SkipReason.RADIO_OFF
        val result = orchestrator.startSurvey(setup(COMMUNITY, types = setOf(CELLULAR, BLUETOOTH)), SurveyOrigin.USER)

        assertTrue(result.recording)
        assertEquals(emptyMap<Any, Any>(), result.recordTypeProblems)
        assertEquals(setOf(CELLULAR), outputs.communityTypes)
    }

    @Test
    fun `a problem with a type only a skipped destination takes is not reported`() {
        outputs.typeProblems[BLUETOOTH] = SkipReason.RADIO_OFF
        outputs.destinationProblems[NS_ANALYTICS] = SkipReason.NOT_REGISTERED
        val result = orchestrator.startSurvey(setup(NS_ANALYTICS, COMMUNITY, types = setOf(CELLULAR, BLUETOOTH)), SurveyOrigin.USER)

        assertEquals(emptyMap<Any, Any>(), result.recordTypeProblems)
        assertEquals(mapOf(NS_ANALYTICS to SkipReason.NOT_REGISTERED), result.skipped)
    }

    @Test
    fun `CDR without its permissions is left out of log files and reported`() {
        outputs.typeProblems[CDR] = SkipReason.PERMISSION_MISSING
        val result = orchestrator.startSurvey(setup(LOG_FILES, types = setOf(CDR)), SurveyOrigin.USER)

        assertFalse(result.recording)
        assertEquals(mapOf(CDR to SkipReason.PERMISSION_MISSING), result.recordTypeProblems)
        assertTrue(outputs.fileTypes.isEmpty())
        assertTrue(outputs.calls.isEmpty())
    }

    @Test
    fun `CDR without its permissions reports nothing when only MQTT is on`() {
        outputs.typeProblems[CDR] = SkipReason.PERMISSION_MISSING
        val result = orchestrator.startSurvey(setup(MQTT, types = setOf(CDR)), SurveyOrigin.USER)

        assertEquals(emptyMap<Any, Any>(), result.recordTypeProblems)
        assertEquals(DestinationResult.Skipped(SkipReason.RECEIVES_NOTHING), result.destinations[MQTT])
    }

    @Test
    fun `stop stops everything and ends the session`() {
        orchestrator.startSurvey(setup(LOG_FILES, MQTT, NS_ANALYTICS, COMMUNITY), SurveyOrigin.USER)
        val result = orchestrator.stopSurvey(SurveyOrigin.USER)

        assertFalse(orchestrator.isRecording())
        assertEquals(setOf(LOG_FILES, MQTT, NS_ANALYTICS, COMMUNITY), result.destinations.keys)
        assertFalse(outputs.tracker.isMissionSessionActive())
    }

    @Test
    fun `stop then an immediate start rolls a fresh Mission ID even while gRPC finishes`() {
        outputs.asyncGrpcStop = true
        orchestrator.startSurvey(setup(LOG_FILES, GRPC), SurveyOrigin.USER)
        outputs.completeGrpcConnection()
        orchestrator.stopSurvey(SurveyOrigin.USER)
        orchestrator.startSurvey(setup(LOG_FILES), SurveyOrigin.USER)
        assertEquals(2, outputs.rolls)

        // The old gRPC disconnect finally lands; the new mission keeps running.
        outputs.completeGrpcDisconnect()
        assertTrue(outputs.tracker.isMissionSessionActive())
        assertEquals("mission-2", outputs.tracker.rolledMissionId())
    }

    @Test
    fun `applying a change adds and removes without rolling the Mission ID`() {
        orchestrator.startSurvey(setup(LOG_FILES), SurveyOrigin.USER)
        orchestrator.applySetup(setup(MQTT, types = setOf(CELLULAR, GNSS)), SurveyOrigin.USER)

        assertEquals(1, outputs.rolls)
        assertTrue(outputs.fileTypes.isEmpty())
        assertEquals(setOf(CELLULAR, PHONE_STATE, GNSS), outputs.mqttTypes)
        // MQTT was started before log files stopped, so the session never ended in between.
        val mqttStart = outputs.calls.indexOfFirst { it.startsWith("mqtt start") }
        assertTrue(mqttStart in 0 until outputs.calls.indexOf("file cellular false"))
    }

    @Test
    fun `swapping log files for a connecting gRPC stream keeps the Mission ID`() {
        orchestrator.startSurvey(setup(LOG_FILES), SurveyOrigin.USER)
        orchestrator.applySetup(setup(GRPC), SurveyOrigin.USER)
        outputs.completeGrpcConnection()
        assertEquals(1, outputs.rolls)
    }

    @Test
    fun `adding a record type to a running stream updates it in place`() {
        orchestrator.startSurvey(setup(MQTT), SurveyOrigin.USER)
        val result = orchestrator.applySetup(setup(MQTT, types = setOf(CELLULAR, WIFI, BLUETOOTH)), SurveyOrigin.USER)
        assertEquals(DestinationResult.Updated(setOf(CELLULAR, PHONE_STATE, WIFI, BLUETOOTH)), result.destinations[MQTT])
        assertTrue(outputs.calls.none { it == "mqtt stop" })
    }

    @Test
    fun `an intent merge never widens what NS Analytics receives`() {
        orchestrator.startSurvey(setup(NS_ANALYTICS, types = setOf(CELLULAR)), SurveyOrigin.USER)
        orchestrator.mergeIntoRunningSurvey(setup(LOG_FILES, types = setOf(GNSS)), SurveyOrigin.INTENT)

        assertEquals(setOf(CELLULAR, PHONE_STATE), outputs.nsAnalyticsTypes)
        assertEquals(setOf(CELLULAR, PHONE_STATE, GNSS), outputs.fileTypes)
    }

    @Test
    fun `a retry starts what the survey left out once it is fixed, keeping the rest`() {
        outputs.typeProblems[BLUETOOTH] = SkipReason.RADIO_OFF
        outputs.destinationProblems[MQTT] = SkipReason.NOT_CONFIGURED
        orchestrator.mergeIntoRunningSurvey(setup(LOG_FILES, MQTT, types = setOf(CELLULAR, BLUETOOTH)), SurveyOrigin.INTENT)
        assertEquals(setOf(CELLULAR, PHONE_STATE), outputs.fileTypes)
        val rolls = outputs.rolls

        outputs.typeProblems.clear()
        outputs.destinationProblems.clear()
        val result = orchestrator.retrySkipped()!!

        assertTrue(result.recording)
        assertEquals(setOf(CELLULAR, PHONE_STATE, BLUETOOTH), outputs.fileTypes)
        assertEquals(setOf(CELLULAR, PHONE_STATE, BLUETOOTH), outputs.mqttTypes)
        assertEquals(rolls, outputs.rolls)
    }

    @Test
    fun `an intent merge keeps what the survey left out earlier, so a retry still starts it`() {
        outputs.typeProblems[BLUETOOTH] = SkipReason.RADIO_OFF
        orchestrator.startSurvey(setup(LOG_FILES, types = setOf(CELLULAR, BLUETOOTH)), SurveyOrigin.USER)
        orchestrator.mergeIntoRunningSurvey(setup(LOG_FILES, types = setOf(GNSS)), SurveyOrigin.INTENT)
        assertEquals(setOf(CELLULAR, BLUETOOTH, GNSS), orchestrator.askedSetup!!.recordTypes)

        outputs.typeProblems.clear()
        orchestrator.retrySkipped()

        assertEquals(setOf(CELLULAR, PHONE_STATE, BLUETOOTH, GNSS), outputs.fileTypes)
    }

    @Test
    fun `a retry while idle does nothing, and stop forgets what was asked`() {
        assertNull(orchestrator.retrySkipped())
        orchestrator.startSurvey(setup(LOG_FILES), SurveyOrigin.USER)
        orchestrator.stopSurvey(SurveyOrigin.USER)
        assertNull(orchestrator.askedSetup)
        assertNull(orchestrator.retrySkipped())
        assertTrue(outputs.fileTypes.isEmpty())
    }

    @Test
    fun `an intent from idle defines the survey and only uses NS Analytics when named`() {
        orchestrator.mergeIntoRunningSurvey(setup(LOG_FILES, types = setOf(WIFI)), SurveyOrigin.INTENT)
        assertNull(outputs.nsAnalyticsTypes)
        assertEquals(setOf(WIFI), outputs.fileTypes)
    }

    @Test
    fun `organization locks are applied and turned off items are skipped`() {
        managed(
            mapOf(
                SurveyMdmKeys.SURVEY_GNSS to "on",
                SurveyMdmKeys.SURVEY_WIFI to "off",
                SurveyMdmKeys.DESTINATION_MQTT to "on",
                SurveyMdmKeys.DESTINATION_LOG_FILES to "off",
            )
        )
        val result = orchestrator.startSurvey(setup(LOG_FILES), SurveyOrigin.USER)

        assertEquals(DestinationResult.Skipped(SkipReason.TURNED_OFF_BY_ORGANIZATION), result.destinations[LOG_FILES])
        assertEquals(setOf(CELLULAR, PHONE_STATE, GNSS), outputs.mqttTypes)
    }

    @Test
    fun `policy blocked destinations are skipped`() {
        managed(mapOf("some_key" to true))
        val result = orchestrator.startSurvey(setup(LOG_FILES, COMMUNITY), SurveyOrigin.USER)
        assertEquals(DestinationResult.Skipped(SkipReason.BLOCKED_BY_POLICY), result.destinations[COMMUNITY])
        assertFalse(outputs.communityRunning)
    }

    @Test
    fun `an organization turning phone state off beats the auto include rule`() {
        managed(mapOf(SurveyMdmKeys.SURVEY_PHONE_STATE to "off"))
        orchestrator.startSurvey(setup(MQTT, types = setOf(CELLULAR)), SurveyOrigin.USER)
        assertEquals(setOf(CELLULAR), outputs.mqttTypes)
    }

    @Test
    fun `a change that leaves nothing valid stops the running survey`() {
        orchestrator.startSurvey(setup(LOG_FILES), SurveyOrigin.USER)
        managed(mapOf(SurveyMdmKeys.DESTINATION_LOG_FILES to "off"))
        val result = orchestrator.applySetup(setup(LOG_FILES), SurveyOrigin.ORGANIZATION)
        assertFalse(result.recording)
        assertFalse(orchestrator.isRecording())
    }

    @Test
    fun `a user change that cannot run leaves the running survey alone`() {
        orchestrator.startSurvey(setup(LOG_FILES), SurveyOrigin.USER)
        outputs.typeProblems[WIFI] = SkipReason.RADIO_OFF
        val result = orchestrator.applySetup(setup(MQTT, types = setOf(WIFI)), SurveyOrigin.USER)

        assertTrue(result.recording)
        assertTrue(orchestrator.isRecording())
        assertEquals(setOf(CELLULAR, PHONE_STATE, WIFI), outputs.fileTypes)
    }

    @Test
    fun `a file type that fails to start is reported and the rest keep running`() {
        outputs.failingFileTypes += WIFI
        val result = orchestrator.startSurvey(setup(LOG_FILES), SurveyOrigin.USER)
        assertTrue(result.recording)
        assertEquals(DestinationResult.Started(setOf(CELLULAR, PHONE_STATE)), result.destinations[LOG_FILES])
        assertEquals("The failure is kept for the screen", setOf(WIFI), orchestrator.fileLoggingFailures)

        orchestrator.applySetup(setup(MQTT), SurveyOrigin.USER)
        assertEquals("Turning Log files off forgets it", emptySet<Any>(), orchestrator.fileLoggingFailures)
    }

    @Test
    fun `the requested setup keeps types no destination takes, and stop forgets it`() {
        orchestrator.startSurvey(setup(NS_ANALYTICS, COMMUNITY, types = setOf(CELLULAR, CDR)), SurveyOrigin.USER)

        val requested = orchestrator.requestedSetup!!
        assertEquals(setOf(CELLULAR, PHONE_STATE, CDR), requested.recordTypes)
        assertEquals(setOf(NS_ANALYTICS, COMMUNITY), requested.destinations)

        orchestrator.stopSurvey(SurveyOrigin.USER)
        assertNull(orchestrator.requestedSetup)
    }

    @Test
    fun `a start that runs nothing requests nothing`() {
        outputs.destinationProblems[MQTT] = SkipReason.NOT_CONFIGURED
        orchestrator.startSurvey(setup(MQTT), SurveyOrigin.USER)
        assertNull(orchestrator.requestedSetup)
    }

    @Test
    fun `community records only the selected types its targets take, and follows changes`() {
        orchestrator.startSurvey(setup(COMMUNITY, types = setOf(CELLULAR, GNSS)), SurveyOrigin.USER)
        assertEquals(setOf(CELLULAR), outputs.communityTypes)

        val result = orchestrator.applySetup(setup(COMMUNITY, types = setOf(CELLULAR, WIFI)), SurveyOrigin.USER)
        assertEquals(DestinationResult.Updated(setOf(CELLULAR, WIFI)), result.destinations[COMMUNITY])
        assertEquals(setOf(CELLULAR, WIFI), outputs.communityTypes)
    }

    @Test
    fun `the running setup is read from the service`() {
        orchestrator.startSurvey(setup(LOG_FILES, MQTT, types = setOf(WIFI)), SurveyOrigin.USER)
        val running = orchestrator.runningSetup()
        assertEquals(setOf(LOG_FILES, MQTT), running.destinations)
        assertEquals(setOf(WIFI), running.recordTypes)
    }
}
