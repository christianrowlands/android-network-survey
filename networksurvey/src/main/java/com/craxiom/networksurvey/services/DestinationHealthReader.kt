package com.craxiom.networksurvey.services

import com.craxiom.mqttlibrary.connection.ConnectionFailure
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.mqttlibrary.connection.DefaultMqttConnection
import com.craxiom.networksurvey.logging.db.uploader.AutoUploadManager
import com.craxiom.networksurvey.survey.engine.CommunityUploadHealth
import com.craxiom.networksurvey.survey.engine.DestinationRuntime
import com.craxiom.networksurvey.survey.engine.LinkFailure
import com.craxiom.networksurvey.survey.engine.LinkState
import com.craxiom.networksurvey.survey.engine.StreamLink
import com.craxiom.networksurvey.survey.engine.SurveyEndedBy
import com.craxiom.networksurvey.survey.engine.SurveyOutputs
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import java.util.EnumMap

/**
 * Reads the health of every destination in the survey for the published survey state: how each
 * MQTT and gRPC connection is doing, which Log files types couldn't start, and how the community
 * databases' automatic upload is doing. Kept out of [NetworkSurveyService] so the service only
 * wires it.
 *
 * A destination the survey asked for that gave up (MQTT whose sign-in was refused) stays in the
 * map, with no record types, so the screen can say so instead of dropping it without a word.
 *
 * @param mqtt            The MQTT connection, or null before the service created it.
 * @param mqttDropping    True while the service drops MQTT messages because its queue is full.
 * @param autoUpload      The community databases' automatic upload, or null when it isn't on.
 * @param clock           The current time in milliseconds.
 */
class DestinationHealthReader(
    private val mqtt: () -> DefaultMqttConnection?,
    private val mqttDropping: () -> Boolean,
    private val autoUpload: () -> AutoUploadManager?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var mqttState = ConnectionState.DISCONNECTED

    @Volatile
    private var mqttConnectingSinceMs = 0L

    /** Called from the service's MQTT connection state listener, to know since when it connects. */
    fun onMqttStateChanged(state: ConnectionState) {
        if (state == ConnectionState.CONNECTING && mqttState != ConnectionState.CONNECTING) {
            mqttConnectingSinceMs = clock()
        }
        mqttState = state
    }

    /**
     * @param outputs     The service operations, for what runs and with which record types.
     * @param requested   What the survey asked for, or null when nothing runs.
     * @param fileFailures Record types Log files couldn't start.
     * @return Every destination in the survey and its health.
     */
    fun read(
        outputs: SurveyOutputs,
        requested: SurveySetup?,
        fileFailures: Set<RecordType>,
    ): Map<Destination, DestinationRuntime> {
        val destinations = EnumMap<Destination, DestinationRuntime>(Destination::class.java)
        for (destination in Destination.entries) {
            if (outputs.isRunning(destination)) {
                val types = outputs.activeTypes(destination)
                destinations[destination] = when (destination) {
                    Destination.LOG_FILES -> DestinationRuntime(types, failedTypes = fileFailures - types)
                    Destination.MQTT -> DestinationRuntime(types, link = mqttLink())
                    Destination.GRPC -> DestinationRuntime(types, link = grpcLink())
                    Destination.COMMUNITY -> DestinationRuntime(types, upload = communityUpload())
                    Destination.NS_ANALYTICS -> DestinationRuntime(types)
                }
            } else if (destination in requested?.destinations.orEmpty()) {
                failedLink(destination)?.let { destinations[destination] = DestinationRuntime(emptySet(), link = it) }
            }
        }
        // A destination that gave up is only shown while something else runs; otherwise the survey
        // has ended and [endedBy] says why.
        if (destinations.values.all { it.failed }) destinations.clear()
        return destinations
    }

    /**
     * @return Why the survey ended on its own: it still has a requested setup (nobody stopped it),
     * nothing runs, and a destination it asked for gave up. Null otherwise.
     */
    fun endedBy(requested: SurveySetup?, destinations: Map<Destination, DestinationRuntime>): SurveyEndedBy? {
        if (requested == null || destinations.isNotEmpty()) return null
        return requested.destinations.firstNotNullOfOrNull { destination ->
            failedLink(destination)?.failure?.let { SurveyEndedBy(destination, it) }
        }
    }

    private fun mqttLink(): StreamLink? {
        val connection = mqtt() ?: return null
        val state = when (connection.connectionState) {
            ConnectionState.CONNECTED -> LinkState.CONNECTED
            else -> LinkState.CONNECTING
        }
        return StreamLink(
            state = state,
            // The library sets its state just before telling listeners; until then this is a new attempt
            sinceMs = if (mqttState == ConnectionState.CONNECTING) mqttConnectingSinceMs else clock(),
            everConnected = connection.hasConnectedOnce(),
            failure = connection.lastFailure?.toLinkFailure(),
            waiting = connection.pendingMessageCount,
            dropping = mqttDropping(),
        )
    }

    private fun grpcLink(): StreamLink {
        val health = GrpcConnectionService.getQueueHealth()
        return StreamLink(
            state = if (GrpcConnectionService.getConnectedState() == ConnectionState.CONNECTED) LinkState.CONNECTED else LinkState.CONNECTING,
            sinceMs = health.connectingSinceMs,
            everConnected = health.everConnected,
            failure = GrpcConnectionService.getLastFailure()?.let { LinkFailure.UNREACHABLE },
            waiting = health.pendingCount(),
            dropping = health.isDropping(),
        )
    }

    /** @return The link of a requested destination that gave up, or null if it didn't. */
    private fun failedLink(destination: Destination): StreamLink? {
        if (destination != Destination.MQTT) return null
        val connection = mqtt() ?: return null
        val failure = connection.lastFailure
        if (failure != ConnectionFailure.SIGN_IN_REJECTED && failure != ConnectionFailure.INVALID_SETTINGS) return null
        return StreamLink(
            state = LinkState.FAILED,
            everConnected = connection.hasConnectedOnce(),
            failure = failure.toLinkFailure(),
        )
    }

    private fun communityUpload(): CommunityUploadHealth? = autoUpload()?.let {
        CommunityUploadHealth(
            failures = it.consecutiveFailureCount,
            retryAtMs = it.retryAtMs,
            keyRejected = it.isOpenCelliDKeyRejected,
        )
    }

    private fun ConnectionFailure.toLinkFailure(): LinkFailure = when (this) {
        ConnectionFailure.SIGN_IN_REJECTED -> LinkFailure.SIGN_IN_REJECTED
        ConnectionFailure.UNREACHABLE -> LinkFailure.UNREACHABLE
        ConnectionFailure.INVALID_SETTINGS -> LinkFailure.INVALID_SETTINGS
    }
}
