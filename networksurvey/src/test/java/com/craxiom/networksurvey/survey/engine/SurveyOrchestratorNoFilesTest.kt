package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pins the no-files rule: a survey whose destinations don't include Log files never turns a file
 * logger on, whatever the other destinations, record types, problems and the way it is started,
 * merged, retried or changed.
 */
class SurveyOrchestratorNoFilesTest {

    /** Every destination combination without Log files, including none at all. */
    private val destinationSets: List<Set<Destination>> = run {
        val others = Destination.entries - Destination.LOG_FILES
        (0 until (1 shl others.size)).map { mask ->
            others.filterIndexedTo(mutableSetOf()) { index, _ -> mask and (1 shl index) != 0 }
        }
    }

    /** All record types, each one alone, and none (a heartbeat only MQTT). */
    private val typeSets: List<Set<RecordType>> =
        listOf(RecordType.entries.toSet(), emptySet<RecordType>()) + RecordType.entries.map { setOf(it) }

    /** Unmanaged, and a managed phone that keeps MQTT on (organization shapes the survey). */
    private val policies: List<SurveyPolicyState> = listOf(
        SurveyPolicyState.UNMANAGED,
        SurveyPolicy.evaluate(RestrictionsSnapshot(mapOf(SurveyMdmKeys.DESTINATION_MQTT to "on")), overrideOn = false),
    )

    private fun setups(): List<SurveySetup> =
        destinationSets.flatMap { destinations -> typeSets.map { SurveySetup.custom(it, destinations) } }

    /**
     * Runs [scenario] against a fresh orchestrator for every policy and heartbeat setting, with a
     * fake that fails the test the moment a file logger is turned on.
     */
    private fun forEveryEnvironment(scenario: (SurveyOrchestrator, FakeSurveyOutputs) -> Unit) {
        for (policy in policies) {
            for (heartbeat in listOf(false, true)) {
                val outputs = FileWatchingOutputs()
                outputs.mqttSendsWithoutRecordTypes = heartbeat
                scenario(SurveyOrchestrator(outputs) { policy }, outputs)
                assertTrue("Log files ran: ${outputs.fileTypes}", outputs.fileTypes.isEmpty())
            }
        }
    }

    @Test
    fun `a start never turns file logging on`() {
        for (setup in setups()) {
            forEveryEnvironment { orchestrator, _ -> orchestrator.startSurvey(setup, SurveyOrigin.USER) }
        }
    }

    @Test
    fun `an intent merge never turns file logging on, from idle or into a running survey`() {
        val all = setups()
        for (running in all) {
            for (merged in listOf(all.first(), all.last(), running)) {
                forEveryEnvironment { orchestrator, _ ->
                    orchestrator.mergeIntoRunningSurvey(running, SurveyOrigin.INTENT)
                    orchestrator.mergeIntoRunningSurvey(merged, SurveyOrigin.INTENT)
                }
            }
        }
    }

    @Test
    fun `a retry after a problem was fixed never turns file logging on`() {
        for (setup in setups()) {
            forEveryEnvironment { orchestrator, outputs ->
                outputs.typeProblems[RecordType.BLUETOOTH] = SkipReason.RADIO_OFF
                outputs.typeProblems[RecordType.CDR] = SkipReason.PERMISSION_MISSING
                outputs.destinationProblems[Destination.GRPC] = SkipReason.NOT_CONFIGURED
                orchestrator.startSurvey(setup, SurveyOrigin.USER)
                outputs.typeProblems.clear()
                outputs.destinationProblems.clear()
                orchestrator.retrySkipped()
            }
        }
    }

    @Test
    fun `applying a change never turns file logging on`() {
        val all = setups()
        for (from in all) {
            for (to in listOf(all.first(), all.last(), all[all.size / 2])) {
                forEveryEnvironment { orchestrator, _ ->
                    orchestrator.startSurvey(from, SurveyOrigin.USER)
                    orchestrator.applySetup(to, SurveyOrigin.USER)
                    orchestrator.applySetup(to, SurveyOrigin.ORGANIZATION)
                }
            }
        }
    }

    /** A [FakeSurveyOutputs] that fails the test on any `setFileLoggingEnabled(type, true)`. */
    private class FileWatchingOutputs : FakeSurveyOutputs() {
        override fun setFileLoggingEnabled(type: RecordType, enabled: Boolean): Boolean {
            if (enabled) fail("File logging of $type was turned on for a survey without Log files")
            return super.setFileLoggingEnabled(type, enabled)
        }
    }
}
