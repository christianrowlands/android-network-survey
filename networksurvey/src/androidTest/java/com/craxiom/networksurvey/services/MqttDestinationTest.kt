package com.craxiom.networksurvey.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.destinations.EmbeddedMqttBroker
import com.craxiom.networksurvey.destinations.awaitCondition
import com.craxiom.networksurvey.mqtt.profile.MqttBrokerProfile
import com.craxiom.networksurvey.services.DestinationTestSupport.MARKER_SERIAL
import com.craxiom.networksurvey.survey.engine.DestinationResult
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end tests of the MQTT destination: the real [NetworkSurveyService] started through the
 * survey orchestrator, publishing to an in-process broker on the phone's loopback interface.
 *
 * The topic names are the NS Messaging wire contract and are pinned here as literals on purpose.
 */
@RunWith(AndroidJUnit4::class)
class MqttDestinationTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @get:Rule
    val permissionRule = DestinationTestSupport.permissionRule()

    private val broker = EmbeddedMqttBroker()
    private val secondBroker = EmbeddedMqttBroker(SECOND_BROKER_PORT)
    private lateinit var service: NetworkSurveyService
    private lateinit var profile: MqttBrokerProfile

    @Before
    fun setUp() {
        broker.start()
        configureBroker()
        service = DestinationTestSupport.bindService(serviceRule)
    }

    @After
    fun tearDown() {
        DestinationTestSupport.stop(service)
        broker.stop()
        secondBroker.stop()
    }

    @Test
    fun switchingTheSelectedBroker_movesTheStream_withoutRollingTheMissionId() {
        secondBroker.start()
        val second = DestinationTestSupport.saveBrokerProfile(secondBroker.port, select = false)
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.MQTT, Destination.LOG_FILES))
        awaitConnected()
        val missionId = service.rolledMissionId

        DestinationTestSupport.brokerProfileStore().setActive(second.id)

        assertWithMessage("the stream reaches the second broker").that(awaitCondition {
            DestinationTestSupport.injectLte(service)
            secondBroker.messagesOn(LTE_TOPIC).any { it.payload.contains(MARKER_SERIAL) }
        }).isTrue()
        assertThat(service.rolledMissionId).isEqualTo(missionId)
        assertThat(service.isMissionSessionActive).isTrue()
    }

    @Test
    fun removingTheSelectedBroker_stopsMqtt_andEndsTheSessionWhenMqttWasTheOnlyDestination() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.MQTT))
        awaitConnected()

        DestinationTestSupport.brokerProfileStore().remove(profile.id)

        assertThat(awaitCondition { service.mqttConnectionState == ConnectionState.DISCONNECTED }).isTrue()
        assertThat(awaitCondition { !service.isMissionSessionActive }).isTrue()
    }

    @Test
    fun selectingABroker_whileTheSurveyWantsMqtt_startsMqttForTheRunningSurvey() {
        // The survey asks for MQTT but no broker is selected, so only Log files starts.
        DestinationTestSupport.brokerProfileStore().setActive(null)
        val setup = SurveySetup.custom(setOf(RecordType.CELLULAR), setOf(Destination.MQTT, Destination.LOG_FILES))
        SurveySetupStore(DestinationTestSupport.context()).setCurrentSetup(setup)
        val result = DestinationTestSupport.start(service, setup.recordTypes, setup.destinations)
        assertThat(result.destinations[Destination.MQTT]).isInstanceOf(DestinationResult.Skipped::class.java)
        val missionId = service.rolledMissionId

        DestinationTestSupport.brokerProfileStore().setActive(profile.id)

        awaitConnected()
        assertThat(awaitCondition {
            DestinationTestSupport.injectLte(service)
            markerCountOn(LTE_TOPIC) > 0
        }).isTrue()
        assertThat(service.rolledMissionId).isEqualTo(missionId)
    }

    @Test
    fun eachRecordType_isPublishedOnItsTopic() {
        val result = DestinationTestSupport.start(service, ALL_STREAMED_TYPES, setOf(Destination.MQTT))

        assertThat(result.destinations[Destination.MQTT])
            .isAnyOf(DestinationResult.Started(ALL_STREAMED_TYPES), DestinationResult.Pending(ALL_STREAMED_TYPES))
        awaitConnected()

        // The connection drops records until it has finished connecting, a moment after it reports
        // CONNECTED, so keep sending one per poll until the topic has it.
        val injectors = mapOf<String, () -> Unit>(
            LTE_TOPIC to { DestinationTestSupport.injectLte(service) },
            WIFI_TOPIC to { DestinationTestSupport.injectWifi(service) },
            BLUETOOTH_TOPIC to { DestinationTestSupport.injectBluetooth(service) },
            GNSS_TOPIC to { DestinationTestSupport.injectGnss(service) },
            DEVICE_STATUS_TOPIC to { DestinationTestSupport.injectPhoneState(service) },
        )
        for ((topic, inject) in injectors) {
            assertWithMessage(topic).that(awaitCondition {
                inject()
                markerCountOn(topic) > 0
            }).isTrue()
        }
        assertThat(service.isMissionSessionActive).isTrue()
    }

    @Test
    fun streamedTypes_followTheSetupWhileRecording() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.MQTT))
        awaitConnected()

        DestinationTestSupport.injectWifi(service)
        DestinationTestSupport.injectLte(service)
        assertThat(awaitMarkerOn(LTE_TOPIC)).isTrue()
        assertThat(markerCountOn(WIFI_TOPIC)).isEqualTo(0)

        val missionId = service.rolledMissionId
        DestinationTestSupport.apply(service, setOf(RecordType.WIFI), setOf(Destination.MQTT))
        val lteBefore = markerCountOn(LTE_TOPIC)

        DestinationTestSupport.injectWifi(service)
        DestinationTestSupport.injectLte(service)
        assertThat(awaitMarkerOn(WIFI_TOPIC)).isTrue()
        assertThat(markerCountOn(LTE_TOPIC)).isEqualTo(lteBefore)
        // Changing what a running survey records never rolls the Mission ID.
        assertThat(service.rolledMissionId).isEqualTo(missionId)
    }

    @Test
    fun brokerRestart_reconnectsAndResumesPublishing() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.MQTT))
        awaitConnected()

        broker.stop()
        assertThat(awaitCondition { service.mqttConnectionState != ConnectionState.CONNECTED }).isTrue()

        broker.start()
        // HiveMQ's automatic reconnect backs off; give it longer than a normal wait.
        assertThat(awaitCondition(RECONNECT_TIMEOUT_MS) {
            service.mqttConnectionState == ConnectionState.CONNECTED
        }).isTrue()

        DestinationTestSupport.injectLte(service)
        assertThat(awaitMarkerOn(LTE_TOPIC)).isTrue()
    }

    @Test
    fun fullQueue_withMqttAsTheOnlyOutput_pausesScanning() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.MQTT))
        awaitConnected()

        broker.stop()
        repeat(QUEUE_OVERFLOW_RECORDS) { DestinationTestSupport.injectLte(service) }

        assertThat(awaitCondition { service.isPausedForQueueBackpressure }).isTrue()
        assertThat(service.isMqttDroppingMessages).isFalse()
    }

    @Test
    fun fullQueue_withAnotherOutput_dropsMqttMessagesAndKeepsScanning() {
        DestinationTestSupport.start(
            service, setOf(RecordType.CELLULAR), setOf(Destination.MQTT, Destination.LOG_FILES)
        )
        awaitConnected()

        broker.stop()
        repeat(QUEUE_OVERFLOW_RECORDS) { DestinationTestSupport.injectLte(service) }

        assertThat(awaitCondition { service.isMqttDroppingMessages }).isTrue()
        assertThat(service.isPausedForQueueBackpressure).isFalse()
        assertThat(service.surveyStateFlow.value.mqttDropping).isTrue()
    }

    private fun configureBroker() {
        DestinationTestSupport.resetPreferences {
            it.putString(MqttConstants.PROPERTY_MQTT_CLIENT_ID, CLIENT_ID)
            // Keep the periodic device status message off so the device status topic only carries
            // phone state records.
            it.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, false)
            it.putString(NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT, QUEUE_LIMIT.toString())
        }
        // The survey streams to the selected broker profile.
        profile = DestinationTestSupport.saveBrokerProfile(broker.port)
    }

    private fun awaitConnected() {
        assertThat(awaitCondition { service.mqttConnectionState == ConnectionState.CONNECTED }).isTrue()
    }

    private fun markerCountOn(topic: String) =
        broker.messagesOn(topic).count { it.payload.contains(MARKER_SERIAL) }

    private fun awaitMarkerOn(topic: String) = awaitCondition { markerCountOn(topic) > 0 }

    private companion object {
        const val CLIENT_ID = "ns-destination-test"
        const val SECOND_BROKER_PORT = 18832
        const val QUEUE_LIMIT = 5
        const val QUEUE_OVERFLOW_RECORDS = 50
        const val RECONNECT_TIMEOUT_MS = 60_000L

        const val LTE_TOPIC = "lte_message"
        const val WIFI_TOPIC = "80211_beacon_message"
        const val BLUETOOTH_TOPIC = "bluetooth_message"
        const val GNSS_TOPIC = "gnss_message"
        const val DEVICE_STATUS_TOPIC = "device_status_message"

        val ALL_STREAMED_TYPES = setOf(
            RecordType.CELLULAR, RecordType.PHONE_STATE, RecordType.WIFI,
            RecordType.BLUETOOTH, RecordType.GNSS
        )
    }
}
