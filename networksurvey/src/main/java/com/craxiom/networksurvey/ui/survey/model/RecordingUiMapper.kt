package com.craxiom.networksurvey.ui.survey.model

import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.status.DeliveryQueues
import com.craxiom.networksurvey.survey.status.DestinationStatus
import com.craxiom.networksurvey.survey.status.DestinationStatusRules
import com.craxiom.networksurvey.survey.status.LeftOutItems
import com.craxiom.networksurvey.survey.status.StatusLevel
import com.craxiom.networksurvey.survey.status.StatusWord

/**
 * The Recording half of [SurveyUiStateMapper]: the hero, the running destinations, and the
 * attention plates. Everything comes from what the service is actually running, so a survey
 * started at boot or by another app shows exactly what it records.
 */
internal object RecordingUiMapper {

    fun recording(inputs: SurveyInputs, form: FormFacts): RecordingUi {
        val runtime = inputs.runtime
        val paused = runtime.pausedForBattery || runtime.pausedForBackpressure
        val types = SurveyUiStateMapper.ordered(runtime.recordTypes)
        // The community databases only save a record once the phone has moved, so counting every
        // record processed would climb while standing still; they show what was saved instead.
        val savedForUpload = isCommunityOnly(inputs)
        val communityTypes = runtime.destinations[Destination.COMMUNITY]?.types.orEmpty()
        val countedTypes = if (savedForUpload) types.filter { it in communityTypes } else types
        val counts = if (savedForUpload) inputs.communityCounts else inputs.counts
        val lastRecordTimeMs = if (savedForUpload) inputs.communityLastRecordTimeMs else inputs.lastRecordTimeMs
        return RecordingUi(
            paused = paused,
            elapsedMs = runtime.sessionStartTimeMs?.let { (inputs.nowMs - it).coerceAtLeast(0L) } ?: 0L,
            totalRecords = countedTypes.sumOf { counts[it] ?: 0L },
            secondsSinceLastRecord = lastRecordTimeMs?.let {
                ((inputs.nowMs - it) / MILLIS_PER_SECOND).coerceAtLeast(0L)
            },
            stalledMinutes = runtime.stalledMinutes,
            missionId = runtime.missionId?.takeIf { runtime.missionSessionActive },
            origin = runtime.startOrigin,
            types = types,
            counts = countedTypes.associateWith { counts[it] ?: 0L },
            destinations = liveDestinations(inputs, showSavedCount = !savedForUpload),
            canChange = !(form.allTypesManaged && form.allDestinationsManaged),
            notSent = routing(inputs)?.notSentAnywhere.orEmpty(),
            countsSavedForUpload = savedForUpload,
            changeSeed = runtime.requested ?: SurveySetup.custom(runtime.recordTypes, runtime.destinations.keys),
            deviceStatusOnly = runtime.recordTypes.isEmpty() && isMqttDeviceStatusOnly(runtime),
            // A type the organization turned off can't be turned on, so it isn't shown as an off chip
            availableTypes = RecordType.entries.filter { form.policy.isRecordTypeAvailable(it) || it in runtime.recordTypes },
        )
    }

    /**
     * @return True when MQTT runs with no record types: it connects and sends device status (and the
     * watchlist, when it streams) only. Only an organization, an upgrade or another app sets this up.
     */
    private fun isMqttDeviceStatusOnly(runtime: SurveyRuntimeState): Boolean =
        runtime.destinations[Destination.MQTT]?.let { !it.failed && it.types.isEmpty() } == true

    /** @return True when the community databases are the only destination still running. */
    private fun isCommunityOnly(inputs: SurveyInputs): Boolean =
        runningDestinations(inputs.runtime) == setOf(Destination.COMMUNITY)

    /** The destinations in the survey that haven't given up. */
    private fun runningDestinations(runtime: SurveyRuntimeState): Set<Destination> =
        runtime.destinations.filterValues { !it.failed }.keys

    /**
     * "What goes where" while recording: what the survey asked for against the destinations that
     * are running (one that gave up takes nothing).
     */
    fun routing(inputs: SurveyInputs): RoutingTableUi? {
        val runtime = inputs.runtime
        return RoutingTableUi.of(
            types = runtime.requested?.recordTypes ?: runtime.recordTypes,
            destinations = runningDestinations(runtime),
            info = inputs.destinationInfo,
            mqttDeviceStatus = isMqttDeviceStatusOnly(runtime),
        )
    }

    /**
     * @param showSavedCount True to give the community row this session's saved count, for when
     * the hero counts every record processed instead.
     */
    private fun liveDestinations(inputs: SurveyInputs, showSavedCount: Boolean = false): List<LiveDestinationUi> =
        Destination.entries.mapNotNull { destination ->
            inputs.runtime.destinations[destination]?.let {
                LiveDestinationUi(
                    destination = destination,
                    status = status(inputs.runtime, destination, inputs.queues, inputs.destinationInfo, inputs.nowMs),
                    savedThisSurvey = if (showSavedCount && destination == Destination.COMMUNITY) {
                        inputs.communityCounts.values.sum()
                    } else {
                        null
                    },
                )
            }
        }

