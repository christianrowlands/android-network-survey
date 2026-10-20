package com.craxiom.networksurvey.ui.survey.model

import com.craxiom.networksurvey.survey.engine.LinkFailure
import com.craxiom.networksurvey.survey.engine.SkipReason
import com.craxiom.networksurvey.survey.engine.SurveyOrigin
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.status.DestinationStatus
import com.craxiom.networksurvey.survey.status.LeftOutItem

/**
 * Everything the Survey screen draws, as plain data. [SurveyUiStateMapper] builds it from the
 * service state; the composables turn it into words, so every rule here is JVM tested and the
 * strings stay in resources.
 */
data class SurveyUiState(
    val recording: RecordingUi? = null,
    val location: LocationStatus = LocationStatus.Finding,
    val lastSession: LastSessionUi? = null,
    val orgLine: OrgLineUi? = null,
    val tiles: List<RecordTypeTileUi> = emptyList(),
    val typeCount: SectionCount = SectionCount(0, 0),
    val destinations: List<DestinationRowUi> = emptyList(),
    val destinationCount: SectionCount = SectionCount(0, 0),
    val userHiddenCount: Int = 0,
    val editList: List<EditListItemUi> = emptyList(),
    val sentence: SentenceUi? = null,
    val startBlock: StartBlockReason? = null,
    val plates: List<AttentionPlateUi> = emptyList(),
    val stopNeedsConfirmation: Boolean = false,
    val stopRestartsAtBoot: Boolean = false,
    val communityUpload: CommunityUploadUi? = null,
    val destinationInfo: DestinationInfo = DestinationInfo(),
    val userSetup: SurveySetup = SurveySetup.empty(),
    val savedSetups: SavedSetupsUi? = null,
    val nowMs: Long = 0L,
    val stillToDo: List<StillToDoItemUi> = emptyList(),
    val routing: RoutingTableUi? = null,
    val known: Boolean = true,
) {
    /** True while a survey runs; the screen shows the Recording layout and the Stop button. */
    val isRecording: Boolean get() = recording != null

    companion object {
        /**
         * Before the service has said whether a survey runs. The screen draws neither Ready nor
         * Recording then, so a running survey never shows Start while the app connects.
         */
        val UNKNOWN = SurveyUiState(known = false)
    }
}

/**
 * The saved setups, opened from the Survey tab's app bar while Ready. Null when the organization sets every record
 * type and destination, because a saved setup could not change anything there.
 *
 * @property setups   Every saved setup, in display order (starred first).
 * @property selected The saved setup the current selection matches, or null.
 * @property canSave  False while nothing to record or nowhere to send is selected.
 */
data class SavedSetupsUi(
    val setups: List<SurveySetup>,
    val selected: SurveySetup?,
    val canSave: Boolean,
)

/** The state of the location line at the top of the screen. */
sealed interface LocationStatus {
    data class Fixed(val accuracyMeters: Int) : LocationStatus
    data object Finding : LocationStatus
    data object ProviderOff : LocationStatus
    data object PermissionNeeded : LocationStatus
}

/** "Last survey ran for 42 min · 3,118 records" on the Ready screen. */
data class LastSessionUi(val length: SessionLength, val records: Long)

/** How long the last survey ran: seconds when it was under a minute, whole minutes otherwise. */
sealed interface SessionLength {
    data class Seconds(val seconds: Int) : SessionLength
    data class Minutes(val minutes: Int) : SessionLength
}

/** Why an item can't be changed from the Survey screen. */
enum class ItemLock {
    NONE,

    /** The organization turned it on; it keeps its full look and opens the lock sheet. */
    ORGANIZATION,

    /** Phone state while Cellular is on: recorded whenever Cellular is. */
    INCLUDED_WITH_CELLULAR,
}

/**
 * One record type tile. Types the organization turned off are not listed. [notSentAnywhere] is
 * true when the type is selected but no destination that is on takes it.
 */
data class RecordTypeTileUi(val type: RecordType, val on: Boolean, val lock: ItemLock, val notSentAnywhere: Boolean = false)

/** "3 of 6 on" next to a section heading, or "Set by your organization" when [allLocked]. */
data class SectionCount(val on: Int, val total: Int, val allLocked: Boolean = false)

/** What a destination row offers on its right edge. */
enum class DestinationControl { SWITCH, SET_UP, CONNECT, CHOOSE }

/** The second line of a destination row. */
sealed interface DestinationDetail {
    /** The destination's own description (workspace, broker, address). */
    data object Default : DestinationDetail
    data object NotSetUp : DestinationDetail
    data object NotConnected : DestinationDetail

    /** MQTT has saved brokers but none is selected. */
    data object ChooseBroker : DestinationDetail
    data object TurnedOnByOrganization : DestinationDetail

    /** The community databases: which selected types go to OpenCelliD and which to BeaconDB. */
    data class CommunityRoutes(val openCelliD: Set<RecordType>, val beaconDb: Set<RecordType>) : DestinationDetail
    data object ReceivesNothing : DestinationDetail

    companion object {
        /** @return Why [destination] cannot be used yet (not connected, choose a broker, not set up), or null when it can. */
        fun notReady(destination: Destination, info: DestinationInfo): DestinationDetail? = when {
            info.isReady(destination) -> null
            destination == Destination.NS_ANALYTICS -> NotConnected
            destination == Destination.MQTT && info.mqttBrokersSavedNoneSelected -> ChooseBroker
            else -> NotSetUp
        }

        /** @return The second line of [destination] on a page that lists it outside a survey (More). */
        fun forPage(destination: Destination, info: DestinationInfo): DestinationDetail = notReady(destination, info) ?: Default
    }
}

