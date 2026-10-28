package com.craxiom.networksurvey.ui.survey

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.craxiom.networksurvey.survey.engine.DestinationRuntime
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.ui.survey.model.LocationStatus
import com.craxiom.networksurvey.ui.survey.model.SurveyInputs
import com.craxiom.networksurvey.ui.survey.model.SurveyUiState
import com.craxiom.networksurvey.ui.survey.model.SurveyUiStateMapper
import com.craxiom.networksurvey.ui.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The saved setups on the Survey screen: the app bar button (Ready only) and the sheet it opens
 * (pick, Save current setup, star, rename, delete), on the stateless screen with states built by
 * the real mapper.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class SavedSetupsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()

    private val walk = SurveySetup("walk", "Warehouse walk", setOf(RecordType.CELLULAR, RecordType.WIFI), setOf(Destination.LOG_FILES))
    private val drive = SurveySetup("drive", "Drive", setOf(RecordType.GNSS), setOf(Destination.LOG_FILES), starred = true)

    @Test
    fun theForm_hasNoSetupsSection_andTheAppBarOpensThem() {
        show(state())
        assertEquals(0, compose.onAllNodesWithText("Warehouse walk").fetchSemanticsNodes().size)
        openSetups()
        compose.onNodeWithText("Warehouse walk").assertIsDisplayed()
        compose.onNodeWithText("Drive").assertIsDisplayed()
    }

    @Test
    fun nothingSaved_explainsSetups_andOffersSaveCurrent() {
        show(state(saved = emptyList()))
        openSetups()
        compose.onNodeWithText("Save what the Survey screen is set to now, to switch back to it with one tap.").assertIsDisplayed()
        compose.onNodeWithText(SAVE_CURRENT).assertIsEnabled().performClick()
        compose.onNodeWithText("Save setup").assertIsDisplayed()
    }

    @Test
    fun theSetupMatchingTheSelection_isInUse_andTappingAnotherPicksIt_andCloses() {
        show(state())
        openSetups()
        compose.onNode(hasText("Warehouse walk") and hasStateDescription("In use"), useUnmergedTree = false).assertExists()
        compose.onNodeWithText("Drive").performClick()
        assertEquals(listOf("pick:drive"), actions.calls)
        assertEquals(0, compose.onAllNodesWithText(SAVE_CURRENT).fetchSemanticsNodes().size)
    }

    @Test
    fun saveCurrent_isOff_whileNothingIsSelected() {
        show(state(types = emptySet()))
        openSetups()
        compose.onNodeWithText(SAVE_CURRENT).assertIsNotEnabled()
    }

    @Test
    fun saveSheet_savesTheTrimmedName_andANameInUseBecomesReplace() {
        show(state())
        openSetups()
        compose.onNodeWithText(SAVE_CURRENT).performClick()
        compose.onNodeWithText("Save setup").assertIsDisplayed()
        compose.onNode(hasText("Save") and !hasText("Save setup")).assertIsNotEnabled()

        compose.onNodeWithText("Name").performTextInput("drive")
        compose.onNodeWithText("Replaces the saved setup with this name").assertIsDisplayed()
        compose.onNodeWithText("Replace").assertIsEnabled()

        compose.onNodeWithText("drive").performTextReplacement("  Office  ")
        compose.onNode(hasText("Save") and !hasText("Save setup")).performClick()
        assertEquals(listOf("save:Office"), actions.calls)
        // Back on the setups sheet
        compose.onNodeWithText(SAVE_CURRENT).assertIsDisplayed()
    }

    @Test
    fun setupsSheet_stars() {
        show(state())
        openSetups()
        compose.onNodeWithContentDescription("Star Warehouse walk").performClick()
        assertEquals(listOf("star:walk:true"), actions.calls)
    }

    @Test
    fun setupsSheet_star_saysWhetherItIsStarred() {
        show(state())
        openSetups()
        // A screen reader hears the toggle's state with its name ("Star Drive, Starred, checkbox"),
        // which uiautomator's content-desc alone doesn't show
        compose.onNode(hasContentDescription("Star Drive") and hasStateDescription("Starred") and isToggleable() and isOn())
            .assertExists()
            .assert(hasRole(Role.Checkbox))
        compose.onNode(hasContentDescription("Star Warehouse walk") and hasStateDescription("Not starred") and isOff())
            .assertExists()
    }

    @Test
    fun setupsSheet_renamesFromTheMenu_andRefusesANameInUse() {
        show(state())
        openSetups()
        compose.onNodeWithContentDescription("More for Warehouse walk").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithText("Rename setup").assertIsDisplayed()
        compose.onNode(hasText("Warehouse walk") and hasSetTextAction()).performTextReplacement("Drive")
        compose.onNodeWithText("Another setup already has this name").assertIsDisplayed()
        compose.onNode(hasText("Save") and !hasText(SAVE_CURRENT)).assertIsNotEnabled()
        compose.onNode(hasText("Drive") and hasSetTextAction()).performTextReplacement("North site")
        compose.onNode(hasText("Save") and !hasText(SAVE_CURRENT)).performClick()
        assertEquals(listOf("rename:walk:North site"), actions.calls)
        // Back on the setups sheet
        compose.onNodeWithContentDescription("More for Drive").assertIsDisplayed()
    }

    @Test
    fun setupsSheet_deletesFromTheMenu_afterConfirming() {
        show(state())
        openSetups()
        compose.onNodeWithContentDescription("More for Drive").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete “Drive”?").assertIsDisplayed()
        compose.onNode(hasText("Delete") and !hasText("Delete “Drive”?")).performClick()
        assertEquals(listOf("delete:drive"), actions.calls)
    }

    @Test
    fun theButton_hides_whenTheOrganizationSetsEverything() {
        val everything = RecordType.entries.associate { "survey_${it.wireName}" to SurveyMdmKeys.VALUE_OFF } +
            Destination.entries.associate { "destination_${it.wireName}" to SurveyMdmKeys.VALUE_ON }
        show(state(restrictions = everything))
        assertEquals(0, compose.onAllNodesWithContentDescription(OPEN_SETUPS).fetchSemanticsNodes().size)
    }

    @Test
    fun recording_hidesTheButton_andNamesTheMatchingSetup() {
        show(state(runtime = recording()))
        compose.onNodeWithText("Warehouse walk").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithContentDescription(OPEN_SETUPS).fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun beforeTheServiceAnswers_theButtonWaits() {
        show(SurveyUiState.UNKNOWN.copy(savedSetups = state().savedSetups))
        assertEquals(0, compose.onAllNodesWithContentDescription(OPEN_SETUPS).fetchSemanticsNodes().size)
    }

    @Test
    // Real text measurement for this check only: under native graphics a dialog with a text field
    // (the Rename dialog) never lets Compose go idle in Robolectric.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun largeFont_keepsTheSetupNamesAndSaveCurrentWhole() {
        show(state(), largeFont = true)
        openSetups()
        listOf("Warehouse walk", "Drive", SAVE_CURRENT).forEach {
            val layout = compose.textLayout(it)
            assertFalse("$it is cut off", layout.isLineEllipsized(layout.lineCount - 1))
        }
    }

    private fun openSetups() {
        compose.onNodeWithContentDescription(OPEN_SETUPS).performClick()
    }

    private fun hasStateDescription(value: String) =
        androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, value)

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun hasSetTextAction() = androidx.compose.ui.test.hasSetTextAction()

    private fun show(state: SurveyUiState, largeFont: Boolean = false) {
        compose.showPage(largeFont = largeFont) { SurveyScreenContent(state, actions, { _, _ -> state }, SnackbarHostState()) }
    }

    private fun state(
        types: Set<RecordType> = walk.recordTypes,
        saved: List<SurveySetup> = listOf(drive, walk),
        restrictions: Map<String, Any?> = emptyMap(),
        runtime: SurveyRuntimeState = SurveyRuntimeState(),
    ): SurveyUiState = SurveyUiStateMapper.map(
        SurveyInputs(
            runtime = runtime.copy(policy = SurveyPolicy.evaluate(RestrictionsSnapshot(restrictions), false)),
            userSetup = SurveySetup.custom(types, walk.destinations),
            autoIncludePhoneState = true,
            destinationInfo = DestinationInfo(nsAnalyticsRegistered = true),
            location = LocationStatus.Fixed(4),
            nowMs = NOW,
            savedSetups = saved,
        )
    )

    private fun recording() = SurveyRuntimeState(
        destinations = mapOf(Destination.LOG_FILES to DestinationRuntime(walk.recordTypes)),
        missionId = "NS-1",
        missionSessionActive = true,
        sessionStartTimeMs = NOW - 60_000L,
    )

    private class RecordingActions : SurveyActions {
        val calls = mutableListOf<String>()
        override fun onPickSetup(id: String) { calls += "pick:$id" }
        override fun onSaveSetup(name: String) { calls += "save:$name" }
        override fun onRenameSetup(id: String, name: String) { calls += "rename:$id:$name" }
        override fun onStarSetup(id: String, starred: Boolean) { calls += "star:$id:$starred" }
        override fun onDeleteSetup(id: String) { calls += "delete:$id" }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val OPEN_SETUPS = "Saved setups"
        const val SAVE_CURRENT = "Save current setup"
    }
}
