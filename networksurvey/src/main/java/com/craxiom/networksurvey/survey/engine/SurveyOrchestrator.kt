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
 *   that an Intent API merge never widens what NS Analytics receives (paid quota). A survey with
 *   no record types at all (from the organization, the upgrade migration or the Intent API) still
 *   runs MQTT when it has a device status heartbeat or watchlist to send.
 * - **Only report what could matter.** A record type problem (a radio off, a missing permission)
 *   is reported only when some destination that may start would take that type.
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

    /**
     * What the latest start, change or merge asked for: its record types (before any was left out
     * for a radio or permission) and the destinations it tried to run. Null after a stop or a start
     * where nothing could run, even after an earlier start that gave up; kept after a start whose
     * destinations all gave up at once. Read from other threads to publish the survey state.
     */
    @Volatile
    var requestedSetup: SurveySetup? = null
        private set

    /**
     * What the running survey asked for, whole: every record type and destination, including the
     * ones left out for a radio, a permission or a missing setting. [retrySkipped] runs it again
     * once something may have been fixed, whoever started the survey. Null when idle.
     */
    @Volatile
    var askedSetup: SurveySetup? = null
        private set

    /** The NS Analytics cap that came with [askedSetup] (an Intent API merge), so a retry keeps it. */
    private var askedNsAnalyticsCap: Set<RecordType>? = null

    /** Record types Log files was asked for but couldn't start (their file couldn't be created). */
    @Volatile
    var fileLoggingFailures: Set<RecordType> = emptySet()
        private set

    /** @return True if any destination is running or connecting. */
    fun isRecording(): Boolean = Destination.entries.any { outputs.isRunning(it) }

    /** @return True if [destination] is running or connecting. */
    fun isRunning(destination: Destination): Boolean = outputs.isRunning(destination)

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
     * start of [setup]. While running, the result is the union of what the running survey asked for
     * and [setup], except that NS Analytics never receives a record type it was not already
     * receiving.
     */
    fun mergeIntoRunningSurvey(setup: SurveySetup, origin: SurveyOrigin): SurveyStartResult {
        if (!isRecording()) return startSurvey(setup, origin)

        // What the survey asked for, so an item it left out earlier is not forgotten by the merge
        val base = askedSetup ?: runningSetup()
        val merged = SurveySetup.custom(
            base.recordTypes + setup.recordTypes,
            base.destinations + setup.destinations,
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
        requestedSetup = null
        askedSetup = null
        askedNsAnalyticsCap = null
        fileLoggingFailures = emptySet()
        return SurveyStartResult(results, emptyMap(), recording = false)
    }

    /**
     * Runs what the running survey asked for again, so an item left out for a radio, a permission
     * or a missing setting starts once it was fixed, whoever started the survey. What already runs
     * keeps running.
     *
     * @return The result, or null when no survey is running.
     */
    fun retrySkipped(): SurveyStartResult? {
        val asked = askedSetup ?: return null
        if (!isRecording()) return null
        Timber.i("Retrying what the survey asked for: types=%s, destinations=%s", asked.recordTypes, asked.destinations)
        return reconcile(asked, askedNsAnalyticsCap, SurveyOrigin.USER)
    }

    /**
     * Takes [destination] out of the running survey because it can no longer take records, without
     * reconciling the rest (which could find nothing else runnable and leave it running). The
     * survey stops when [destination] was the only one running.
     *
     * @return True if [destination] was running and is stopped now.
     */
    fun dropDestination(destination: Destination): Boolean {
        if (!outputs.isRunning(destination)) return false
        if (runningSetup().destinations == setOf(destination)) {
            stopSurvey(SurveyOrigin.USER)
        } else {
            stopDestination(destination)
            requestedSetup = requestedSetup?.let { SurveySetup.custom(it.recordTypes, it.destinations - destination) }
            askedSetup = askedSetup?.let { SurveySetup.custom(it.recordTypes, it.destinations - destination) }
            if (destination == Destination.NS_ANALYTICS) askedNsAnalyticsCap = null
        }
        return !outputs.isRunning(destination)
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
        val requestedTypes = DestinationCapabilities.effectiveRecordTypes(
            effectiveSetup.recordTypes,
            outputs.autoIncludePhoneState(),
            policy.phoneStateForcedOff,
        )
        val types = requestedTypes.filterTo(linkedSetOf()) { type ->
            val problem = outputs.probeRecordType(type)
            if (problem != null) recordTypeProblems[type] = problem
            problem == null
        }

        val results = linkedMapOf<Destination, DestinationResult>()
        val targets = linkedMapOf<Destination, Set<RecordType>>()
        val communityTargets = outputs.communityTargets()
        // The record types some destination that may start could take, had none been left out
        val takeableTypes = mutableSetOf<RecordType>()
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

            takeableTypes += typesFor(destination, requestedTypes, communityTargets, nsAnalyticsCap)
            val destinationTypes = typesFor(destination, types, communityTargets, nsAnalyticsCap)
            if (destinationTypes.isEmpty()) {
                if (isHeartbeatOnlyMqtt(destination, requestedTypes)) {
                    targets[destination] = emptySet()
                    continue
                }
                results[destination] = DestinationResult.Skipped(SkipReason.RECEIVES_NOTHING)
                continue
            }
            targets[destination] = destinationTypes
        }
        // A problem only matters for a type some destination could take: fixing it changes nothing
        // otherwise (Bluetooth off for a survey that only sends to the community databases).
        recordTypeProblems.keys.retainAll(takeableTypes)

        if (targets.isEmpty()) {
            if (isRecording() && origin == SurveyOrigin.ORGANIZATION) {
                // The organization's change leaves nothing this phone may run, so the survey stops.
                Timber.w("Nothing in the organization's setup can run, so the running survey is stopped")
                val stopped = stopSurvey(SurveyOrigin.ORGANIZATION) // Also forgets the requested setup
                return SurveyStartResult(results + stopped.destinations, recordTypeProblems, recording = false)
            }
            if (isRecording()) {
                // A user change that cannot run (for example Wi-Fi only with the radio off) is
                // reported and the running survey is left as it is.
                Timber.w("Nothing in the requested setup can run, so the running survey is left unchanged")
                return SurveyStartResult(results, recordTypeProblems, recording = true)
            }
            Timber.w("Nothing in the requested setup can start, so the survey stays ready")
            requestedSetup = null
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
        if (Destination.LOG_FILES !in targets) fileLoggingFailures = emptySet()

        val recording = isRecording()
        // A start whose destinations all gave up at once (MQTT refusing its settings inside the
        // connect) keeps what it asked for, the same as a survey whose only destination gave up
        // later, so Ready can say why it stopped. Nothing retries it while nothing records.
        requestedSetup = SurveySetup.custom(requestedTypes, targets.keys)
        askedSetup = if (recording) requested else null
        askedNsAnalyticsCap = if (recording) nsAnalyticsCap else null
        return SurveyStartResult(results, recordTypeProblems, recording = recording)
    }

    /** @return The record types [destination] takes from [types], capped for NS Analytics by [nsAnalyticsCap]. */
    private fun typesFor(
        destination: Destination,
        types: Set<RecordType>,
        communityTargets: Set<RecordType>,
        nsAnalyticsCap: Set<RecordType>?,
    ): Set<RecordType> {
        val destinationTypes = DestinationCapabilities.typesFor(destination, types, communityTargets)
        return if (destination == Destination.NS_ANALYTICS && nsAnalyticsCap != null) {
            destinationTypes intersect nsAnalyticsCap
        } else {
            destinationTypes
        }
    }

    /**
     * @return True if [destination] is MQTT in a survey that asks for no record types at all, and
     * MQTT still has something to send (the device status heartbeat or the watchlist). Such a
     * setup only comes from the organization, the upgrade migration or the Intent API; the setup
     * screen always asks for a record type. A survey whose types were all left out (a radio off)
     * is not one, so MQTT still receives nothing then.
     */
    private fun isHeartbeatOnlyMqtt(destination: Destination, requestedTypes: Set<RecordType>): Boolean =
        destination == Destination.MQTT && requestedTypes.isEmpty() && outputs.mqttSendsWithoutRecordTypes()

    private fun startOrUpdate(destination: Destination, types: Set<RecordType>): DestinationResult {
        if (destination == Destination.LOG_FILES) return applyFileLogging(types)

        if (!outputs.isRunning(destination)) {
            val started = when (destination) {
                Destination.NS_ANALYTICS -> outputs.startNsAnalytics(types)
                Destination.MQTT -> outputs.startMqtt(types)
                Destination.GRPC -> outputs.startGrpc(types)
                Destination.COMMUNITY -> outputs.startCommunity(types)
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
            Destination.COMMUNITY -> outputs.updateCommunityTypes(types)
            Destination.LOG_FILES -> return DestinationResult.Unchanged
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
        fileLoggingFailures = types - after
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
