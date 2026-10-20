package com.craxiom.networksurvey.ui.survey

import com.craxiom.networksurvey.survey.engine.DestinationRuntime
import com.craxiom.networksurvey.survey.engine.LastSession
import com.craxiom.networksurvey.survey.engine.LinkState
import com.craxiom.networksurvey.survey.engine.StreamLink
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.ui.survey.model.LocationStatus
import com.craxiom.networksurvey.ui.survey.model.RoutingColumn
import com.craxiom.networksurvey.ui.survey.model.SurveyInputs
import com.craxiom.networksurvey.ui.survey.model.SurveyUiState
import com.craxiom.networksurvey.ui.survey.model.SurveyUiStateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Recording layout and the Ready line take from the running survey: the setup the Change
 * sheet starts from, a survey that only sends device status over MQTT, and the last Mission ID.
 */
class RecordingUiMapperTest {

    private val info = DestinationInfo(mqttConfigured = true, mqttBrokerLabel = "broker.example.net")

    private fun map(runtime: SurveyRuntimeState, working: SurveySetup = WORKING): SurveyUiState = SurveyUiStateMapper.map(
        SurveyInputs(
            runtime = runtime,
            userSetup = working,
            autoIncludePhoneState = true,
            destinationInfo = info,
            location = LocationStatus.Fixed(4),
            nowMs = NOW,
        )
    )

    // The Change sheet's seed

    @Test
    fun `the Change sheet starts from what another app asked for, not the working setup`() {
        val intent = SurveySetup.custom(setOf(RecordType.WIFI, RecordType.CDR), setOf(Destination.LOG_FILES, Destination.MQTT))
        val runtime = recording(Destination.LOG_FILES to setOf(RecordType.WIFI), Destination.MQTT to setOf(RecordType.WIFI))
            .copy(requested = intent)

        assertEquals(intent, map(runtime).recording!!.changeSeed)
    }

    @Test
    fun `without a request the Change sheet starts from what runs`() {
        val runtime = recording(Destination.LOG_FILES to setOf(RecordType.CELLULAR, RecordType.GNSS))

        val seed = map(runtime).recording!!.changeSeed
        assertEquals(setOf(RecordType.CELLULAR, RecordType.GNSS), seed.recordTypes)
        assertEquals(setOf(Destination.LOG_FILES), seed.destinations)
    }

    // Device status only (MQTT with no record types)

    @Test
    fun `MQTT running with no record types is a device status only survey, with a Device status row`() {
        val runtime = recording(Destination.MQTT to emptySet())
            .copy(requested = SurveySetup.custom(emptySet(), setOf(Destination.MQTT)))
        val state = map(runtime)

        assertTrue(state.recording!!.deviceStatusOnly)
        val table = state.routing!!
        assertTrue(table.mqttDeviceStatus)
        assertEquals(listOf(RoutingColumn.MQTT), table.columns)
        assertTrue(table.rows.isEmpty())
    }

    @Test
    fun `MQTT sending only device status next to Log files keeps the type chips, and the table adds Device status`() {
        val runtime = recording(Destination.LOG_FILES to setOf(RecordType.CDR), Destination.MQTT to emptySet())
            .copy(requested = SurveySetup.custom(setOf(RecordType.CDR), setOf(Destination.LOG_FILES, Destination.MQTT)))
        val state = map(runtime)

        assertFalse(state.recording!!.deviceStatusOnly)
        assertTrue(state.routing!!.mqttDeviceStatus)
        assertEquals(listOf(RecordType.CDR), state.routing!!.rows.map { it.type })
    }

    @Test
    fun `MQTT with record types has no Device status row`() {
        val state = map(recording(Destination.MQTT to setOf(RecordType.CELLULAR)))

        assertFalse(state.recording!!.deviceStatusOnly)
        assertFalse(state.routing!!.mqttDeviceStatus)
    }

    @Test
    fun `MQTT that gave up is not device status only`() {
        val runtime = recording(Destination.LOG_FILES to setOf(RecordType.CELLULAR))
            .let { it.copy(destinations = it.destinations + (Destination.MQTT to DestinationRuntime(emptySet(), link = StreamLink(LinkState.FAILED, sinceMs = NOW - 1_000L)))) }

        assertFalse(map(runtime).recording!!.deviceStatusOnly)
        assertFalse(map(runtime).routing!!.mqttDeviceStatus)
    }

    // The last Mission ID

    @Test
    fun `after Stop the last survey's Mission ID can still be copied`() {
        // The runtime no longer has the ID (the service was recreated); the last session keeps it
        val stopped = SurveyRuntimeState(lastSession = LastSession(120_000L, 40L, NOW - 5_000L, MISSION_ID))

        assertEquals(MISSION_ID, map(stopped).lastSession!!.missionId)
    }

    @Test
    fun `a last session with no Mission ID shows none`() {
        val stopped = SurveyRuntimeState(lastSession = LastSession(120_000L, 40L, NOW - 5_000L))

        assertNull(map(stopped).lastSession!!.missionId)
    }

    private fun recording(vararg destinations: Pair<Destination, Set<RecordType>>) = SurveyRuntimeState(
        destinations = destinations.associate { (destination, types) -> destination to DestinationRuntime(types) },
        missionId = MISSION_ID,
        missionSessionActive = true,
        sessionStartTimeMs = NOW - 60_000L,
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MISSION_ID = "NS-1"
        val WORKING = SurveySetup.custom(setOf(RecordType.CELLULAR, RecordType.BLUETOOTH, RecordType.GNSS), setOf(Destination.LOG_FILES))
    }
}
