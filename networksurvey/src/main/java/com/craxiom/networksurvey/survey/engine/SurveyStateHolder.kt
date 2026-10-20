package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where an MQTT or gRPC connection stands. */
enum class LinkState {
    /** Trying to connect, for the first time or again after the connection dropped. */
    CONNECTING,

    /** Connected; records are being sent. */
    CONNECTED,

    /** Gave up: the survey asked for it, but it isn't trying any more (for example a refused sign-in). */
    FAILED,
}

/** Why an MQTT or gRPC connection isn't connected. */
enum class LinkFailure {
    /** The server or broker can't be reached, or the connection dropped. Retrying on its own. */
    UNREACHABLE,

    /** The broker refused the user name or password. Not retrying. */
    SIGN_IN_REJECTED,

    /** The broker settings are not valid (for example a wildcard in the topic prefix). Not retrying. */
    INVALID_SETTINGS,
}

/**
 * The health of an MQTT or gRPC connection, for a status that says what is happening to the data.
 *
 * @property state         Where the connection stands.
 * @property sinceMs       When it started connecting (meaningful while [state] is CONNECTING).
 * @property everConnected True if it has connected since the survey asked for it, so a
 *                         CONNECTING state is a reconnect.
 * @property failure       Why it isn't connected, if known.
 * @property waiting       Records held on the phone, waiting to be sent.
 * @property dropping      True while its queue is full and new records are thrown away.
 */
data class StreamLink(
    val state: LinkState,
    val sinceMs: Long = 0L,
    val everConnected: Boolean = false,
    val failure: LinkFailure? = null,
    val waiting: Int = 0,
    val dropping: Boolean = false,
)

/**
 * How the community databases' automatic upload is doing.
 *
 * @property failures    Uploads that failed in a row.
 * @property retryAtMs   When automatic upload tries again after failures, or 0 if it isn't waiting.
 * @property keyRejected True if OpenCelliD refused the API key, so it gets no more uploads.
 */
data class CommunityUploadHealth(
    val failures: Int = 0,
    val retryAtMs: Long = 0L,
    val keyRejected: Boolean = false,
)

/**
 * What one destination in the survey is doing.
 *
 * @property types       The record types it receives (empty for a failed connection).
 * @property link        The connection health, for MQTT and gRPC; null for the others.
 * @property failedTypes Log files: record types whose file couldn't be created, so they aren't saved.
 * @property upload      The community databases' automatic upload health; null for the others.
 */
data class DestinationRuntime(
    val types: Set<RecordType>,
    val link: StreamLink? = null,
    val failedTypes: Set<RecordType> = emptySet(),
    val upload: CommunityUploadHealth? = null,
) {
    /** True while an MQTT or gRPC connection is still being established. */
    val connecting: Boolean
        get() = link?.state == LinkState.CONNECTING

    /** True if the survey asked for this destination but it gave up (it receives nothing). */
    val failed: Boolean
        get() = link?.state == LinkState.FAILED
}

/**
 * Why the last survey ended without anyone stopping it: the only destination it had gave up.
 *
 * @property destination The destination that gave up.
 * @property failure     Why.
 */
data class SurveyEndedBy(val destination: Destination, val failure: LinkFailure)

/**
 * A snapshot of the survey as it is actually running in the service. The Survey screen renders
 * this, so a survey started at boot, by the Intent API or by MDM shows as recording with its real
 * record types and destinations, never a stored plan.
 *
 * Per record type counts change with every record, so they are not part of the snapshot; read them
 * from the service's session counters when rendering.
 *
 * @property destinations          The running destinations; empty when nothing runs.
 * @property missionId             The rolled Mission ID, kept after the survey stops; null if none
 *                                 has rolled in this app session.
 * @property missionSessionActive  True while a mission relevant destination runs.
 * @property sessionStartTimeMs    When the session started, or null when nothing runs.
 * @property pausedForBattery      True while scanning is paused for low battery.
 * @property pausedForBackpressure True while scanning is paused because the MQTT queue is full and
 *                                 MQTT is the only destination.
 * @property mqttDropping          True while MQTT drops messages because its queue is full.
 * @property stalledMinutes        Minutes without a record while running, or null when records flow.
 * @property policy                The organization policy in force.
 * @property startOrigin           What started the running survey, or null when nothing runs.
 * @property lastResult            The result of the last start or change, for the attention plate.
 * @property batteryLevel          The battery percentage, or -1 when unknown.
 * @property batteryThresholdPercent Scanning pauses below this battery percentage; 0 means never.
 * @property mqttQueueLimit        The MQTT message queue limit; 0 means unlimited.
 * @property orgUpdatedWhileOverridden True when the organization changed its configuration while
 *                                 the user had "Override organization settings" on.
 * @property lastSession           The most recent session that ended, or null if none has.
 * @property requested             What the running survey asked for (record types and
 *                                 destinations), so the screen can say what isn't sent anywhere;
 *                                 null when nothing runs.
 * @property endedBy               Why the last survey ended on its own, or null.
 */
data class SurveyRuntimeState(
    val destinations: Map<Destination, DestinationRuntime> = emptyMap(),
    val missionId: String? = null,
    val missionSessionActive: Boolean = false,
    val sessionStartTimeMs: Long? = null,
    val pausedForBattery: Boolean = false,
    val pausedForBackpressure: Boolean = false,
    val mqttDropping: Boolean = false,
    val stalledMinutes: Int? = null,
    val policy: SurveyPolicyState = SurveyPolicyState.UNMANAGED,
    val startOrigin: SurveyOrigin? = null,
    val lastResult: SurveyStartResult? = null,
    val batteryLevel: Int = -1,
    val batteryThresholdPercent: Int = 0,
    val mqttQueueLimit: Int = 0,
    val orgUpdatedWhileOverridden: Boolean = false,
    val lastSession: LastSession? = null,
    val requested: SurveySetup? = null,
    val endedBy: SurveyEndedBy? = null,
) {
    /** True while any destination runs or connects (one that gave up doesn't count). */
    val isRecording: Boolean
        get() = destinations.values.any { !it.failed }

    /** Every record type any running destination receives. */
    val recordTypes: Set<RecordType>
        get() = destinations.values.flatMap { it.types }.toSet()
}

/**
 * Publishes the [SurveyRuntimeState]. The service republishes it every time it refreshes the
 * survey notification, so the screen and the notification always describe the same state.
 */
class SurveyStateHolder {
    private val mutableState = MutableStateFlow(SurveyRuntimeState())

    /** The latest survey state. */
    val state: StateFlow<SurveyRuntimeState> = mutableState.asStateFlow()

    /** Replaces the published state. */
    fun publish(newState: SurveyRuntimeState) {
        mutableState.value = newState
    }
}
