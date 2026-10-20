package com.craxiom.networksurvey.ui.survey

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.engine.DestinationResult
import com.craxiom.networksurvey.survey.engine.DestinationRuntime
import com.craxiom.networksurvey.survey.engine.LinkState
import com.craxiom.networksurvey.survey.engine.StreamLink
import com.craxiom.networksurvey.survey.engine.SkipReason
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.engine.SurveyStartResult
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.status.DeliveryQueues
import com.craxiom.networksurvey.survey.status.NsAnalyticsQueue
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.survey.model.AttentionItemUi
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.ui.survey.model.LocationStatus
import com.craxiom.networksurvey.ui.survey.model.SurveyInputs
import com.craxiom.networksurvey.ui.survey.model.SurveyUiState
import com.craxiom.networksurvey.ui.survey.model.SurveyUiStateMapper
import com.craxiom.networksurvey.ui.theme.NsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Survey screen's states and interactions, on the stateless [SurveyScreenContent]. States are
 * built through the real [SurveyUiStateMapper], so these also cover the rules end to end.
 */
@RunWith(RobolectricTestRunner::class)
// The Compose test rule drives input through Espresso, which reflects on
// InputManager.getInstance(); that method is gone on API 37 (the module-wide Robolectric pin), so
// this screen test runs on API 35 until Espresso catches up.
@Config(sdk = [35])
class SurveyScreenContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()

    private val ready = DestinationInfo(
        nsAnalyticsRegistered = true,
        workspaceName = "Acme",
        mqttConfigured = false,
        communityTargets = setOf(RecordType.CELLULAR, RecordType.WIFI),
        openCelliDSelected = true,
        beaconDbSelected = true,
    )

    // Before the service answers

    @Test
    fun unknown_drawsNeitherStartNorStop_soARunningSurveyNeverLooksStopped() {
        show(SurveyUiState.UNKNOWN)
        listOf("Start survey", "Stop survey", "What should it survey?").forEach {
            assertEquals(it, 0, compose.onAllNodesWithText(it).fetchSemanticsNodes().size)
        }
    }

    // Ready

    @Test
    fun ready_showsBothQuestions_andStartRuns() {
        show(state())
        compose.onNodeWithText("What should it survey?").assertIsDisplayed()
        compose.onNodeWithText("Where should the data go?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start survey").assertIsEnabled().performClick()
        assertEquals(listOf("start"), actions.calls)
    }

    @Test
    fun nsAnalyticsNotConnected_disablesStart_andOffersBothFixes() {
        show(state(destinations = setOf(Destination.NS_ANALYTICS), info = ready.copy(nsAnalyticsRegistered = false)))
        compose.onNodeWithText("Start survey").assertIsNotEnabled()
        compose.onNodeWithText("Connect NS Analytics or turn on Log files to start").assertIsDisplayed()
        compose.onNodeWithText("Use Log files").performClick()
        compose.onNodeWithText("Connect NS Analytics").performClick()
        assertEquals(listOf("useLogFiles", "connectNsAnalytics"), actions.calls)
    }

    @Test
    fun emptySelection_saysWhatIsMissing() {
        show(state(types = emptySet()))
        compose.onNodeWithText("Choose at least one thing to survey").assertIsDisplayed()
        compose.onNodeWithText("Start survey").assertIsNotEnabled()
    }

    @Test
    fun tiles_toggle_andIncludedPhoneStateExplainsItself() {
        show(state())
        compose.onNodeWithText("Bluetooth").performClick()
        compose.onNodeWithText("Phone state").performClick()
        assertEquals(listOf("toggleType:BLUETOOTH", "explainPhoneState"), actions.calls)
    }

    @Test
    fun destinationRows_openTheirPage_andTheSwitchToggles() {
        show(state())
        // MQTT and gRPC both need setting up; MQTT is listed first.
        compose.onAllNodesWithText("Set up")[0].performScrollTo().performClick()
        compose.onNodeWithText("Log files").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Community databases").performScrollTo().performClick()
        assertEquals(listOf("open:MQTT", "open:LOG_FILES", "toggleDest:COMMUNITY"), actions.calls)
    }

    @Test
    fun lockedDestinationRow_opensItsPage_andItsSwitchExplainsTheLock() {
        show(state(restrictions = mapOf(SurveyMdmKeys.DESTINATION_LOG_FILES to SurveyMdmKeys.VALUE_ON)))
        compose.onNodeWithText("Log files").performScrollTo().performClick()
        assertEquals(listOf("open:LOG_FILES"), actions.calls)
        compose.onNodeWithContentDescription("Log files", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Your organization chose this for this phone. Ask your administrator to change it.")
            .assertIsDisplayed()
        assertEquals(listOf("open:LOG_FILES"), actions.calls)
    }

    @Test
    fun editList_opensTheShowOnSurveyScreenSheet() {
        show(state())
        compose.onNodeWithText("Edit list").performScrollTo().performClick()
        compose.onNodeWithText("Show on Survey screen").assertIsDisplayed()
    }

    // Organization

    @Test
    fun lockedTile_opensTheLockSheet_whichLeadsToTheOverride() {
        show(state(restrictions = mapOf(SurveyMdmKeys.SURVEY_CELLULAR to SurveyMdmKeys.VALUE_ON)))
        compose.onNodeWithText("Cellular").performClick()
        compose.onNodeWithText("Your organization chose this for this phone. Ask your administrator to change it.")
            .assertIsDisplayed()
        compose.onNodeWithText("Use my own settings").performClick()
        compose.onNodeWithText("Use your own settings?").assertIsDisplayed()
        compose.onAllNodesWithText("Use my own settings")[0].performClick()
        assertEquals(listOf("useOwnSettings"), actions.calls)
    }

    @Test
    fun seeAll_inTheLockSheetAndOnTheOrgLine_opensOrganizationSettings() {
        show(state(restrictions = mapOf(SurveyMdmKeys.SURVEY_CELLULAR to SurveyMdmKeys.VALUE_ON)))
        compose.onNodeWithText("Cellular").performClick()
        // The org line has its own "See all"; the sheet's is the second one.
        compose.onAllNodesWithText("See all")[1].performClick()
        compose.onAllNodesWithText("See all")[0].performClick()
        assertEquals(listOf("organizationSettings", "organizationSettings"), actions.calls)
    }

    @Test
    fun mqttWithBrokersButNoneSelected_asksToChooseOne() {
        show(state(info = DestinationInfo(mqttBrokersSavedNoneSelected = true)))
        compose.onNodeWithText("Choose a broker").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Choose").performScrollTo().performClick()
        assertEquals(listOf("open:MQTT"), actions.calls)
    }

    @Test
    fun overrideLine_restoresAfterConfirming() {
        show(state(restrictions = mapOf(SurveyMdmKeys.SURVEY_CELLULAR to SurveyMdmKeys.VALUE_ON), overrideOn = true))
        compose.onNodeWithText("Using your own settings, not your organization's").assertIsDisplayed()
        compose.onNodeWithText("Restore").performClick()
        compose.onNodeWithText("Restore your organization's settings?").assertIsDisplayed()
        compose.onAllNodesWithText("Restore")[1].performClick()
        assertEquals(listOf("restore"), actions.calls)
    }

    @Test
    fun managedStop_asksFirst_unmanagedStopDoesNot() {
        show(state(runtime = recording(), restrictions = mapOf(SurveyMdmKeys.AUTO_START_SURVEY to SurveyMdmKeys.VALUE_ON)))
        compose.onNodeWithText("Stop survey").performClick()
        compose.onNodeWithText("It starts again the next time this phone restarts.").assertIsDisplayed()
        assertTrue(actions.calls.isEmpty())
        compose.onAllNodesWithText("Stop survey")[1].performClick()
        assertEquals(listOf("stop"), actions.calls)
    }

    @Test
    fun unmanagedStop_stopsRightAway() {
        show(state(runtime = recording()))
        compose.onNodeWithText("Stop survey").performClick()
        assertEquals(listOf("stop"), actions.calls)
    }

    // Recording

    @Test
    fun recording_showsTheTimer_missionId_records_andDestinations() {
        show(state(runtime = recording()))
        compose.onNodeWithText("00:01:00").assertIsDisplayed()
        compose.onNodeWithText("NS-1").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.survey_running_heading)).assertIsDisplayed()
        compose.onNodeWithText("Records").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Saving").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun batteryPause_showsThePlate() {
        show(state(runtime = recording().copy(pausedForBattery = true, batteryLevel = 14, batteryThresholdPercent = 15)))
        compose.onNodeWithText("Paused: battery at 14%").assertIsDisplayed()
        compose.onNodeWithText("Change the threshold").performClick()
        assertEquals(listOf("batteryThreshold"), actions.calls)
    }

    @Test
    fun partialStart_listsEachProblemWithItsFix() {
        val result = SurveyStartResult(
            destinations = mapOf(
                Destination.LOG_FILES to DestinationResult.Started(setOf(RecordType.CELLULAR)),
                Destination.MQTT to DestinationResult.Skipped(SkipReason.NOT_CONFIGURED),
            ),
            recordTypeProblems = mapOf(RecordType.WIFI to SkipReason.RADIO_OFF),
            recording = true,
        )
        val running = recording().copy(
            destinations = mapOf(Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.CELLULAR))),
            lastResult = result,
        )
        show(state(destinations = setOf(Destination.LOG_FILES, Destination.MQTT), runtime = running, info = ready.copy(mqttConfigured = true)))
        compose.onNodeWithText("2 things need attention").assertIsDisplayed()
        compose.onNodeWithText("Turn on Wi-Fi").performClick()
        assertEquals(listOf("fix:Type(type=WIFI, reason=RADIO_OFF)"), actions.calls)
    }

    @Test
    fun changeSheet_appliesTheDraft() {
        show(state(runtime = recording()))
        compose.onNodeWithText("Change").performClick()
        compose.onNodeWithText("Change setup").assertIsDisplayed()
        compose.onNodeWithText("Apply now").performClick()
        assertEquals(listOf("apply"), actions.calls)
    }

    @Test
    fun changeSheet_removingNsAnalytics_saysQueuedRecordsStillUpload() {
        showNsAnalyticsRemovedInTheChangeSheet(autoUpload = true)
        compose.onNodeWithText(string(R.string.survey_remove_ns_analytics_note)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun changeSheet_removingNsAnalytics_withoutAutoUpload_saysTheyStayOnThePhone() {
        showNsAnalyticsRemovedInTheChangeSheet(autoUpload = false)
        compose.onNodeWithText(string(R.string.survey_remove_ns_analytics_note_manual)).performScrollTo().assertIsDisplayed()
    }

    private fun showNsAnalyticsRemovedInTheChangeSheet(autoUpload: Boolean) {
        show(
            state(
                destinations = setOf(Destination.LOG_FILES, Destination.NS_ANALYTICS),
                info = ready.copy(nsAnalyticsAutoUpload = autoUpload),
                runtime = recording(),
            )
        )
        compose.onNodeWithText("Change").performClick()
        compose.onNodeWithContentDescription("NS Analytics").performScrollTo().performClick()
    }

    @Test
    fun largeFonts_stillShowStart() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT_SCALE)) {
                NsTheme { SurveyScreenContent(state(), actions, { _, _ -> state() }, SnackbarHostState()) }
            }
        }
        compose.onNodeWithText("Start survey").assertIsDisplayed()
    }

    @Test
    fun stillToDo_listsWaitingRecords_andUploadNowUploads() {
        val queues = DeliveryQueues(nsAnalytics = NsAnalyticsQueue(pending = 312))
        show(state(info = ready.copy(nsAnalyticsAutoUpload = false), queues = queues))

        compose.onNodeWithText("Still to do").assertIsDisplayed()
        compose.onNodeWithText("312 records for NS Analytics").assertIsDisplayed()
        compose.onNodeWithText("Automatic upload is off").assertIsDisplayed()
        compose.onNodeWithText("Upload now").performClick()
        assertEquals(listOf("uploadNow:NS_ANALYTICS"), actions.calls)
    }

    @Test
    fun whatGoesWhere_opensFromReady_andFlagsWhatNoDestinationTakes() {
        show(state(types = setOf(RecordType.CELLULAR, RecordType.CDR), destinations = setOf(Destination.NS_ANALYTICS)))

        compose.onNodeWithText("Not sent anywhere").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("What goes where").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Call records, not sent anywhere").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cellular, sent to NS Analytics").assertIsDisplayed()
        compose.onNodeWithText("Log files takes every record type.", substring = true).assertIsDisplayed()
    }

    @Test
    fun recording_aNotSentChipOpensTheTable() {
        val runtime = recording().copy(
            destinations = mapOf(Destination.NS_ANALYTICS to DestinationRuntime(setOf(RecordType.CELLULAR))),
            requested = SurveySetup.custom(setOf(RecordType.CELLULAR, RecordType.CDR), setOf(Destination.NS_ANALYTICS)),
        )
        show(state(runtime = runtime))

        compose.onNodeWithText("Call records · not sent").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Call records, not sent anywhere").assertIsDisplayed()
    }

    @Test
    fun recording_aStreamThatCantConnect_raisesThePlate_whichOpensItsPage() {
        val runtime = recording().copy(
            destinations = mapOf(
                Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.CELLULAR)),
                Destination.MQTT to DestinationRuntime(
                    setOf(RecordType.CELLULAR),
                    link = StreamLink(LinkState.CONNECTING, sinceMs = NOW - 30_000L, waiting = 40),
                ),
            ),
        )
        show(state(runtime = runtime))

        compose.onNodeWithText("MQTT stream can't connect").assertIsDisplayed()
        compose.onNodeWithText("Can't connect").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open").performScrollTo().performClick()
        assertTrue(actions.calls.single().startsWith("fix:Delivery(destination=MQTT"))
    }

    private fun show(state: SurveyUiState) {
        compose.setContent {
            NsTheme(darkTheme = false) { SurveyScreenContent(state, actions, { _, _ -> state }, SnackbarHostState()) }
        }
    }

    private fun state(
        types: Set<RecordType> = setOf(RecordType.CELLULAR, RecordType.WIFI),
        destinations: Set<Destination> = setOf(Destination.LOG_FILES),
        info: DestinationInfo = ready,
        restrictions: Map<String, Any?> = emptyMap(),
        overrideOn: Boolean = false,
        runtime: SurveyRuntimeState = SurveyRuntimeState(),
        queues: DeliveryQueues = DeliveryQueues(),
    ): SurveyUiState = SurveyUiStateMapper.map(
        SurveyInputs(
            runtime = runtime.copy(policy = SurveyPolicy.evaluate(RestrictionsSnapshot(restrictions), overrideOn)),
            userSetup = SurveySetup.custom(types, destinations),
            autoIncludePhoneState = true,
            destinationInfo = info,
            location = LocationStatus.Fixed(4),
            counts = mapOf(RecordType.CELLULAR to 10L, RecordType.WIFI to 30L),
            lastRecordTimeMs = NOW - 2_000L,
            nowMs = NOW,
            queues = queues,
        )
    )

    private fun recording() = SurveyRuntimeState(
        destinations = mapOf(Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.CELLULAR, RecordType.WIFI))),
        missionId = "NS-1",
        missionSessionActive = true,
        sessionStartTimeMs = NOW - 60_000L,
    )

    /** Records which actions the screen asked for, in order. */
    private class RecordingActions : SurveyActions {
        val calls = mutableListOf<String>()
        override fun onStart() { calls += "start" }
        override fun onStop() { calls += "stop" }
        override fun onToggleRecordType(type: RecordType) { calls += "toggleType:$type" }
        override fun onToggleDestination(destination: Destination) { calls += "toggleDest:$destination" }
        override fun onOpenDestination(destination: Destination) { calls += "open:$destination" }
        override fun onExplainPhoneState() { calls += "explainPhoneState" }
        override fun onUseLogFiles() { calls += "useLogFiles" }
        override fun onConnectNsAnalytics() { calls += "connectNsAnalytics" }
        override fun onUseOwnSettings() { calls += "useOwnSettings" }
        override fun onRestoreOrganizationSettings() { calls += "restore" }
        override fun onApplyDraft(types: Set<RecordType>, destinations: Set<Destination>) { calls += "apply" }
        override fun onChangeBatteryThreshold() { calls += "batteryThreshold" }
        override fun onFixItem(item: AttentionItemUi) { calls += "fix:$item" }
        override fun onOpenOrganizationSettings() { calls += "organizationSettings" }
        override fun onUploadNow(destination: Destination) { calls += "uploadNow:$destination" }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val LARGE_FONT_SCALE = 1.3f
    }
}
