package com.craxiom.networksurvey.ui.survey.model

import com.craxiom.networksurvey.survey.mdm.ManagedState
import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.DestinationCapabilities
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.status.DestinationStatusRules
import kotlin.math.roundToLong

/**
 * Turns [SurveyInputs] into the [SurveyUiState] the Survey screen draws. Pure, so every rule
 * (what Start needs, what is locked or hidden, what each row says) is covered by JVM tests.
 *
 * The form always shows the user's setup with the organization's locks applied, but the user's
 * own setup is what gets edited and stored, so lifting a lock never leaves the organization's
 * choices behind in it.
 */
object SurveyUiStateMapper {

    fun map(inputs: SurveyInputs): SurveyUiState {
        val policy = inputs.runtime.policy
        val form = FormFacts(inputs)
        val rows = destinationRows(form)
        val readyRouting = RoutingTableUi.of(form.effectiveTypes, rows.filter { it.on }.map { it.destination }, form.info)
        val tiles = tiles(form, readyRouting?.notSentAnywhere.orEmpty())
        val recording = if (inputs.runtime.isRecording) RecordingUiMapper.recording(inputs, form) else null
        val startBlock = if (recording == null) startBlock(form, rows) else null

        return SurveyUiState(
            recording = recording,
            location = inputs.location,
            lastSession = if (recording == null) lastSession(inputs) else null,
            orgLine = orgLine(inputs, form),
            tiles = tiles,
            typeCount = SectionCount(tiles.count { it.on }, tiles.size, form.allTypesManaged),
            destinations = rows,
            destinationCount = SectionCount(rows.count { it.on }, rows.size, form.allDestinationsManaged),
            userHiddenCount = form.availableDestinations.count { it in inputs.hiddenDestinations && !form.isOn(it) },
            editList = form.availableDestinations.map {
                EditListItemUi(it, shown = it !in inputs.hiddenDestinations || form.isOn(it), onInSetup = form.isOn(it))
            },
            sentence = sentence(form, rows, readyRouting?.notSentAnywhere.orEmpty(), startBlock),
            startBlock = startBlock,
            plates = if (recording != null) RecordingUiMapper.plates(inputs) else RecordingUiMapper.readyPlates(inputs),
            stopNeedsConfirmation = policy.isManaged && !policy.overrideOn,
            stopRestartsAtBoot = policy.isAutoStartEnabled(inputs.userAutoStart),
            communityUpload = inputs.communityUpload,
            destinationInfo = inputs.destinationInfo,
            userSetup = inputs.userSetup,
            savedSetups = savedSetups(inputs, form),
            nowMs = inputs.nowMs,
            stillToDo = if (recording == null) stillToDo(inputs) else emptyList(),
            routing = if (recording != null) RecordingUiMapper.routing(inputs) else readyRouting,
        )
    }

    /**
     * Records still on the phone after a survey, for destinations that can still upload them. The
     * panel goes away by itself once both queues are empty.
     */
    private fun stillToDo(inputs: SurveyInputs): List<StillToDoItemUi> {
        val info = inputs.destinationInfo
        val nsAnalytics = inputs.queues.nsAnalytics
        val community = inputs.queues.community
        return listOfNotNull(
            StillToDoItemUi(
                Destination.NS_ANALYTICS,
                nsAnalytics.pending,
                DestinationStatusRules.nsAnalyticsIdle(nsAnalytics, info.nsAnalyticsAutoUpload),
            ).takeIf { info.nsAnalyticsRegistered && nsAnalytics.pending > 0 },
            StillToDoItemUi(
                Destination.COMMUNITY,
                community.pending,
                DestinationStatusRules.communityIdle(community, info.communityAutoUpload),
            ).takeIf { info.communityTargets.isNotEmpty() && community.pending > 0 },
        )
    }

