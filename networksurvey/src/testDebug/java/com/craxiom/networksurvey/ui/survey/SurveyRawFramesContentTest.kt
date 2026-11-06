package com.craxiom.networksurvey.ui.survey

import android.text.format.Formatter
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.RawFramesProgress
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.survey.engine.DestinationRuntime
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.ui.common.CountFormat
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.ui.survey.model.LocationStatus
import com.craxiom.networksurvey.ui.survey.model.SurveyInputs
import com.craxiom.networksurvey.ui.survey.model.SurveyUiState
import com.craxiom.networksurvey.ui.survey.model.SurveyUiStateMapper
import com.craxiom.networksurvey.ui.survey.sheets.ChangeSetupSheet
import com.craxiom.networksurvey.ui.theme.NsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Raw frames on the Survey screen: the privacy question at every place it gets turned on (its tile,
 * the Change sheet, a saved setup), the tile's "Turn on Log files" hint, and the Records panel's
 * own raw line with its storage-full state.
 */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class SurveyRawFramesContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : SurveyActions {
        override fun onToggleRecordType(type: RecordType) {
            calls += "toggle:$type"
        }

        override fun onPickSetup(id: String) {
            calls += "pick:$id"
        }

        override fun onApplyDraft(types: Set<RecordType>, destinations: Set<Destination>) {
            calls += "apply:${types.sorted()}"
        }
    }

    private val title get() = string(R.string.survey_raw_confirm_title)
    private val confirm get() = string(R.string.survey_raw_confirm_action)
    private val rawName get() = string(R.string.survey_type_wifi_raw)

    @Test
    fun tile_asksFirst_andCancelLeavesItOff() {
        show(state())
        compose.onNodeWithText(rawName).performScrollTo().performClick()
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.survey_raw_confirm_body)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cancel)).performClick()
        assertTrue(calls.isEmpty())
        assertEquals(0, compose.onAllNodesWithText(title).fetchSemanticsNodes().size)

        compose.onNodeWithText(rawName).performScrollTo().performClick()
        compose.onNodeWithText(confirm).performClick()
        assertEquals(listOf("toggle:WIFI_RAW"), calls)
    }

    @Test
    fun tile_turningRawFramesOff_asksNothing() {
        show(state(types = setOf(RecordType.WIFI, RecordType.WIFI_RAW)))
        compose.onNodeWithText(rawName).performScrollTo().performClick()
        assertEquals(listOf("toggle:WIFI_RAW"), calls)
        assertEquals(0, compose.onAllNodesWithText(title).fetchSemanticsNodes().size)
    }

    @Test
    fun tile_withoutLogFiles_saysToTurnThemOn() {
        show(state(types = setOf(RecordType.WIFI, RecordType.WIFI_RAW), destinations = setOf(Destination.MQTT)))
        compose.onNodeWithText(string(R.string.survey_type_wifi_raw_needs_log_files)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tile_withLogFiles_describesItself() {
        show(state())
        compose.onNodeWithText(string(R.string.survey_type_wifi_raw_description)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun savedSetup_withRawFrames_asksFirst_andOneWithoutPicksAtOnce() {
        val raw = SurveySetup("raw", "Raw walk", setOf(RecordType.WIFI, RecordType.WIFI_RAW), setOf(Destination.LOG_FILES))
        val plain = SurveySetup("plain", "Plain walk", setOf(RecordType.CELLULAR), setOf(Destination.LOG_FILES))
        show(state(saved = listOf(raw, plain)))

        compose.onNodeWithContentDescription(string(R.string.survey_setups_heading)).performClick()
        compose.onNodeWithText("Raw walk").performClick()
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(confirm).performClick()
        assertEquals(listOf("pick:raw"), calls)

        compose.onNodeWithContentDescription(string(R.string.survey_setups_heading)).performClick()
        compose.onNodeWithText("Plain walk").performClick()
        assertEquals(listOf("pick:raw", "pick:plain"), calls)
    }

    @Test
    fun changeSheet_asksBeforeTheDraftHoldsRawFrames() {
        val start = SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES))
        compose.setContent {
            NsTheme(darkTheme = false) {
                ChangeSetupSheet(
                    initial = start,
                    preview = { types, destinations -> state(types = types, destinations = destinations) },
                    onApply = actions::onApplyDraft,
                    onOpenDestination = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText(rawName).performScrollTo().performClick()
        // The sheet has a Cancel of its own; the dialog's is the newest
        compose.onAllNodesWithText(string(R.string.cancel)).onLast().performClick()
        assertEquals(0, compose.onAllNodesWithText(title).fetchSemanticsNodes().size)
        compose.onNodeWithText(rawName).performScrollTo().performClick()
        compose.onNodeWithText(confirm).performClick()
        compose.onNodeWithText(string(R.string.survey_apply_now)).performClick()
        assertEquals(listOf("apply:${listOf(RecordType.WIFI, RecordType.WIFI_RAW)}"), calls)
    }

    @Test
    fun recording_rawFramesHaveTheirOwnLine_notALegendEntry() {
        val progress = RawFramesProgress(framesSaved = 12_480, bytesSaved = 38L * 1024 * 1024)
        show(state(types = setOf(RecordType.WIFI, RecordType.WIFI_RAW), recording = true, raw = progress))
        val size = Formatter.formatShortFileSize(RuntimeEnvironment.getApplication(), progress.bytesSaved)
        compose.onNodeWithText(string(R.string.survey_records_raw_saved, "12,480", size)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun recording_storageFull_saysSoInWords() {
        val progress = RawFramesProgress(framesSaved = 10, bytesSaved = 4_096, state = RawFramesProgress.State.STORAGE_FULL)
        show(state(types = setOf(RecordType.WIFI_RAW), recording = true, raw = progress))
        compose.onNodeWithText(string(R.string.survey_records_raw_storage_full_word)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.survey_records_raw_storage_full)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun recording_droppedFrames_haveTheirOwnLine() {
        val dropped = CountFormat.plural(RuntimeEnvironment.getApplication().resources, R.plurals.scout_missed_raw, 12L)
        val progress = RawFramesProgress(framesSaved = 10, bytesSaved = 4_096, framesDropped = 12)
        show(state(types = setOf(RecordType.WIFI_RAW), recording = true, raw = progress))
        compose.onNodeWithText(dropped).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun recording_noDroppedFrames_showsNoDroppedLine() {
        val dropped = CountFormat.plural(RuntimeEnvironment.getApplication().resources, R.plurals.scout_missed_raw, 0L)
        val progress = RawFramesProgress(framesSaved = 10, bytesSaved = 4_096)
        show(state(types = setOf(RecordType.WIFI_RAW), recording = true, raw = progress))
        assertEquals(0, compose.onAllNodesWithText(dropped).fetchSemanticsNodes().size)
    }

    private fun show(state: SurveyUiState) {
        compose.setContent {
            NsTheme(darkTheme = false) { SurveyScreenContent(state, actions, { _, _ -> state }, SnackbarHostState()) }
        }
    }

    private fun state(
        types: Set<RecordType> = setOf(RecordType.WIFI),
        destinations: Set<Destination> = setOf(Destination.LOG_FILES),
        recording: Boolean = false,
        raw: RawFramesProgress? = null,
        saved: List<SurveySetup> = emptyList(),
    ): SurveyUiState {
        val runtime = if (recording) {
            SurveyRuntimeState(
                destinations = mapOf(Destination.LOG_FILES to DestinationRuntime(types)),
                missionId = "NS-1",
                missionSessionActive = true,
                sessionStartTimeMs = NOW - 120_000L,
            )
        } else {
            SurveyRuntimeState()
        }
        return SurveyUiStateMapper.map(
            SurveyInputs(
                runtime = runtime,
                userSetup = SurveySetup.custom(types, destinations),
                autoIncludePhoneState = false,
                destinationInfo = DestinationInfo(mqttConfigured = true, mqttBrokerLabel = "broker"),
                location = LocationStatus.Fixed(4),
                nowMs = NOW,
                scout = ScoutState.Connected(SCOUT, batteryPercent = null, channel = 36, framesPerSecond = 74, capturing = recording, dataMissed = false),
                rawFrames = raw,
                savedSetups = saved,
            )
        )
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        val SCOUT = PairedScout(
            id = "NS-02000000A1B2", address = "02:00:00:00:A1:B2", shortId = "A1B2", boardModel = 2, modelName = "",
            firmwareVersion = "0.1.0", protocolMajor = 1, bands = listOf(1, 2), pairedAtMs = 0L,
        )
    }
}
