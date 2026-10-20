package com.craxiom.networksurvey.services

import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.destinations.FakeGrpcSurveyServer
import com.craxiom.networksurvey.destinations.FakeGrpcSurveyServer.Stream
import com.craxiom.networksurvey.destinations.LOOPBACK_HOST
import com.craxiom.networksurvey.destinations.awaitCondition
import com.craxiom.networksurvey.fragments.model.GrpcConnectionSettings
import com.craxiom.networksurvey.services.DestinationTestSupport.MARKER_SERIAL
import com.craxiom.networksurvey.survey.engine.SurveyOrigin
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * End to end tests of the gRPC destination: the survey orchestrator starts
 * [GrpcConnectionService], which streams to an in-process server on the phone's loopback. Saving a
 * new server (as the gRPC page does) moves a running stream without ending the survey.
 */
@RunWith(AndroidJUnit4::class)
class GrpcDestinationTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @get:Rule
    val permissionRule = DestinationTestSupport.permissionRule()

    private val server = FakeGrpcSurveyServer()
    private lateinit var service: NetworkSurveyService

    @Before
    fun setUp() {
        server.start()
        DestinationTestSupport.resetPreferences {
            it.putString(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_HOST, LOOPBACK_HOST)
            it.putInt(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_PORT, server.port)
            it.putString(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_DEVICE_NAME, DEVICE_NAME)
        }
        service = DestinationTestSupport.bindService(serviceRule)
    }

    @After
    fun tearDown() {
        DestinationTestSupport.stop(service)
        server.stop()
    }

    @Test
    fun surveyTypes_areStreamedToTheServer() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR, RecordType.WIFI), setOf(Destination.GRPC))
        awaitConnected()

        injectUntilReceived(Stream.LTE) { DestinationTestSupport.injectLte(service) }
        injectUntilReceived(Stream.WIFI_BEACON) { DestinationTestSupport.injectWifi(service) }
        assertThat(service.isMissionSessionActive).isTrue()
    }

    @Test
    fun liveTypeUpdate_opensAndClosesStreams() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.GRPC))
        awaitConnected()
        injectUntilReceived(Stream.LTE) { DestinationTestSupport.injectLte(service) }

        DestinationTestSupport.apply(service, setOf(RecordType.GNSS), setOf(Destination.GRPC))
        injectUntilReceived(Stream.GNSS) { DestinationTestSupport.injectGnss(service) }

        val lteBefore = markerCount(Stream.LTE)
        DestinationTestSupport.injectLte(service)
        Thread.sleep(QUIET_PERIOD_MS)
        assertThat(markerCount(Stream.LTE)).isEqualTo(lteBefore)
    }

    @Test
    fun serverRestart_reconnects() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.GRPC))
        awaitConnected()

        server.stop()
        // The app notices a dead server when a stream fails, so keep sending until it does.
        assertThat(awaitCondition(RECONNECT_TIMEOUT_MS) {
            DestinationTestSupport.injectLte(service)
            GrpcConnectionService.getConnectedState() != ConnectionState.CONNECTED
        }).isTrue()

        server.start()
        assertThat(awaitCondition(RECONNECT_TIMEOUT_MS) {
            GrpcConnectionService.getConnectedState() == ConnectionState.CONNECTED
        }).isTrue()
        injectUntilReceived(Stream.LTE) { DestinationTestSupport.injectLte(service) }
    }

    @Test
    fun deviceName_isSentOnRecords() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.GRPC))
        awaitConnected()

        injectUntilReceived(Stream.LTE) { DestinationTestSupport.injectLte(service) }
        assertThat(server.receivedOn(Stream.LTE).first { it.text.contains(MARKER_SERIAL) }.text).contains(DEVICE_NAME)
    }

    @Test
    fun newServerWhileStreaming_movesTheStream_keepsTheMissionId() {
        startGrpcSurvey(setOf(Destination.GRPC))
        awaitConnected()
        injectUntilReceived(Stream.LTE) { DestinationTestSupport.injectLte(service) }
        val missionId = service.rolledMissionId

        val second = FakeGrpcSurveyServer(SECOND_PORT).also { it.start() }
        try {
            saveServer(SECOND_PORT)
            assertThat(awaitCondition {
                DestinationTestSupport.injectLte(service)
                second.receivedOn(Stream.LTE).any { it.text.contains(MARKER_SERIAL) }
            }).isTrue()
            assertThat(service.rolledMissionId).isEqualTo(missionId)
            assertThat(service.isMissionSessionActive).isTrue()
        } finally {
            second.stop()
        }
    }

    @Test
    fun switchToDeadServer_grpcOnly_keepsTheSessionAndMissionId() {
        startGrpcSurvey(setOf(Destination.GRPC))
        awaitConnected()
        val missionId = service.rolledMissionId

        saveServer(SECOND_PORT)
        assertThat(awaitCondition { GrpcConnectionService.getLastFailure()?.port == SECOND_PORT }).isTrue()

        // It keeps trying instead of ending the survey.
        assertThat(GrpcConnectionService.getConnectedState()).isEqualTo(ConnectionState.CONNECTING)
        assertThat(service.isMissionSessionActive).isTrue()
        assertThat(service.rolledMissionId).isEqualTo(missionId)

        val second = FakeGrpcSurveyServer(SECOND_PORT).also { it.start() }
        try {
            assertThat(awaitCondition(RECONNECT_TIMEOUT_MS) {
                DestinationTestSupport.injectLte(service)
                second.receivedOn(Stream.LTE).any { it.text.contains(MARKER_SERIAL) }
            }).isTrue()
            assertThat(service.rolledMissionId).isEqualTo(missionId)
        } finally {
            second.stop()
        }
    }

    @Test
    fun saveRightAfterStop_doesNotReconnect() {
        startGrpcSurvey(setOf(Destination.GRPC))
        awaitConnected()

        val second = FakeGrpcSurveyServer(SECOND_PORT).also { it.start() }
        try {
            val stopped = service.stopSurvey(SurveyOrigin.USER)
            saveServer(SECOND_PORT)
            stopped.get(DestinationTestSupport.SURVEY_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            assertThat(awaitCondition { GrpcConnectionService.getConnectedState() == ConnectionState.DISCONNECTED }).isTrue()
            Thread.sleep(QUIET_PERIOD_MS)
            assertThat(GrpcConnectionService.getConnectedState()).isEqualTo(ConnectionState.DISCONNECTED)
            assertThat(second.handshakes.get()).isEqualTo(0)
            assertThat(service.isMissionSessionActive).isFalse()
        } finally {
            second.stop()
        }
    }

    @Test
    fun staleRetry_afterSwitch_doesNotOpenASecondConnection() {
        startGrpcSurvey(setOf(Destination.GRPC))
        awaitConnected()

        // The server goes away, so the connection is waiting to retry it.
        server.stop()
        assertThat(awaitCondition(RECONNECT_TIMEOUT_MS) {
            DestinationTestSupport.injectLte(service)
            GrpcConnectionService.getLastFailure()?.port == server.port
        }).isTrue()

        val second = FakeGrpcSurveyServer(SECOND_PORT).also { it.start() }
        try {
            saveServer(SECOND_PORT)
            assertThat(awaitCondition { GrpcConnectionService.getConnectedState() == ConnectionState.CONNECTED }).isTrue()

            // The old server comes back after the retry it scheduled was due; nothing reaches it.
            server.start()
            val before = server.handshakes.get()
            Thread.sleep(GrpcConnectionService.RECONNECTION_ATTEMPT_BACKOFF_TIME + QUIET_PERIOD_MS)
            assertThat(server.handshakes.get()).isEqualTo(before)
            assertThat(second.handshakes.get()).isEqualTo(1)
            injectUntilReceived(second, Stream.LTE) { DestinationTestSupport.injectLte(service) }
        } finally {
            second.stop()
        }
    }

    @Test
    fun fixedServer_restartsGrpcForTheRunningSurvey() {
        saveServer(SECOND_PORT)
        startGrpcSurvey(setOf(Destination.LOG_FILES, Destination.GRPC))
        // Nothing listens on the second port, so the first attempt fails and gRPC drops out.
        assertThat(awaitCondition { GrpcConnectionService.getLastFailure()?.port == SECOND_PORT }).isTrue()
        assertThat(awaitCondition { GrpcConnectionService.getConnectedState() == ConnectionState.DISCONNECTED }).isTrue()
        val missionId = service.rolledMissionId

        saveServer(server.port)

        awaitConnected()
        injectUntilReceived(Stream.LTE) { DestinationTestSupport.injectLte(service) }
        assertThat(service.rolledMissionId).isEqualTo(missionId)
    }

    /** Starts a Cellular survey to [destinations], stored as the user's setup the way Start does. */
    private fun startGrpcSurvey(destinations: Set<Destination>) {
        val types = setOf(RecordType.CELLULAR)
        SurveySetupStore(DestinationTestSupport.context()).setCurrentSetup(SurveySetup.custom(types, destinations))
        DestinationTestSupport.start(service, types, destinations)
    }

    /** Saves the gRPC server on the loopback [port] the way the gRPC page does. */
    private fun saveServer(port: Int) {
        GrpcConnectionSettings.save(
            PreferenceManager.getDefaultSharedPreferences(DestinationTestSupport.context()),
            GrpcConnectionSettings(LOOPBACK_HOST, port, DEVICE_NAME),
        )
    }

    private fun awaitConnected() {
        assertThat(awaitCondition {
            GrpcConnectionService.getConnectedState() == ConnectionState.CONNECTED
        }).isTrue()
    }

    private fun markerCount(stream: Stream) = server.receivedOn(stream).count { it.text.contains(MARKER_SERIAL) }

    /**
     * The per-type streams open asynchronously after CONNECTED, so a record sent too early is
     * dropped. Keep sending one per poll until the server has one.
     */
    private fun injectUntilReceived(stream: Stream, inject: () -> Unit) = injectUntilReceived(server, stream, inject)

    private fun injectUntilReceived(target: FakeGrpcSurveyServer, stream: Stream, inject: () -> Unit) {
        assertThat(awaitCondition {
            inject()
            target.receivedOn(stream).any { it.text.contains(MARKER_SERIAL) }
        }).isTrue()
    }

    private companion object {
        const val DEVICE_NAME = "ns-grpc-test-device"
        const val QUIET_PERIOD_MS = 2_000L
        const val RECONNECT_TIMEOUT_MS = 60_000L
        const val SECOND_PORT = FakeGrpcSurveyServer.DEFAULT_PORT + 1
    }
}