    /** Hidden when the organization sets every record type and destination, leaving nothing to pick. */
    private fun savedSetups(inputs: SurveyInputs, form: FormFacts): SavedSetupsUi? {
        if (form.allTypesManaged && form.allDestinationsManaged) return null
        return SavedSetupsUi(
            setups = inputs.savedSetups,
            selected = selectedSetup(inputs.savedSetups, inputs.selectedSetupId, inputs.userSetup),
            canSave = !inputs.userSetup.isEmpty,
        )
    }

    /**
     * @return The saved setup [working] matches: the one it was last picked from while it still
     * has the same content, else the first saved setup with the same content, else null. So a chip
     * lights up again when tile edits bring the selection back to a saved setup.
     */
    fun selectedSetup(saved: List<SurveySetup>, selectedId: String?, working: SurveySetup): SurveySetup? =
        saved.firstOrNull { it.id == selectedId && it.hasSameContentAs(working) }
            ?: saved.firstOrNull { it.hasSameContentAs(working) }

    private fun tiles(form: FormFacts, notSent: Set<RecordType>): List<RecordTypeTileUi> = RecordType.entries
        .filter { form.policy.isRecordTypeAvailable(it) }
        .map { type ->
            val lock = when {
                form.policy.lockFor(type) == ManagedState.ON -> ItemLock.ORGANIZATION
                type == RecordType.PHONE_STATE && form.phoneStateIncluded -> ItemLock.INCLUDED_WITH_CELLULAR
                else -> ItemLock.NONE
            }
            RecordTypeTileUi(type, on = type in form.effectiveTypes, lock = lock, notSentAnywhere = type in notSent)
        }

    private fun destinationRows(form: FormFacts): List<DestinationRowUi> = form.visibleDestinations.map { destination ->
        val ready = form.info.isReady(destination)
        val on = form.isOn(destination)
        val received = DestinationCapabilities.typesFor(destination, form.effectiveTypes, form.info.communityTargets)
        val organizationOn = form.policy.lockFor(destination) == ManagedState.ON
        val detail = DestinationDetail.notReady(destination, form.info) ?: when {
            on && form.effectiveTypes.isNotEmpty() && received.isEmpty() -> DestinationDetail.ReceivesNothing
            on && destination == Destination.COMMUNITY -> DestinationDetail.CommunityRoutes(
                openCelliD = if (form.info.openCelliDSelected) DestinationCapabilities.OPENCELLID_TYPES intersect received else emptySet(),
                beaconDb = if (form.info.beaconDbSelected) DestinationCapabilities.BEACONDB_TYPES intersect received else emptySet(),
            )

            organizationOn -> DestinationDetail.TurnedOnByOrganization
            else -> DestinationDetail.Default
        }
        DestinationRowUi(
            destination = destination,
            on = on,
            control = when {
                ready -> DestinationControl.SWITCH
                destination == Destination.NS_ANALYTICS -> DestinationControl.CONNECT
                destination == Destination.MQTT && form.info.mqttBrokersSavedNoneSelected -> DestinationControl.CHOOSE
                else -> DestinationControl.SET_UP
            },
            lock = if (organizationOn) ItemLock.ORGANIZATION else ItemLock.NONE,
            detail = detail,
        )
    }

    /**
     * The warning above Start, or null when every selected type goes somewhere and every destination
     * that is on gets something. Also null when nothing is delivered at all: the Start bar already
     * gives that reason, and the tiles and rows name what is affected.
     */
    private fun sentence(
        form: FormFacts,
        rows: List<DestinationRowUi>,
        notSent: Set<RecordType>,
        startBlock: StartBlockReason?,
    ): SentenceUi? {
        val on = rows.filter { it.on }
        if (form.effectiveTypes.isEmpty() || on.isEmpty() || startBlock == StartBlockReason.NOTHING_DELIVERED) return null
        val receivesNothing = on.filter { it.detail == DestinationDetail.ReceivesNothing }.map { it.destination }
        if (notSent.isEmpty() && receivesNothing.isEmpty()) return null
        return SentenceUi(receivesNothing = receivesNothing, notSent = ordered(notSent))
    }

