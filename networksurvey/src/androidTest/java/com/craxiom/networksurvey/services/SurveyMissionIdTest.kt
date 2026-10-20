package com.craxiom.networksurvey.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.destinations.EmbeddedMqttBroker
import com.craxiom.networksurvey.destinations.awaitCondition
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mission ID rules through the survey orchestrator, on the real service: one roll per survey no
 * matter how many destinations start (or finish connecting later), none for a Community-only
 * survey, and a fresh one after Stop. Also checks that Stop keeps the session's totals for the
 * Survey screen. Complements [NetworkSurveyServiceMissionIdTest], which covers
 * the individual toggles.
 */
@RunWith(AndroidJUnit4::class)
class SurveyMissionIdTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @get:Rule
    val permissionRule = DestinationTestSupport.permissionRule()

    private val broker = EmbeddedMqttBroker()
    private lateinit var service: NetworkSurveyService

    @Before
    fun setUp() {
        broker.start()
        DestinationTestSupport.resetPreferences {
            it.putString(MqttConstants.PROPERTY_MQTT_CLIENT_ID, CLIENT_ID)
            it.putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, true)
            it.putBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, true)
        }
        DestinationTestSupport.saveBrokerProfile(broker.port)
        service = DestinationTestSupport.bindService(serviceRule)
    }

    @After
    fun tearDown() {
        DestinationTestSupport.stop(service)
        broker.stop()
    }

    @Test
    fun multiDestinationStart_rollsOnce_evenAfterMqttFinishesConnecting() {
        DestinationTestSupport.start(
            service, setOf(RecordType.CELLULAR), setOf(Destination.LOG_FILES, Destination.MQTT)
        )
        val missionId = service.rolledMissionId
        assertThat(missionId).startsWith(NetworkSurveyConstants.MISSION_ID_PREFIX)

        assertThat(awaitCondition { service.mqttConnectionState == ConnectionState.CONNECTED }).isTrue()
        assertThat(service.rolledMissionId).isEqualTo(missionId)
        assertThat(service.surveyStateFlow.value.missionId).isEqualTo(missionId)
    }

    @Test
    fun communityOnlySurvey_neverRollsAMissionId() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.COMMUNITY))

        assertThat(service.isUploadScanningActive).isTrue()
        assertThat(service.rolledMissionId).isNull()
        assertThat(service.isMissionSessionActive).isFalse()
    }

    @Test
    fun stopThenStart_rollsANewMissionId() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.LOG_FILES))
        val first = service.rolledMissionId

        DestinationTestSupport.stop(service)
        assertThat(service.isMissionSessionActive).isFalse()
        // The Mission ID carries a timestamp with one second resolution, so a restart inside the
        // same second would produce the same string even though it rolled.
        Thread.sleep(MISSION_ID_RESOLUTION_MS)

        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.LOG_FILES))
        assertThat(service.rolledMissionId).isNotNull()
        assertThat(service.rolledMissionId).isNotEqualTo(first)
    }

    @Test
    fun stop_keepsTheLastSessionForTheSurveyScreen() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.LOG_FILES))
        repeat(RECORDS_IN_SESSION) { DestinationTestSupport.injectLte(service) }
        assertThat(service.sessionCounters.total()).isAtLeast(RECORDS_IN_SESSION.toLong())

        DestinationTestSupport.stop(service)

        val lastSession = service.surveyStateFlow.value.lastSession
        assertThat(lastSession).isNotNull()
        assertThat(lastSession!!.totalRecords).isAtLeast(RECORDS_IN_SESSION.toLong())
        assertThat(service.sessionCounters.total()).isEqualTo(0L)
    }

    private companion object {
        const val CLIENT_ID = "ns-mission-id-survey-test"
        const val MISSION_ID_RESOLUTION_MS = 1_100L
        const val RECORDS_IN_SESSION = 3
    }
}
