package com.craxiom.networksurvey.ui.survey

import android.content.Context
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.engine.SurveyTypeResolver
import com.craxiom.networksurvey.survey.mdm.ManagedState
import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import com.craxiom.networksurvey.survey.status.DestinationStatus
import com.craxiom.networksurvey.ui.survey.model.RecordingUiMapper

/**
 * What a streaming destination's page (MQTT, gRPC) needs from the survey: how the destination is
 * doing in a running survey, and what happens to the survey when the destination is set up for the
 * first time, the way the prototype's "Save and turn on" works.
 *
 * @param policy Reads the organization policy in force; replaced in tests.
 */
class DestinationSurveyLink(
    private val context: Context,
    private val destination: Destination,
    private val setupStore: SurveySetupStore = SurveySetupStore(context),
    private val policy: () -> SurveyPolicyState = { SurveyTypeResolver.currentPolicy(context) },
) {

    /** @return The destination's status in a running survey, or null when it is not in it. */
    fun liveStatus(runtime: SurveyRuntimeState?): DestinationStatus? =
        runtime?.let { RecordingUiMapper.liveStatus(it, destination, System.currentTimeMillis()) }

    /** @return True when the organization keeps this destination off on this phone. */
    fun isTurnedOffByOrganization(): Boolean {
        val policy = policy()
        return policy.lockFor(destination) == ManagedState.OFF || !policy.isDestinationAvailable(destination)
    }

    /** @return True when the survey setup (with the organization's locks applied) sends here. */
    fun isInSetup(): Boolean = destination in policy().effectiveSetup(setupStore.getCurrentSetup()).destinations

    /**
     * Called after the destination is set up for the first time. Decided on the effective setup
     * (the user's setup with the organization's locks applied), the one Start and the survey
     * service use. Call off the main thread.
     *
     * - Already in the setup: a recording survey starts it now ([FirstSetupOutcome.STARTING]); an
     *   idle one needs nothing ([FirstSetupOutcome.NONE]).
     * - Not in the setup and idle: it joins the user's setup ([FirstSetupOutcome.TURNED_ON]).
     * - Not in the setup while recording: the running survey is left alone
     *   ([FirstSetupOutcome.TURN_ON_TO_STREAM]).
     * - Kept off by the organization: nothing ([FirstSetupOutcome.NONE]).
     */
    fun onFirstSetup(recording: Boolean): FirstSetupOutcome {
        val policy = policy()
        if (policy.lockFor(destination) == ManagedState.OFF || !policy.isDestinationAvailable(destination)) {
            return FirstSetupOutcome.NONE
        }
        val setup = setupStore.getCurrentSetup()
        if (destination in policy.effectiveSetup(setup).destinations) {
            return if (recording) FirstSetupOutcome.STARTING else FirstSetupOutcome.NONE
        }
        if (recording) return FirstSetupOutcome.TURN_ON_TO_STREAM
        setupStore.setCurrentSetup(SurveySetup.custom(setup.recordTypes, setup.destinations + destination))
        return FirstSetupOutcome.TURNED_ON
    }
}

/** What setting up a destination for the first time did to the survey. */
enum class FirstSetupOutcome {
    /** Nothing changed in the survey. */
    NONE,

    /** The destination was added to the setup for the next survey. */
    TURNED_ON,

    /** Saved while recording, and the running survey does not send here: turn it on to stream. */
    TURN_ON_TO_STREAM,

    /** The running survey already sends here, so the destination starts now. */
    STARTING,
}