/** One row under "Where should the data go?". */
data class DestinationRowUi(
    val destination: Destination,
    val on: Boolean,
    val control: DestinationControl,
    val lock: ItemLock,
    val detail: DestinationDetail,
)

/** One checkbox in the "Show on Survey screen" sheet. */
data class EditListItemUi(val destination: Destination, val shown: Boolean, val onInSetup: Boolean)

/**
 * The warning above Start, present only when something in the setup goes nowhere: [notSent] are the
 * selected types no destination that is on takes, and [receivesNothing] the destinations that are on
 * but take none of the selected types. At least one of the two is non-empty.
 */
data class SentenceUi(
    val receivesNothing: List<Destination>,
    val notSent: List<RecordType> = emptyList(),
)

/** Why Start is disabled; the bar shows the reason and, where one exists, the fix. */
enum class StartBlockReason {
    NO_TYPES,
    NO_DESTINATIONS,
    ORGANIZATION_TURNED_OFF_EVERY_DESTINATION,
    NS_ANALYTICS_NOT_CONNECTED,

    /** Every destination that is on would receive nothing (for example CDR to MQTT). */
    NOTHING_DELIVERED,
}

/** The org status line under the location line, highest priority first. */
sealed interface OrgLineUi {
    data object UpdatedWhileOverridden : OrgLineUi
    data object Overridden : OrgLineUi
    data class SetsPartOfSurvey(val turnedOffCount: Int) : OrgLineUi
    data class TurnedOff(val count: Int) : OrgLineUi
}

/**
 * The Recording layout.
 *
 * @property counts              The count of each type the Records panel shows. When
 *                               [countsSavedForUpload] is set it holds only the types the community
 *                               databases receive.
 * @property countsSavedForUpload True when the community databases are the only destination, so the
 *                               counts and the heartbeat are of records saved for upload (which
 *                               only grow as the phone moves), not every record processed.
 */
data class RecordingUi(
    val paused: Boolean,
    val elapsedMs: Long,
    val totalRecords: Long,
    val secondsSinceLastRecord: Long?,
    val stalledMinutes: Int?,
    val missionId: String?,
    val origin: SurveyOrigin?,
    val types: List<RecordType>,
    val counts: Map<RecordType, Long>,
    val destinations: List<LiveDestinationUi>,
    val canChange: Boolean,
    val notSent: Set<RecordType> = emptySet(),
    val countsSavedForUpload: Boolean = false,
)

/**
 * One row of the Ready screen's "Still to do" panel: records still on the phone for a destination
 * that uploads later, and what is happening to them.
 */
data class StillToDoItemUi(val destination: Destination, val pending: Int, val status: DestinationStatus)

/**
 * One row under "Where it's going": the destination and what is happening to its records.
 *
 * @property savedThisSurvey The records saved for the community databases this session, on the
 *                           community row when other destinations run too (the hero counts every
 *                           record then); null otherwise.
 */
data class LiveDestinationUi(
    val destination: Destination,
    val status: DestinationStatus,
    val savedThisSurvey: Long? = null,
)

/** One plate above the Recording card. */
sealed interface AttentionPlateUi {
    /** Paused at [level] by the battery [threshold], which the organization may set ([thresholdSetByOrganization]). */
    data class BatteryPaused(val level: Int, val threshold: Int, val thresholdSetByOrganization: Boolean = false) : AttentionPlateUi

    /** The battery is close to [threshold], where the survey will pause. */
    data class BatteryLow(val level: Int, val threshold: Int, val thresholdSetByOrganization: Boolean = false) : AttentionPlateUi

    /**
     * MQTT is the only destination and its queue is full. With [limitSetByOrganization] the queue
     * limit comes from the organization, so the plate cannot offer to change it.
     */
    data class MqttOnlyPaused(val limitSetByOrganization: Boolean = false) : AttentionPlateUi
    data class MqttDropping(val stillReceiving: List<Destination>) : AttentionPlateUi
    data class Stalled(val minutes: Int) : AttentionPlateUi
    data class NeedsAttention(val items: List<AttentionItemUi>) : AttentionPlateUi

    /** Ready: the last survey ended on its own because [destination], its only destination, gave up. */
    data class SurveyEnded(val destination: Destination, val failure: LinkFailure) : AttentionPlateUi
}

/**
 * One row of the "N things need attention" plate: something a partial start left out, or a
 * running destination whose records aren't arriving.
 */
sealed interface AttentionItemUi {
    data class Type(val type: RecordType, val reason: SkipReason) : AttentionItemUi
    data class Dest(val destination: Destination, val reason: SkipReason) : AttentionItemUi

    /** A running destination whose records aren't arriving; the fix is on its page. */
    data class Delivery(val destination: Destination, val status: DestinationStatus) : AttentionItemUi
}

/** The row for something the running survey left out. */
fun LeftOutItem.toAttentionItem(): AttentionItemUi = when (this) {
    is LeftOutItem.Type -> AttentionItemUi.Type(type, reason)
    is LeftOutItem.Dest -> AttentionItemUi.Dest(destination, reason)
}

/** The compact Community upload row shown while an upload runs or waits to start ([running] false). */
data class CommunityUploadUi(val percent: Int, val message: String?, val running: Boolean = true)
