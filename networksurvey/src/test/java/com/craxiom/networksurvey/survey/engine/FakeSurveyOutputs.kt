package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType

/**
 * An in-memory survey service for orchestrator tests. It drives a real [MissionSessionTracker] the
 * same way the service does: each destination start calls [MissionSessionTracker.onSurveyStarted]
 * and each stop calls [MissionSessionTracker.onSurveyStopped]. gRPC is asynchronous: it sits in a
 * connecting state until the test calls [completeGrpcConnection].
 */
open class FakeSurveyOutputs : SurveyOutputs {

    val calls = mutableListOf<String>()
    private var missionCounter = 0
    val missionIds = mutableListOf<String?>()

    val tracker = MissionSessionTracker(
        generateMissionId = { "mission-${++missionCounter}" },
        isAnySurveyActive = { Destination.entries.any { isRunning(it) } },
        isAnyMissionRelevantSurveyActive = { Destination.entries.filter { it.isMissionRelevant }.any { isRunning(it) } },
        onMissionIdChanged = { id, _ -> missionIds += id },
    )

    val fileTypes = linkedSetOf<RecordType>()
    var mqttTypes: Set<RecordType>? = null
    var nsAnalyticsTypes: Set<RecordType>? = null
    var grpcTypes: Set<RecordType>? = null
    var grpcConnected = false
    var communityRunning = false
    var communityTypes: Set<RecordType> = emptySet()

    /** When true, a gRPC stop only takes effect once [completeGrpcDisconnect] is called, like the real service. */
    var asyncGrpcStop = false

    var destinationProblems = mutableMapOf<Destination, SkipReason>()
    var typeProblems = mutableMapOf<RecordType, SkipReason>()
    var failingFileTypes = mutableSetOf<RecordType>()
    var autoIncludePhoneState = true

    /** What [mqttSendsWithoutRecordTypes] answers: device status or the watchlist streams over MQTT. */
    var mqttSendsWithoutRecordTypes = false
    var community = setOf(RecordType.CELLULAR, RecordType.WIFI)

    /** The number of distinct Mission IDs rolled so far. */
    val rolls: Int
        get() = missionCounter

    override fun activeTypes(destination: Destination): Set<RecordType> = when (destination) {
        Destination.LOG_FILES -> fileTypes.toSet()
        Destination.MQTT -> mqttTypes.orEmpty()
        Destination.NS_ANALYTICS -> nsAnalyticsTypes.orEmpty()
        Destination.GRPC -> grpcTypes.orEmpty()
        Destination.COMMUNITY -> if (communityRunning) communityTypes else emptySet()
    }

    override fun isRunning(destination: Destination): Boolean = when (destination) {
        Destination.LOG_FILES -> fileTypes.isNotEmpty()
        Destination.MQTT -> mqttTypes != null
        Destination.NS_ANALYTICS -> nsAnalyticsTypes != null
        Destination.GRPC -> grpcTypes != null
        Destination.COMMUNITY -> communityRunning
    }

    override fun probeDestination(destination: Destination): SkipReason? = destinationProblems[destination]
    override fun probeRecordType(type: RecordType): SkipReason? = typeProblems[type]
    override fun communityTargets(): Set<RecordType> = community
    override fun autoIncludePhoneState(): Boolean = autoIncludePhoneState
    override fun mqttSendsWithoutRecordTypes(): Boolean = mqttSendsWithoutRecordTypes

    override fun setFileLoggingEnabled(type: RecordType, enabled: Boolean): Boolean {
        calls += "file ${type.wireName} $enabled"
        if (enabled) {
            if (type in failingFileTypes) return false
            tracker.onSurveyStarted(true)
            fileTypes += type
            if (type == RecordType.CELLULAR && autoIncludePhoneState) fileTypes += RecordType.PHONE_STATE
        } else {
            fileTypes -= type
            if (type == RecordType.CELLULAR) fileTypes -= RecordType.PHONE_STATE
            tracker.onSurveyStopped()
        }
        return true
    }

    override fun startMqtt(types: Set<RecordType>): Boolean {
        calls += "mqtt start $types"
        mqttTypes = types
        tracker.onSurveyStarted(true)
        return true
    }

    override fun updateMqttTypes(types: Set<RecordType>) {
        calls += "mqtt update $types"
        mqttTypes = types
    }

    override fun stopMqtt() {
        calls += "mqtt stop"
        mqttTypes = null
        tracker.onSurveyStopped()
    }

    override fun startNsAnalytics(types: Set<RecordType>): Boolean {
        calls += "nsa start $types"
        tracker.onSurveyStarted(true)
        nsAnalyticsTypes = types
        return true
    }

    override fun updateNsAnalyticsTypes(types: Set<RecordType>) {
        calls += "nsa update $types"
        nsAnalyticsTypes = types
    }

    override fun stopNsAnalytics() {
        calls += "nsa stop"
        nsAnalyticsTypes = null
        tracker.onSurveyStopped()
    }

    override fun startGrpc(types: Set<RecordType>): Boolean {
        calls += "grpc start $types"
        grpcTypes = types
        grpcConnected = false
        return true
    }

    /** Simulates the gRPC handshake finishing: the service reports CONNECTED, which starts the session. */
    fun completeGrpcConnection() {
        if (grpcTypes == null) return
        grpcConnected = true
        tracker.onSurveyStarted(true)
    }

    override fun updateGrpcTypes(types: Set<RecordType>) {
        calls += "grpc update $types"
        grpcTypes = types
    }

    override fun stopGrpc() {
        calls += "grpc stop"
        if (!asyncGrpcStop) completeGrpcDisconnect()
    }

    /** Simulates the gRPC service finishing its disconnect and reporting DISCONNECTED. */
    fun completeGrpcDisconnect() {
        grpcTypes = null
        grpcConnected = false
        tracker.onSurveyStopped()
    }

    override fun startCommunity(types: Set<RecordType>): Boolean {
        calls += "community start"
        communityRunning = true
        communityTypes = types
        tracker.onSurveyStarted(false)
        return true
    }

    override fun updateCommunityTypes(types: Set<RecordType>) {
        calls += "community types $types"
        communityTypes = types
    }

    override fun stopCommunity() {
        calls += "community stop"
        communityRunning = false
        tracker.onSurveyStopped()
    }

    override fun endMissionSession() {
        calls += "end session"
        tracker.endSession()
    }
}
