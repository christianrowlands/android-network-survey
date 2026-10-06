package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What one running destination is doing.
 *
 * @property types      The record types it receives.
 * @property connecting True while an MQTT or gRPC connection is still being established.
 */
data class DestinationRuntime(val types: Set<RecordType>, val connecting: Boolean = false)

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
) {
    /** True while any destination runs or connects. */
    val isRecording: Boolean
        get() = destinations.isNotEmpty()

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
