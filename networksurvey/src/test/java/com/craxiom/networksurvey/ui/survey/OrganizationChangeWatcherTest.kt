package com.craxiom.networksurvey.ui.survey

import com.craxiom.networksurvey.survey.engine.DestinationRuntime
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.survey.model.SurveyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "Your organization changed this survey": only a policy change followed by a change to what runs
 * produces it, and the user's own changes never do.
 */
class OrganizationChangeWatcherTest {

    private val watcher = OrganizationChangeWatcher()

    private val before = running(setOf(RecordType.CELLULAR), setOf(Destination.LOG_FILES, Destination.COMMUNITY), emptyMap())
    private val pushed = mapOf(SurveyMdmKeys.SURVEY_GNSS to SurveyMdmKeys.VALUE_ON)

    @Test
    fun `a push that changes the running survey is reported once`() {
        watcher.onState(before, 0L)
        assertNull(watcher.onState(before.copy(policy = policy(pushed)), 1L))

        val after = running(setOf(RecordType.CELLULAR, RecordType.GNSS), setOf(Destination.LOG_FILES), pushed)
        assertEquals(
            SurveyEvent.OrganizationChanged(
                addedTypes = listOf(RecordType.GNSS),
                removedTypes = emptyList(),
                addedDestinations = emptyList(),
                removedDestinations = listOf(Destination.COMMUNITY),
            ),
            watcher.onState(after, 2L),
        )
        assertNull(watcher.onState(after.copy(stalledMinutes = 1), 3L))
    }

    @Test
    fun `a push that leaves nothing running says the organization stopped it`() {
        watcher.onState(before, 0L)
        watcher.onState(before.copy(policy = policy(pushed)), 1L)
        assertEquals(SurveyEvent.OrganizationStopped, watcher.onState(SurveyRuntimeState(policy = policy(pushed)), 2L))
    }

    @Test
    fun `the user's own change after disarming is never credited to the organization`() {
        watcher.onState(before, 0L)
        watcher.onState(before.copy(policy = policy(pushed)), 1L)
        watcher.disarm()
        assertNull(watcher.onState(running(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES), pushed), 2L))
    }

    @Test
    fun `a push that changes nothing expires`() {
        watcher.onState(before, 0L)
        watcher.onState(before.copy(policy = policy(pushed)), 1L)
        assertNull(watcher.onState(running(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES), pushed), 60_000L))
    }

    private fun running(types: Set<RecordType>, destinations: Set<Destination>, restrictions: Map<String, Any?>) =
        SurveyRuntimeState(destinations = destinations.associateWith { DestinationRuntime(types) }, policy = policy(restrictions))

    private fun policy(restrictions: Map<String, Any?>) = SurveyPolicy.evaluate(RestrictionsSnapshot(restrictions), overrideOn = false)
}
