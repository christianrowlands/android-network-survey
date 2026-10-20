package com.craxiom.networksurvey.ui.survey

import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.ui.survey.model.SurveyEvent
import com.craxiom.networksurvey.ui.survey.model.SurveyUiStateMapper

/**
 * Notices when an organization configuration push changes a running survey, so the screen can say
 * "Your organization changed this survey: GNSS added, Community databases stopped."
 *
 * The service refreshes the policy first and reconciles the running survey a moment later, so a
 * policy change only arms the watcher; the next change to what is running produces the message.
 * A push that changes nothing running expires after [ARMED_FOR_MS], and a change the user makes
 * from this screen disarms it.
 */
class OrganizationChangeWatcher {

    private var previous: SurveyRuntimeState? = null
    private var armedWith: SurveyRuntimeState? = null
    private var armedAtMs = 0L

    fun onState(state: SurveyRuntimeState, nowMs: Long): SurveyEvent? {
        val last = previous
        previous = state
        if (last == null) return null

        if (last.policy.fingerprint != state.policy.fingerprint && last.isRecording && !state.policy.overrideOn) {
            armedWith = last
            armedAtMs = nowMs
        }
        val before = armedWith ?: return null
        if (nowMs - armedAtMs > ARMED_FOR_MS) {
            armedWith = null
            return null
        }
        if (!state.isRecording) {
            armedWith = null
            return SurveyEvent.OrganizationStopped
        }
        if (state.recordTypes == before.recordTypes && state.destinations.keys == before.destinations.keys) return null

        armedWith = null
        return SurveyEvent.OrganizationChanged(
            addedTypes = SurveyUiStateMapper.ordered(state.recordTypes - before.recordTypes),
            removedTypes = SurveyUiStateMapper.ordered(before.recordTypes - state.recordTypes),
            addedDestinations = (state.destinations.keys - before.destinations.keys).sorted(),
            removedDestinations = (before.destinations.keys - state.destinations.keys).sorted(),
        )
    }

    /** Called when the user changes the survey, so their own change is never credited to the organization. */
    fun disarm() {
        armedWith = null
    }

    private companion object {
        const val ARMED_FOR_MS = 30_000L
    }
}
