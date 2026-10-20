package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType

/**
 * The operations the [SurveyOrchestrator] needs from the survey service. The service implements
 * it with thin adapters over its existing per-destination start and stop methods, which keep
 * carrying the permission, radio, MDM and backpressure logic. Tests implement it with a fake.
 *
 * State methods must report the **actual** running state, never a cached plan, because surveys can
 * also be started by boot, the Intent API and other screens.
 */
interface SurveyOutputs {

    /** @return The record types [destination] is currently receiving; empty when it is not running. */
    fun activeTypes(destination: Destination): Set<RecordType>

    /** @return True if [destination] is running or connecting. */
    fun isRunning(destination: Destination): Boolean

    /**
     * Checks whether [destination] could start right now, without any side effect (no prompts,
     * no panels, no toasts).
     *
     * @return Null if it can start, otherwise why it cannot.
     */
    fun probeDestination(destination: Destination): SkipReason?

    /**
     * Checks whether [type] could be recorded right now, without any side effect.
     *
     * @return Null if it can be recorded, otherwise why it cannot.
     */
    fun probeRecordType(type: RecordType): SkipReason?

    /** @return The record types the selected community upload targets need. */
    fun communityTargets(): Set<RecordType>

    /** @return The `auto_include_phone_state_with_cellular` setting (MDM aware). */
    fun autoIncludePhoneState(): Boolean

    /**
     * Turns file logging of [type] on or off.
     *
     * @return True if the type is now in the requested state.
     */
    fun setFileLoggingEnabled(type: RecordType, enabled: Boolean): Boolean

    /** Connects to the active MQTT broker streaming [types]. @return True if the connection was started. */
    fun startMqtt(types: Set<RecordType>): Boolean

    /** Changes the record types streamed over the running MQTT connection. */
    fun updateMqttTypes(types: Set<RecordType>)

    /** Disconnects from the MQTT broker. */
    fun stopMqtt()

    /** Starts collecting [types] for NS Analytics. @return True if collection started. */
    fun startNsAnalytics(types: Set<RecordType>): Boolean

    /** Changes the record types collected for NS Analytics. */
    fun updateNsAnalyticsTypes(types: Set<RecordType>)

    /** Stops collecting for NS Analytics. */
    fun stopNsAnalytics()

    /** Starts connecting to the gRPC server streaming [types]. @return True if the connection was started. */
    fun startGrpc(types: Set<RecordType>): Boolean

    /** Changes the record types streamed over the gRPC connection. */
    fun updateGrpcTypes(types: Set<RecordType>)

    /** Disconnects from the gRPC server. */
    fun stopGrpc()

    /**
     * Starts saving [types] for the community databases; [types] is already narrowed to what the
     * selected upload targets take. @return True if it started.
     */
    fun startCommunity(types: Set<RecordType>): Boolean

    /** Changes the record types saved for the community databases. */
    fun updateCommunityTypes(types: Set<RecordType>)

    /** Stops saving records for the community databases. */
    fun stopCommunity()

    /**
     * Ends the survey session and Mission ID session immediately, even if a destination is still
     * finishing an asynchronous stop.
     */
    fun endMissionSession()
}
