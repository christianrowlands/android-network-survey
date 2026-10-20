package com.craxiom.networksurvey.ui.survey

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.engine.DestinationRuntime
import com.craxiom.networksurvey.survey.engine.LastSession
import com.craxiom.networksurvey.survey.engine.SurveyOrigin
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.status.CommunityQueue
import com.craxiom.networksurvey.survey.status.CommunityUploadFailure
import com.craxiom.networksurvey.survey.status.DeliveryQueues
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.survey.components.labelRes
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.ui.survey.model.LocationStatus
import com.craxiom.networksurvey.ui.survey.model.SurveyInputs
import com.craxiom.networksurvey.ui.survey.model.SurveyUiState
import com.craxiom.networksurvey.ui.survey.model.SurveyUiStateMapper
import com.craxiom.networksurvey.ui.theme.NsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Survey screen against what actually runs or ran: Start and Stop while a command is on its
 * way, the Change sheet for a survey another app started, a survey that only sends device status,
 * the last Mission ID after Stop, and a failed community upload.
 */
@RunWith(RobolectricTestRunner::class)
// Espresso needs API 35 or lower under Robolectric (see SurveyScreenContentTest)
@Config(sdk = [35])
class SurveyRunningSurveyContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = Actions()

    private val info = DestinationInfo(
        mqttConfigured = true,
        mqttBrokerLabel = "broker.example.net",
        communityTargets = setOf(RecordType.CELLULAR, RecordType.WIFI),
        openCelliDSelected = true,
        beaconDbSelected = true,
    )

    // A command on its way

    @Test
    fun whileAStartIsOnItsWay_startIsDisabled_andKeepsItsLabel() {
        show(state().copy(commandInFlight = true))
        compose.onNodeWithText(string(R.string.survey_start)).assertIsNotEnabled().performClick()
        assertEquals(emptyList<String>(), actions.calls)
    }

    @Test
    fun whileAStopIsOnItsWay_stopIsDisabled_andKeepsItsLabel() {
        val runtime = running(Destination.LOG_FILES to setOf(RecordType.CELLULAR))
        show(state(runtime = runtime).copy(commandInFlight = true))
        compose.onNodeWithText(string(R.string.survey_stop)).assertIsNotEnabled().performClick()
        assertEquals(emptyList<String>(), actions.calls)
    }

    @Test
    fun onceTheCommandIsDone_stopIsEnabledAgain() {
        show(state(runtime = running(Destination.LOG_FILES to setOf(RecordType.CELLULAR))))
        compose.onNodeWithText(string(R.string.survey_stop)).assertIsEnabled()
    }

    // The Change sheet

    @Test
    fun aSurveyStartedByAnotherApp_opensTheChangeSheetWithThatAppsSetup() {
        val intent = SurveySetup.custom(setOf(RecordType.WIFI, RecordType.GNSS), setOf(Destination.LOG_FILES))
        val runtime = running(Destination.LOG_FILES to intent.recordTypes).copy(requested = intent, startOrigin = SurveyOrigin.INTENT)
        // The working setup is something else entirely
        show(state(types = setOf(RecordType.BLUETOOTH, RecordType.CDR), destinations = setOf(Destination.MQTT), runtime = runtime))

        compose.onNodeWithText(string(R.string.survey_change)).performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.survey_apply_now)).performClick()

        assertEquals(listOf("apply:${intent.recordTypes}:${intent.destinations}"), actions.calls)
    }

    // Device status only

    @Test
    fun mqttWithNoRecordTypes_saysDeviceStatusOnly_andTheTableHasADeviceStatusRow() {
        val runtime = running(Destination.MQTT to emptySet())
            .copy(requested = SurveySetup.custom(emptySet(), setOf(Destination.MQTT)), startOrigin = SurveyOrigin.ORGANIZATION)
        show(state(runtime = runtime))

        // The hero and the setup panel both say what goes out instead of six chips that are all off
        assertEquals(2, compose.onAllNodesWithText(string(R.string.survey_device_status_only)).fetchSemanticsNodes().size)
        compose.onNodeWithText(string(R.string.survey_what_goes_where)).performScrollTo().performClick()
        val row = string(R.string.survey_routing_row, string(R.string.survey_routing_device_status), string(Destination.MQTT.labelRes))
        compose.onNodeWithContentDescription(row).assertIsDisplayed()
    }

    // After Stop

    @Test
    fun afterStop_theLastMissionIdShows_andCopies() {
        val stopped = SurveyRuntimeState(lastSession = LastSession(120_000L, 40L, NOW - 5_000L, MISSION_ID))
        show(state(runtime = stopped))

        compose.onNodeWithText(MISSION_ID).assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.mission_id_copy_content_description)).performClick()
        assertEquals(listOf("copy:$MISSION_ID"), actions.calls)
    }

    // A failed community upload

    @Test
    fun aFailedCommunityUpload_saysSoInStillToDo_andOpensTheCommunityPage() {
        val queues = DeliveryQueues(community = CommunityQueue(pending = 30, lastFailure = CommunityUploadFailure(INVALID_KEY)))
        show(state(queues = queues))

        compose.onNodeWithText(string(R.string.survey_status_upload_failed_detail, INVALID_KEY)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.survey_action_open)).performClick()
        assertEquals(listOf("open:COMMUNITY"), actions.calls)
    }

    private fun show(state: SurveyUiState) {
        compose.setContent {
            NsTheme(darkTheme = false) { SurveyScreenContent(state, actions, { types, destinations -> preview(types, destinations, state) }, SnackbarHostState()) }
        }
    }

    /** The screen as the Change sheet's draft would make it, as the view model builds it. */
    private fun preview(types: Set<RecordType>, destinations: Set<Destination>, current: SurveyUiState): SurveyUiState =
        state(types, destinations).copy(recording = current.recording)

    private fun state(
        types: Set<RecordType> = setOf(RecordType.CELLULAR, RecordType.WIFI),
        destinations: Set<Destination> = setOf(Destination.LOG_FILES),
        runtime: SurveyRuntimeState = SurveyRuntimeState(),
        queues: DeliveryQueues = DeliveryQueues(),
    ): SurveyUiState = SurveyUiStateMapper.map(
        SurveyInputs(
            runtime = runtime,
            userSetup = SurveySetup.custom(types, destinations),
            autoIncludePhoneState = true,
            destinationInfo = info,
            location = LocationStatus.Fixed(4),
            nowMs = NOW,
            queues = queues,
        )
    )

    private fun running(vararg destinations: Pair<Destination, Set<RecordType>>) = SurveyRuntimeState(
        destinations = destinations.associate { (destination, types) -> destination to DestinationRuntime(types) },
        missionId = MISSION_ID,
        missionSessionActive = true,
        sessionStartTimeMs = NOW - 60_000L,
    )

    /** Records which actions the screen asked for, in order. */
    private class Actions : SurveyActions {
        val calls = mutableListOf<String>()
        override fun onStart() { calls += "start" }
        override fun onStop() { calls += "stop" }
        override fun onApplyDraft(types: Set<RecordType>, destinations: Set<Destination>) { calls += "apply:$types:$destinations" }
        override fun onCopyMissionId(missionId: String) { calls += "copy:$missionId" }
        override fun onOpenDestination(destination: Destination) { calls += "open:$destination" }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MISSION_ID = "NS-1"
        const val INVALID_KEY = "Invalid API key"
    }
}
