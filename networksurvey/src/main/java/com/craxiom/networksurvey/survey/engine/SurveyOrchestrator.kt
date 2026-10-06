package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.mdm.ManagedState
import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.DestinationCapabilities
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import timber.log.Timber

/**
 * The single implementation behind every way a survey starts, changes and stops: the Survey screen,
 * the Intent API, boot, app open and MDM changes. It turns a [SurveySetup] into calls on the
 * existing per-destination toggles through [SurveyOutputs].
 *
 * Rules it guarantees:
 * - **Validate before starting.** Destinations that cannot start (not set up, not connected,
 *   blocked or turned off by the organization, nothing to receive) are skipped with a reason
 *   before anything starts. If nothing can start, nothing starts and the Mission ID does not roll.
 * - **Every destination gets everything the survey records**, minus capability limits, except
 *   that an Intent API merge never widens what NS Analytics receives (paid quota).
 * - **Add before remove.** When a running survey changes, new destinations and types are started
 *   before old ones are stopped, so the session (and its Mission ID) never ends in between.
 * - **Stop stops everything**, then ends the session explicitly so an immediate Start rolls a
 *   fresh Mission ID even while gRPC is still finishing its disconnect.
 * - **A change that cannot run** leaves a running survey alone, except an organization change,
 *   which stops it (the organization no longer allows anything that was running).
 *
 * Not thread safe: the service calls it from one serial executor.
 *
 * @param outputs        The service operations.
 * @param policyProvider The current organization policy.
 */