    private fun startBlock(form: FormFacts, rows: List<DestinationRowUi>): StartBlockReason? {
        val on = rows.filter { it.on }
        return when {
            form.effectiveTypes.isEmpty() -> StartBlockReason.NO_TYPES
            on.isEmpty() && form.availableDestinations.isEmpty() ->
                StartBlockReason.ORGANIZATION_TURNED_OFF_EVERY_DESTINATION

            on.isEmpty() && Destination.NS_ANALYTICS in form.effective.destinations &&
                !form.info.nsAnalyticsRegistered -> StartBlockReason.NS_ANALYTICS_NOT_CONNECTED

            on.isEmpty() -> StartBlockReason.NO_DESTINATIONS
            on.all { it.detail == DestinationDetail.ReceivesNothing } -> StartBlockReason.NOTHING_DELIVERED
            else -> null
        }
    }

    private fun orgLine(inputs: SurveyInputs, form: FormFacts): OrgLineUi? {
        val policy = form.policy
        if (!policy.isManaged) return null
        val turnedOff = RecordType.entries.count { !policy.isRecordTypeAvailable(it) } +
            Destination.entries.count { !policy.isDestinationAvailable(it) }
        return when {
            policy.overrideOn && inputs.runtime.orgUpdatedWhileOverridden -> OrgLineUi.UpdatedWhileOverridden
            policy.overrideOn -> OrgLineUi.Overridden
            policy.hasSetupLocks -> OrgLineUi.SetsPartOfSurvey(turnedOff)
            turnedOff > 0 -> OrgLineUi.TurnedOff(turnedOff)
            else -> null
        }
    }

    private fun lastSession(inputs: SurveyInputs): LastSessionUi? {
        val last = inputs.runtime.lastSession ?: return null
        val seconds = (last.durationMs / MILLIS_PER_SECOND.toDouble()).roundToLong().coerceAtLeast(1L)
        val length = if (seconds < SECONDS_PER_MINUTE) {
            SessionLength.Seconds(seconds.toInt())
        } else {
            SessionLength.Minutes((last.durationMs / MILLIS_PER_MINUTE.toDouble()).roundToLong().toInt())
        }
        return LastSessionUi(length, last.totalRecords)
    }

    /** @return [types] in the order the screen always lists record types. */
    fun ordered(types: Collection<RecordType>): List<RecordType> = RecordType.entries.filter { it in types }

    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MILLIS_PER_MINUTE = 60_000L
}

/** The facts about the setup that both the Ready form and the Recording layout derive from. */
internal class FormFacts(inputs: SurveyInputs) {
    val policy: SurveyPolicyState = inputs.runtime.policy
    val info: DestinationInfo = inputs.destinationInfo
    val effective: SurveySetup = policy.effectiveSetup(inputs.userSetup)
    val effectiveTypes: Set<RecordType> = DestinationCapabilities.effectiveRecordTypes(
        effective.recordTypes, inputs.autoIncludePhoneState, policy.phoneStateForcedOff,
    )
    val phoneStateIncluded: Boolean = inputs.autoIncludePhoneState && !policy.phoneStateForcedOff &&
        RecordType.CELLULAR in effective.recordTypes
    val availableDestinations: List<Destination> = Destination.entries.filter { policy.isDestinationAvailable(it) }
    val visibleDestinations: List<Destination> =
        availableDestinations.filter { it !in inputs.hiddenDestinations || isOn(it) }
    val allTypesManaged: Boolean = RecordType.entries.all { policy.lockFor(it).isManaged }
    val allDestinationsManaged: Boolean =
        Destination.entries.all { policy.lockFor(it).isManaged || it in policy.blockedDestinations }

    /** A destination counts as on only when it is in the setup and has what it needs to run. */
    fun isOn(destination: Destination): Boolean = destination in effective.destinations && info.isReady(destination)
}
