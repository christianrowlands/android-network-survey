package com.craxiom.networksurvey.ui.survey

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.survey.engine.DestinationRuntime
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.status.ScoutOutage
import com.craxiom.networksurvey.survey.status.ScoutWifiStatus
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.survey.model.AttentionItemUi
import com.craxiom.networksurvey.ui.survey.model.AttentionPlateUi
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.ui.survey.model.LocationStatus
import com.craxiom.networksurvey.ui.survey.model.SurveyEvent
import com.craxiom.networksurvey.ui.survey.model.SurveyInputs
import com.craxiom.networksurvey.ui.survey.model.SurveyUiState
import com.craxiom.networksurvey.ui.survey.model.SurveyUiStateMapper
import com.craxiom.networksurvey.ui.theme.NsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Survey tab's NS Scout row, Wi-Fi tile and plates, built through the real
 * [SurveyUiStateMapper] and drawn by [SurveyScreenContent].
 */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class SurveyScoutContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : SurveyActions {
        override fun onOpenScout() {
            calls += "openScout"
        }

        override fun onUseScout() {
            calls += "useScout"
        }

        override fun onUsePhoneWifi() {
            calls += "usePhone"
        }

        override fun onFixItem(item: AttentionItemUi) {
            calls += "fix:${(item as AttentionItemUi.Scout).outage}"
        }
    }

    @Test
    fun neverPaired_showsNoScoutAnywhere() {
        show(state(ScoutState.NotPaired))
        assertEquals(0, compose.onAllNodesWithText("NS-", substring = true).fetchSemanticsNodes().size)
        compose.onNodeWithText(string(R.string.survey_type_wifi_description)).assertIsDisplayed()
    }

    @Test
    fun ready_connected_showsWordBandsAndPower_andTheWifiTileNamesTheScout() {
        show(state(connected(capturing = false)))
        compose.onNodeWithText(string(R.string.scout_word_connected)).assertIsDisplayed()
        compose.onNodeWithText("2.4 and 5 GHz · USB powered").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.survey_type_wifi_from_scout, NAME)).assertIsDisplayed()

        compose.onNodeWithText(NAME).performClick()
        assertEquals(listOf("openScout"), calls)
    }

    @Test
    fun recording_connected_showsChannelAndFramesHeard_andTheChipNamesTheScout() {
        show(state(connected(capturing = true), recording = true))
        compose.onNodeWithText("Ch 36 · hearing 74 frames/s").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.survey_chip_wifi_scout, NAME)).performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText(string(R.string.scout_detail_data_missed)).fetchSemanticsNodes().size)
    }

    @Test
    fun recording_dataMissed_saysSoUnderTheRow() {
        show(state(connected(capturing = true, dataMissed = true), recording = true))
        compose.onNodeWithText(string(R.string.scout_detail_data_missed)).assertIsDisplayed()
    }

    @Test
    fun phoneSource_rowStays_andOffersTheScoutBeforeStart() {
        show(state(ScoutState.NotInUse(SCOUT)))
        compose.onNodeWithText(string(R.string.scout_row_phone_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_row_phone_detail, NAME)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_action_use, NAME)).performClick()
        assertEquals(listOf("useScout"), calls)
        // Wi-Fi is the phone's again
        compose.onNodeWithText(string(R.string.survey_type_wifi_description)).assertIsDisplayed()
    }

    @Test
    fun phoneSource_whileRecording_isLocked() {
        show(state(ScoutState.NotInUse(SCOUT), recording = true))
        compose.onNodeWithText(string(R.string.scout_row_phone_title)).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText(string(R.string.scout_action_use, NAME)).fetchSemanticsNodes().size)
    }

    @Test
    fun ready_outOfRange_platesOffersPhoneWifiAndTheScoutPage() {
        show(state(ScoutState.OutOfRange(SCOUT, batteryMayBeEmpty = false)))
        compose.onNodeWithText(string(R.string.wifi_scout_not_connected_title, NAME)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_word_not_connected)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wifi_scout_action_use_phone)).performClick()
        compose.onNodeWithText(string(R.string.scout_action_scout_page)).performClick()
        assertEquals(listOf("usePhone", "openScout"), calls)
    }

    @Test
    fun ready_outOfRange_withoutWifiSelected_hasNoPlate() {
        val state = state(ScoutState.OutOfRange(SCOUT, batteryMayBeEmpty = false), types = setOf(RecordType.CELLULAR))
        assertTrue(state.plates.none { it is AttentionPlateUi.Scout })
    }

    @Test
    fun ready_connecting_isTheGrace_soNoPlate() {
        assertTrue(state(ScoutState.Connecting(SCOUT)).plates.isEmpty())
    }

    @Test
    fun ready_bluetoothOff_mergesBluetoothIntoOnePlate_withTurnOn() {
        show(state(ScoutState.BluetoothOff(SCOUT), types = setOf(RecordType.WIFI, RecordType.BLUETOOTH)))
        compose.onNodeWithText("Wi-Fi from $NAME and Bluetooth won't be recorded.").assertIsDisplayed()
        compose.onNodeWithText("Turn on Bluetooth").performClick()
        assertEquals(listOf("fix:BLUETOOTH_OFF"), calls)
    }

    @Test
    fun ready_bluetoothOff_namesTheScoutTypes_onThePlate() {
        show(state(ScoutState.BluetoothOff(SCOUT), types = setOf(RecordType.WIFI, RecordType.WIFI_CLIENTS, RecordType.WIFI_RAW)))
        compose.onNodeWithText("Wi-Fi from $NAME, Wi-Fi clients and Raw frames won't be recorded.").assertIsDisplayed()
    }

    @Test
    fun recording_outOfRange_getsItsOwnPlate_notALineInTheAttentionPlate() {
        val state = state(
            ScoutState.OutOfRange(SCOUT, batteryMayBeEmpty = false), recording = true,
            scoutWifi = ScoutWifiStatus(NAME, ScoutOutage.OUT_OF_RANGE),
        )
        val scoutPlate = state.plates.single { it is AttentionPlateUi.Scout } as AttentionPlateUi.Scout
        assertTrue(scoutPlate.recording)
        assertTrue(state.plates.none { it is AttentionPlateUi.NeedsAttention })

        show(state)
        compose.onNodeWithText(string(R.string.scout_item_out_of_range, NAME)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_plate_out_of_range_body)).assertIsDisplayed()
        // The source is locked while recording
        assertEquals(0, compose.onAllNodesWithText(string(R.string.wifi_scout_action_use_phone)).fetchSemanticsNodes().size)
        compose.onNodeWithText(string(R.string.scout_word_out_of_range)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun snackbar_phoneSource_offersUndo() {
        val resources = RuntimeEnvironment.getApplication().resources
        assertEquals(string(R.string.scout_snackbar_phone), SurveyEventMessages.message(resources, SurveyEvent.WifiFromPhone))
        assertEquals(string(R.string.mqtt_undo), SurveyEventMessages.actionLabel(resources, SurveyEvent.WifiFromPhone))
        assertNull(SurveyEventMessages.actionLabel(resources, SurveyEvent.WifiFromScout(NAME)))
    }

    private fun show(state: SurveyUiState) {
        compose.setContent {
            NsTheme(darkTheme = false) { SurveyScreenContent(state, actions, { _, _ -> state }, SnackbarHostState()) }
        }
    }

    private fun state(
        scout: ScoutState,
        recording: Boolean = false,
        types: Set<RecordType> = setOf(RecordType.CELLULAR, RecordType.WIFI),
        scoutWifi: ScoutWifiStatus? = null,
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
                userSetup = SurveySetup.custom(types, setOf(Destination.LOG_FILES)),
                autoIncludePhoneState = false,
                destinationInfo = DestinationInfo(),
                location = LocationStatus.Fixed(4),
                nowMs = NOW,
                scout = scout,
                scoutWifi = scoutWifi,
            )
        )
    }

    private fun connected(capturing: Boolean, dataMissed: Boolean = false) =
        ScoutState.Connected(SCOUT, batteryPercent = null, channel = 36, framesPerSecond = 74, capturing = capturing, dataMissed = dataMissed)

    private companion object {
        const val NAME = "NS-A1B2"
        const val NOW = 1_800_000_000_000L
        val SCOUT = PairedScout(
            id = "NS-02000000A1B2", address = "02:00:00:00:A1:B2", shortId = "A1B2", boardModel = 2, modelName = "",
            firmwareVersion = "0.1.0", protocolMajor = 1, bands = listOf(1, 2), pairedAtMs = 0L,
        )
    }
}