class SurveyOrchestrator(
    private val outputs: SurveyOutputs,
    private val policyProvider: () -> SurveyPolicyState,
) {

    /** @return True if any destination is running or connecting. */
    fun isRecording(): Boolean = Destination.entries.any { outputs.isRunning(it) }

    /**
     * Starts [setup]. If a survey is already running, the running survey is changed to match
     * [setup] instead (the same as [applySetup]).
     */
    fun startSurvey(setup: SurveySetup, origin: SurveyOrigin): SurveyStartResult {
        Timber.i("Starting the survey from %s: types=%s, destinations=%s", origin, setup.recordTypes, setup.destinations)
        return reconcile(setup, nsAnalyticsCap = null, origin = origin)
    }

    /**
     * Changes the running survey to match [setup], adding before removing, without rolling the
     * Mission ID. If nothing is running this starts [setup].
     */
    fun applySetup(setup: SurveySetup, origin: SurveyOrigin): SurveyStartResult {
        Timber.i("Applying a setup from %s: types=%s, destinations=%s", origin, setup.recordTypes, setup.destinations)
        return reconcile(setup, nsAnalyticsCap = null, origin = origin)
    }

    /**
     * Adds [setup] to whatever is running, for an Intent API START. From idle this is a plain
     * start of [setup]. While running, the result is the union of the running survey and [setup],
     * except that NS Analytics never receives a record type it was not already receiving.
     */
    fun mergeIntoRunningSurvey(setup: SurveySetup, origin: SurveyOrigin): SurveyStartResult {
        if (!isRecording()) return startSurvey(setup, origin)

        val running = runningSetup()
        val merged = SurveySetup.custom(
            running.recordTypes + setup.recordTypes,
            running.destinations + setup.destinations,
        )
        val nsAnalyticsCap = if (outputs.isRunning(Destination.NS_ANALYTICS)) {
            outputs.activeTypes(Destination.NS_ANALYTICS)
        } else {
            setup.recordTypes
        }
        Timber.i("Merging an %s request into the running survey: %s", origin, merged)
        return reconcile(merged, nsAnalyticsCap, origin)
    }

    /** Stops every destination, then ends the session. */
    fun stopSurvey(origin: SurveyOrigin): SurveyStartResult {
        Timber.i("Stopping the survey from %s", origin)
        val results = linkedMapOf<Destination, DestinationResult>()
        for (destination in STOP_ORDER) {
            if (outputs.isRunning(destination)) {
                stopDestination(destination)
                results[destination] = DestinationResult.Stopped
            }
        }
        outputs.endMissionSession()
        return SurveyStartResult(results, emptyMap(), recording = false)
    }

    /**
     * @return The survey that is actually running, read from the service: the union of every
     * running destination's record types, and the running destinations.
     */
    fun runningSetup(): SurveySetup {
        val running = Destination.entries.filter { outputs.isRunning(it) }
        val types = running.flatMap { outputs.activeTypes(it) }.toSet()
        return SurveySetup.custom(types, running.toSet())
    }

    private fun reconcile(requested: SurveySetup, nsAnalyticsCap: Set<RecordType>?, origin: SurveyOrigin): SurveyStartResult {
        val policy = policyProvider()
        val effectiveSetup = policy.effectiveSetup(requested)

        val recordTypeProblems = linkedMapOf<RecordType, SkipReason>()
        val types = DestinationCapabilities.effectiveRecordTypes(
            effectiveSetup.recordTypes,
            outputs.autoIncludePhoneState(),
            policy.phoneStateForcedOff,
        ).filterTo(linkedSetOf()) { type ->
            val problem = outputs.probeRecordType(type)
            if (problem != null) recordTypeProblems[type] = problem
            problem == null
        }

        val results = linkedMapOf<Destination, DestinationResult>()
        val targets = linkedMapOf<Destination, Set<RecordType>>()
        val communityTargets = outputs.communityTargets()
        for (destination in Destination.entries) {
            val wanted = destination in requested.destinations || policy.lockFor(destination) == ManagedState.ON
            if (!wanted) continue

            val reason = when {
                destination in policy.blockedDestinations -> SkipReason.BLOCKED_BY_POLICY
                policy.lockFor(destination) == ManagedState.OFF -> SkipReason.TURNED_OFF_BY_ORGANIZATION
                else -> outputs.probeDestination(destination)
            }
            if (reason != null) {
                results[destination] = DestinationResult.Skipped(reason)
                continue
            }

            var destinationTypes = DestinationCapabilities.typesFor(destination, types, communityTargets)
            if (destination == Destination.NS_ANALYTICS && nsAnalyticsCap != null) {
                destinationTypes = destinationTypes intersect nsAnalyticsCap
            }
            if (destinationTypes.isEmpty()) {
                results[destination] = DestinationResult.Skipped(SkipReason.RECEIVES_NOTHING)
                continue
            }
            targets[destination] = destinationTypes
        }

        if (targets.isEmpty()) {
            if (isRecording() && origin == SurveyOrigin.ORGANIZATION) {
                // The organization's change leaves nothing this phone may run, so the survey stops.
                Timber.w("Nothing in the organization's setup can run, so the running survey is stopped")
                val stopped = stopSurvey(SurveyOrigin.ORGANIZATION)
                return SurveyStartResult(results + stopped.destinations, recordTypeProblems, recording = false)
            }
            if (isRecording()) {
                // A user change that cannot run (for example Wi-Fi only with the radio off) is
                // reported and the running survey is left as it is.
                Timber.w("Nothing in the requested setup can run, so the running survey is left unchanged")
                return SurveyStartResult(results, recordTypeProblems, recording = true)
            }
            Timber.w("Nothing in the requested setup can start, so the survey stays ready")
            return SurveyStartResult(results, recordTypeProblems, recording = false)
        }

        // Add and update first, so the session never ends between removing one destination and
        // starting another.
        for ((destination, destinationTypes) in targets) {
            results[destination] = startOrUpdate(destination, destinationTypes)
        }
        for (destination in STOP_ORDER) {
            if (destination !in targets && outputs.isRunning(destination)) {
                stopDestination(destination)
                if (results[destination] == null) results[destination] = DestinationResult.Stopped
            }
        }

        return SurveyStartResult(results, recordTypeProblems, recording = isRecording())
    }

    private fun startOrUpdate(destination: Destination, types: Set<RecordType>): DestinationResult {
        if (destination == Destination.LOG_FILES) return applyFileLogging(types)

        if (!outputs.isRunning(destination)) {
            val started = when (destination) {
                Destination.NS_ANALYTICS -> outputs.startNsAnalytics(types)
                Destination.MQTT -> outputs.startMqtt(types)
                Destination.GRPC -> outputs.startGrpc(types)
                Destination.COMMUNITY -> outputs.startCommunity()
                Destination.LOG_FILES -> error("Handled above")
            }
            return when {
                !started -> DestinationResult.Skipped(SkipReason.START_FAILED)
                destination == Destination.MQTT || destination == Destination.GRPC -> DestinationResult.Pending(types)
                else -> DestinationResult.Started(types)
            }
        }

        if (outputs.activeTypes(destination) == types) return DestinationResult.Unchanged
        when (destination) {
            Destination.NS_ANALYTICS -> outputs.updateNsAnalyticsTypes(types)
            Destination.MQTT -> outputs.updateMqttTypes(types)
            Destination.GRPC -> outputs.updateGrpcTypes(types)
            // The community databases record what their upload targets need, so there is nothing to update.
            Destination.COMMUNITY, Destination.LOG_FILES -> return DestinationResult.Unchanged
        }
        return DestinationResult.Updated(types)
    }

    /**
     * Brings file logging to exactly [types]. Cellular is started before Phone state so the
     * "Phone state with Cellular" rule can start Phone state itself; types are added before others
     * are removed, and Phone state is re-checked afterwards because stopping Cellular also stops a
     * Phone state it had started.
     */
    private fun applyFileLogging(types: Set<RecordType>): DestinationResult {
        val wasRunning = outputs.isRunning(Destination.LOG_FILES)
        val before = outputs.activeTypes(Destination.LOG_FILES)
        if (wasRunning && before == types) return DestinationResult.Unchanged

        for (type in FILE_ORDER) {
            if (type in types && type !in outputs.activeTypes(Destination.LOG_FILES)) {
                if (!outputs.setFileLoggingEnabled(type, true)) {
                    Timber.w("File logging of %s could not be started", type)
                }
            }
        }
        for (type in FILE_ORDER.reversed()) {
            if (type !in types && type in outputs.activeTypes(Destination.LOG_FILES)) {
                outputs.setFileLoggingEnabled(type, false)
            }
        }
        for (type in FILE_ORDER) {
            if (type in types && type !in outputs.activeTypes(Destination.LOG_FILES)) {
                outputs.setFileLoggingEnabled(type, true)
            }
        }

        val after = outputs.activeTypes(Destination.LOG_FILES)
        return when {
            after.isEmpty() -> DestinationResult.Skipped(SkipReason.START_FAILED)
            wasRunning -> DestinationResult.Updated(after)
            else -> DestinationResult.Started(after)
        }
    }

    private fun stopDestination(destination: Destination) {
        when (destination) {
            Destination.LOG_FILES -> {
                for (type in FILE_ORDER.reversed()) {
                    if (type in outputs.activeTypes(Destination.LOG_FILES)) {
                        outputs.setFileLoggingEnabled(type, false)
                    }
                }
            }

            Destination.NS_ANALYTICS -> outputs.stopNsAnalytics()
            Destination.MQTT -> outputs.stopMqtt()
            Destination.GRPC -> outputs.stopGrpc()
            Destination.COMMUNITY -> outputs.stopCommunity()
        }
    }

    private companion object {
        /** Cellular first, so it can start Phone state itself under the "Phone state with Cellular" rule. */
        val FILE_ORDER = listOf(
            RecordType.CELLULAR,
            RecordType.PHONE_STATE,
            RecordType.WIFI,
            RecordType.BLUETOOTH,
            RecordType.GNSS,
            RecordType.CDR,
        )

        /** Mission relevant destinations last, so the community databases never hold the session alone. */
        val STOP_ORDER = listOf(
            Destination.COMMUNITY,
            Destination.GRPC,
            Destination.MQTT,
            Destination.NS_ANALYTICS,
            Destination.LOG_FILES,
        )
    }
}