    /**
     * @return What is happening to [destination]'s records in the running survey, or null when it
     * isn't in it. The destination pages show the same status the Survey screen does.
     */
    fun liveStatus(
        runtime: SurveyRuntimeState,
        destination: Destination,
        nowMs: Long,
        queues: DeliveryQueues = DeliveryQueues(),
        info: DestinationInfo = DestinationInfo(),
    ): DestinationStatus? {
        if (destination !in runtime.destinations) return null
        return status(runtime, destination, queues, info, nowMs)
    }

    /**
     * @return The status a destination's page shows: the same one as its Survey row while it is in
     * the running survey, otherwise what waits for it ([idleStatus]); null when there is nothing to
     * say (a stream or Log files outside a survey).
     */
    fun pageStatus(
        runtime: SurveyRuntimeState?,
        destination: Destination,
        nowMs: Long,
        queues: DeliveryQueues,
        info: DestinationInfo,
    ): DestinationStatus? = runtime?.let { liveStatus(it, destination, nowMs, queues, info) }
        ?: idleStatus(destination, queues, info)

    /**
     * @return The status of a destination that uploads later while no survey sends to it, the one
     * the "Still to do" panel shows; null for a destination that doesn't upload later, or isn't set up.
     */
    fun idleStatus(destination: Destination, queues: DeliveryQueues, info: DestinationInfo): DestinationStatus? =
        when (destination) {
            Destination.NS_ANALYTICS -> DestinationStatusRules.nsAnalyticsIdle(queues.nsAnalytics, info.nsAnalyticsAutoUpload)
                .takeIf { info.nsAnalyticsRegistered }

            Destination.COMMUNITY -> DestinationStatusRules.communityIdle(queues.community, info.communityAutoUpload)
                .takeIf { info.communityTargets.isNotEmpty() }

            else -> null
        }

    private fun status(
        runtime: SurveyRuntimeState,
        destination: Destination,
        queues: DeliveryQueues,
        info: DestinationInfo,
        nowMs: Long,
    ): DestinationStatus = DestinationStatusRules.status(
        destination = destination,
        runtime = runtime.destinations[destination],
        paused = runtime.pausedForBattery || runtime.pausedForBackpressure,
        queues = queues,
        autoUpload = DestinationStatusRules.AutoUpload(info.nsAnalyticsAutoUpload, info.communityAutoUpload),
        nowMs = nowMs,
    )

    fun plates(inputs: SurveyInputs): List<AttentionPlateUi> {
        val runtime = inputs.runtime
        val plates = mutableListOf<AttentionPlateUi>()
        val thresholdManaged = inputs.destinationInfo.batteryThresholdSetByOrganization
        if (runtime.pausedForBattery) {
            plates += AttentionPlateUi.BatteryPaused(runtime.batteryLevel, runtime.batteryThresholdPercent, thresholdManaged)
        } else if (isBatteryNearThreshold(runtime)) {
            plates += AttentionPlateUi.BatteryLow(runtime.batteryLevel, runtime.batteryThresholdPercent, thresholdManaged)
        }
        if (runtime.pausedForBackpressure) {
            plates += AttentionPlateUi.MqttOnlyPaused(inputs.destinationInfo.queueLimitSetByOrganization)
        }
        if (runtime.mqttDropping) {
            plates += AttentionPlateUi.MqttDropping(
                Destination.entries.filter { it != Destination.MQTT && it in runtime.destinations }
            )
        }
        val stalled = runtime.stalledMinutes
        if (stalled != null && !runtime.pausedForBattery && !runtime.pausedForBackpressure) {
            plates += AttentionPlateUi.Stalled(stalled)
        }
        val items = attentionItems(inputs) + deliveryItems(inputs, runtime.mqttDropping)
        if (items.isNotEmpty()) plates += AttentionPlateUi.NeedsAttention(items)
        return plates
    }

    /**
     * The running destinations whose records aren't arriving (red), so the problem shows at the top
     * with a way to its page. MQTT dropping has its own plate.
     */
    private fun deliveryItems(inputs: SurveyInputs, mqttDroppingPlate: Boolean): List<AttentionItemUi> =
        liveDestinations(inputs)
            .filter { it.status.level == StatusLevel.NOT_ARRIVING }
            .filterNot { mqttDroppingPlate && it.destination == Destination.MQTT && it.status.word == StatusWord.DROPPING }
            .map { AttentionItemUi.Delivery(it.destination, it.status) }

    /** True when the battery is known and within the warning margin of a pause threshold that is on. */
    private fun isBatteryNearThreshold(runtime: SurveyRuntimeState): Boolean {
        val threshold = runtime.batteryThresholdPercent
        val level = runtime.batteryLevel
        return threshold > 0 && level >= 0 && level <= threshold + NetworkSurveyConstants.BATTERY_WARNING_MARGIN_PERCENT
    }

    /** Ready: why the last survey ended on its own, if it did. */
    fun readyPlates(inputs: SurveyInputs): List<AttentionPlateUi> =
        listOfNotNull(inputs.runtime.endedBy?.let { AttentionPlateUi.SurveyEnded(it.destination, it.failure) })

    /**
     * What the running survey asked for and isn't getting, whoever started it: the same items the
     * survey notification lists. An item disappears here as soon as it runs.
     */
    private fun attentionItems(inputs: SurveyInputs): List<AttentionItemUi> =
        LeftOutItems.of(inputs.runtime, inputs.wifiOff).map { it.toAttentionItem() }

    private const val MILLIS_PER_SECOND = 1_000L
}
