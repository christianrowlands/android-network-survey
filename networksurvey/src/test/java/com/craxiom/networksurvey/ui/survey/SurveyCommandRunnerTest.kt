package com.craxiom.networksurvey.ui.survey

import android.location.Location
import android.location.LocationListener
import com.craxiom.networksurvey.survey.engine.DestinationResult
import com.craxiom.networksurvey.survey.engine.SkipReason
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.engine.SurveyStartResult
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.survey.model.AttentionItemUi
import com.craxiom.networksurvey.ui.survey.model.SurveyEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException

/**
 * The Survey screen's command runner: commands run off the main thread, a command issued while the
 * screen is unbound (a permission dialog paused it) is sent on the next binding, and results turn
 * into the right snackbar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SurveyCommandRunnerTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val events = mutableListOf<SurveyEvent>()
    private val runner = SurveyCommandRunner(scope, dispatcher) { events += it }

    @Test
    fun `a command issued while unbound is sent on the next binding`() = scope.runTest {
        val gateway = FakeGateway()
        runner.run { SurveyCommandRunner.Outcome.Start(it.startWorkingSetup().get(), "NS-1") }
        advanceUntilIdle()
        assertEquals(0, gateway.starts)

        runner.onConnected(gateway)
        advanceUntilIdle()
        assertEquals(1, gateway.starts)
        assertEquals(listOf(SurveyEvent.Started("NS-1", 0)), events)
    }

    @Test
    fun `a follow-up runs once its command is done, also when the command waited for a binding`() = scope.runTest {
        val order = mutableListOf<String>()
        val gateway = FakeGateway()
        runner.run(afterwards = { order += "after, ${gateway.starts} started" }) {
            SurveyCommandRunner.Outcome.Start(it.startWorkingSetup().get(), "NS-1")
        }
        advanceUntilIdle()
        assertEquals(emptyList<String>(), order)

        runner.onConnected(gateway)
        advanceUntilIdle()
        assertEquals(listOf("after, 1 started"), order)
    }

    @Test
    fun `a service shutting down keeps the command for the next binding`() = scope.runTest {
        val dying = FakeGateway(rejectStarts = true)
        runner.onConnected(dying)
        runner.run { SurveyCommandRunner.Outcome.Start(it.startWorkingSetup().get(), null) }
        advanceUntilIdle()
        assertTrue(events.isEmpty())

        val fresh = FakeGateway()
        runner.onDisconnected()
        runner.onConnected(fresh)
        advanceUntilIdle()
        assertEquals(1, fresh.starts)
    }

    @Test
    fun `nothing starting reports why`() = scope.runTest {
        val gateway = FakeGateway(
            result = SurveyStartResult(
                destinations = mapOf(Destination.NS_ANALYTICS to DestinationResult.Skipped(SkipReason.NOT_REGISTERED)),
                recordTypeProblems = emptyMap(),
                recording = false,
            )
        )
        runner.onConnected(gateway)
        runner.run { SurveyCommandRunner.Outcome.Start(it.startWorkingSetup().get(), null) }
        advanceUntilIdle()
        assertEquals(
            listOf(SurveyEvent.CouldNotStart(listOf(AttentionItemUi.Dest(Destination.NS_ANALYTICS, SkipReason.NOT_REGISTERED)))),
            events,
        )
    }

    @Test
    fun `a partial start counts what was skipped`() = scope.runTest {
        val gateway = FakeGateway(
            result = SurveyStartResult(
                destinations = mapOf(
                    Destination.LOG_FILES to DestinationResult.Started(setOf(RecordType.CELLULAR)),
                    Destination.MQTT to DestinationResult.Skipped(SkipReason.NOT_CONFIGURED),
                ),
                recordTypeProblems = mapOf(RecordType.WIFI to SkipReason.RADIO_OFF),
                recording = true,
            )
        )
        runner.onConnected(gateway)
        runner.run { SurveyCommandRunner.Outcome.Start(it.startWorkingSetup().get(), "NS-2") }
        advanceUntilIdle()
        assertEquals(listOf(SurveyEvent.Started("NS-2", 2)), events)
    }

    @Test
    fun `stop reports the records of the session that ended`() = scope.runTest {
        runner.onConnected(FakeGateway())
        runner.run { SurveyCommandRunner.Outcome.Stop(3_118L) }
        advanceUntilIdle()
        assertEquals(listOf(SurveyEvent.Stopped(3_118L)), events)
    }

    private fun TestScope.runTest(block: suspend TestScope.() -> Unit) = kotlinx.coroutines.test.runTest(dispatcher) { block() }

    private class FakeGateway(
        private val result: SurveyStartResult = SurveyStartResult(emptyMap(), emptyMap(), recording = true),
        private val rejectStarts: Boolean = false,
    ) : SurveyServiceGateway {
        var starts = 0
        override val state: StateFlow<SurveyRuntimeState> = MutableStateFlow(SurveyRuntimeState())
        override fun counts(): Map<RecordType, Long> = emptyMap()
        override fun lastRecordTimeMs(): Long? = null
        override fun communityCounts(): Map<RecordType, Long> = emptyMap()
        override fun communityLastRecordTimeMs(): Long? = null
        override fun activeMissionId(): String? = null
        override fun startWorkingSetup(): Future<SurveyStartResult> {
            // The coordinator returns a failed future once the service has shut its executor down.
            if (rejectStarts) return CompletableFuture<SurveyStartResult>().apply {
                completeExceptionally(RejectedExecutionException("shutting down"))
            }
            starts++
            return CompletableFuture.completedFuture(result)
        }

        override fun applyWorkingSetup(): Future<SurveyStartResult> = CompletableFuture.completedFuture(result)
        override fun stop(): Future<SurveyStartResult> = CompletableFuture.completedFuture(result)
        override fun retrySkipped(): Future<SurveyStartResult?> = CompletableFuture.completedFuture(result)
        override fun isMqttBrokerConfigured(): Boolean = false
        override fun latestLocation(): Location? = null
        override fun registerLocationListener(listener: LocationListener) = Unit
        override fun unregisterLocationListener(listener: LocationListener) = Unit
    }
}
