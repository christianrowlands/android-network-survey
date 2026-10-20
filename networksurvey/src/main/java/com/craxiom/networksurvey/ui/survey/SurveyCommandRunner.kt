package com.craxiom.networksurvey.ui.survey

import com.craxiom.networksurvey.survey.engine.DestinationResult
import com.craxiom.networksurvey.survey.engine.SurveyStartResult
import com.craxiom.networksurvey.ui.survey.model.AttentionItemUi
import com.craxiom.networksurvey.ui.survey.model.SurveyEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException

/**
 * Runs the Survey screen's start, change and stop commands off the main thread and turns their
 * results into snackbar events.
 *
 * The screen unbinds from the service whenever it pauses, and a system permission or battery
 * dialog pauses it. A command issued in that window is kept and sent as soon as the service is
 * bound again, instead of being lost or sent to a service that is shutting down. A command can
 * carry a follow-up that runs on the screen's thread once the command is done (Go to Settings on the
 * battery question opens the settings only after the survey has started).
 */
class SurveyCommandRunner(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val emit: (SurveyEvent) -> Unit,
) {
    /** What a command produced, for the snackbar. */
    sealed interface Outcome {
        data class Start(val result: SurveyStartResult, val missionId: String?) : Outcome
        data class Stop(val records: Long) : Outcome
        data object Applied : Outcome
        data object Silent : Outcome
    }

    /** A command and what runs after it. */
    private class Queued(val command: (SurveyServiceGateway) -> Outcome, val afterwards: () -> Unit)

    private var gateway: SurveyServiceGateway? = null
    private var pending: Queued? = null

    /**
     * Runs [command] now if the service is bound, otherwise as soon as it is, then [afterwards] on
     * the scope's own thread.
     */
    fun run(afterwards: () -> Unit = {}, command: (SurveyServiceGateway) -> Outcome) {
        val queued = Queued(command, afterwards)
        val bound = gateway
        if (bound == null) {
            pending = queued
            return
        }
        execute(bound, queued)
    }

    /** Called with each new binding; sends a command that was waiting for one. */
    fun onConnected(newGateway: SurveyServiceGateway) {
        gateway = newGateway
        pending?.let {
            pending = null
            execute(newGateway, it)
        }
    }

    /** Called when the screen unbinds, so later commands wait for the next binding. */
    fun onDisconnected() {
        gateway = null
    }

    private fun execute(target: SurveyServiceGateway, queued: Queued) {
        scope.launch(ioDispatcher) {
            try {
                eventFor(queued.command(target))?.let(emit)
            } catch (e: ExecutionException) {
                if (e.cause is RejectedExecutionException) return@launch keepForNextBinding(queued, e)
                Timber.e(e, "A survey command failed")
            } catch (e: RejectedExecutionException) {
                return@launch keepForNextBinding(queued, e)
            }
            // Done (or failed for good): the follow-up runs either way
            scope.launch { queued.afterwards() }
        }
    }

    /** The service shut down between binding and the command; send it on the next binding. */
    private fun keepForNextBinding(queued: Queued, cause: Exception) {
        Timber.w(cause, "The survey service was shutting down; sending the command on the next binding")
        pending = queued
    }

    private fun eventFor(outcome: Outcome): SurveyEvent? = when (outcome) {
        is Outcome.Start -> startEvent(outcome.result, outcome.missionId)
        is Outcome.Stop -> SurveyEvent.Stopped(outcome.records)
        Outcome.Applied -> SurveyEvent.Applied
        Outcome.Silent -> null
    }

    private fun startEvent(result: SurveyStartResult, missionId: String?): SurveyEvent {
        val problems = result.recordTypeProblems.map { (type, reason) -> AttentionItemUi.Type(type, reason) } +
            result.destinations.mapNotNull { (destination, outcome) ->
                (outcome as? DestinationResult.Skipped)?.let { AttentionItemUi.Dest(destination, it.reason) }
            }
        return if (result.recording) {
            SurveyEvent.Started(missionId = missionId, skipped = problems.size)
        } else {
            SurveyEvent.CouldNotStart(problems)
        }
    }
}
