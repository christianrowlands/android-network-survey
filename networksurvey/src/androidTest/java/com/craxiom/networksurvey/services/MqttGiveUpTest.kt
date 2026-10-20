package com.craxiom.networksurvey.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.destinations.EmbeddedMqttBroker
import com.craxiom.networksurvey.destinations.LOOPBACK_HOST
import com.craxiom.networksurvey.destinations.awaitCondition
import com.craxiom.networksurvey.mqtt.profile.MqttBrokerProfile
import com.craxiom.networksurvey.mqtt.profile.MqttBrokerProfileStore
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An MQTT connection the client gives up on (the broker rejects the sign-in, or the settings are
 * invalid) must take its listeners with it, so the scanners it started stop and the wake lock is
 * released, even though the survey service never disconnected it on purpose.
 */
@RunWith(AndroidJUnit4::class)
class MqttGiveUpTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @get:Rule
    val permissionRule = DestinationTestSupport.permissionRule()

    /** A broker that refuses every sign-in. */
    private val broker = EmbeddedMqttBroker(REJECTING_BROKER_PORT, rejectSignIn = true)
    private lateinit var service: NetworkSurveyService

    @Before
    fun setUp() {
        broker.start()
        DestinationTestSupport.resetPreferences {
            it.putString(MqttConstants.PROPERTY_MQTT_CLIENT_ID, CLIENT_ID)
            // Device status on, so its listener is registered and has to be released too
            it.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, true)
        }
        service = DestinationTestSupport.bindService(serviceRule)
    }

    @After
    fun tearDown() {
        DestinationTestSupport.stop(service)
        broker.stop()
    }

    @Test
    fun rejectedSignIn_releasesTheListenersAndTheWakeLock() {
        DestinationTestSupport.saveBrokerProfile(broker.port)

        DestinationTestSupport.start(service, STREAMED_TYPES, setOf(Destination.MQTT))

        assertGaveUpAndReleasedEverything()
    }

    @Test
    fun invalidSettings_releaseTheListenersAndTheWakeLock() {
        // The client refuses a topic prefix with a wildcard before connecting, so it gives up inside
        // the connect call, before the service has registered the stream's listeners.
        MqttBrokerProfileStore.create(DestinationTestSupport.context()).save(
            MqttBrokerProfile(
                id = MqttBrokerProfileStore.newId(),
                name = "Invalid broker",
                host = LOOPBACK_HOST,
                port = broker.port,
                tlsEnabled = false,
                topicPrefix = INVALID_TOPIC_PREFIX,
            ),
            username = "",
            password = "",
            select = true,
        )

        DestinationTestSupport.start(service, STREAMED_TYPES, setOf(Destination.MQTT))

        assertGaveUpAndReleasedEverything()
    }

    private fun assertGaveUpAndReleasedEverything() {
        assertWithMessage("MQTT gave up").that(awaitCondition {
            service.mqttConnectionState == ConnectionState.DISCONNECTED
        }).isTrue()
        assertWithMessage("no listener is left registered").that(awaitCondition {
            !service.surveyRecordProcessorForTest.isBeingUsed
        }).isTrue()
        assertWithMessage("the wake lock is released").that(awaitCondition { !service.isWakeLockHeldForTest }).isTrue()
        assertThat(service.mqttStreamTypes).isEmpty()
        assertThat(service.isMissionSessionActive).isFalse()
    }

    private companion object {
        const val CLIENT_ID = "ns-give-up-test"
        const val REJECTING_BROKER_PORT = 18833
        const val INVALID_TOPIC_PREFIX = "ns/#"

        val STREAMED_TYPES = setOf(RecordType.CELLULAR, RecordType.PHONE_STATE, RecordType.WIFI, RecordType.GNSS)
    }
}
