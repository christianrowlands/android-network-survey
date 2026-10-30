package com.craxiom.networksurvey.ui.onboarding

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import com.craxiom.networksurvey.ui.PageTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * First run end to end through its view model: who sees it (a fresh install, not a managed or a
 * registered phone), moving through the cards with Continue and Back, keeping the card across a
 * recreation, and the choice it stores.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var store: SurveySetupStore
    private var restrictions: Map<String, Any?> = emptyMap()
    private var registered = false
    private var finishedWith: OnboardingChoice? = null

    @Before
    fun setUp() {
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("onboarding_screen_test", Context.MODE_PRIVATE)
        preferences.edit { clear() }
        store = SurveySetupStore(preferences)
        store.onboardingPending = true
        // Granted, so the permission card continues without the system dialog
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            "android.permission.POST_NOTIFICATIONS",
        )
    }

    private fun viewModel() = OnboardingViewModel(
        RuntimeEnvironment.getApplication() as Application,
        store,
        { RestrictionsSnapshot(restrictions) },
        { registered },
        { false },
    )

    /** What MainCompose does: the cards while first run shows, else the tabs. */
    private fun host(viewModel: OnboardingViewModel) {
        compose.setContent { Host(viewModel) }
    }

    @androidx.compose.runtime.Composable
    private fun Host(viewModel: OnboardingViewModel) {
        PageTheme {
            val visible by viewModel.visible.collectAsStateWithLifecycle()
            if (visible) OnboardingScreen(viewModel) { finishedWith = it } else Text(TABS)
        }
    }

    @Test
    fun aFreshInstall_seesTheCards() {
        host(viewModel())
        compose.onNodeWithText(INTRO_TITLE).assertIsDisplayed()
    }

    @Test
    fun aManagedPhone_goesStraightToTheTabs() {
        restrictions = mapOf("survey_cellular" to "on")
        host(viewModel())
        compose.onNodeWithText(TABS).assertIsDisplayed()
        assertFalse(store.onboardingPending)
    }

    @Test
    fun aRegisteredPhone_goesStraightToTheTabs() {
        registered = true
        host(viewModel())
        compose.onNodeWithText(TABS).assertIsDisplayed()
    }

    @Test
    fun continueAndBack_moveBetweenTheCards_andTheChoiceIsStored() {
        host(viewModel())
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Permissions, and why").assertIsDisplayed()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(INTRO_TITLE).assertIsDisplayed()

        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Files on this phone").performScrollTo().performClick()

        compose.onNodeWithText(TABS).assertIsDisplayed()
        assertEquals(OnboardingChoice.LOG_FILES, finishedWith)
        assertEquals(setOf(Destination.LOG_FILES), store.getCurrentSetup().destinations)
        assertFalse(store.onboardingPending)
    }

    @Test
    fun continueTwiceInOneFrame_onTheLastButOneCard_staysOnTheLastCard() {
        host(viewModel())
        // The footer only leaves after a recomposition, so the clicks all land on the same button
        val click = compose.onNodeWithText("Continue").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnUiThread { repeat(3) { click() } }

        compose.onNodeWithText("Files on this phone").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun backTwiceInOneFrame_onTheSecondCard_staysOnTheFirstCard() {
        host(viewModel())
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Permissions, and why").assertIsDisplayed()

        compose.runOnUiThread { repeat(2) { compose.activity.onBackPressedDispatcher.onBackPressed() } }

        compose.onNodeWithText(INTRO_TITLE).assertIsDisplayed()
    }

    @Test
    fun skip_endsFirstRun_withTheSetupUnchanged() {
        host(viewModel())
        val before = store.getCurrentSetup()
        compose.onNodeWithText("Skip").performClick()
        compose.onNodeWithText(TABS).assertIsDisplayed()
        assertEquals(before, store.getCurrentSetup())
        assertEquals(null, finishedWith)
    }

    @Test
    fun theCard_survivesARecreation() {
        val viewModel = viewModel()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Host(viewModel) }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Permissions, and why").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Permissions, and why").assertIsDisplayed()
    }

    private companion object {
        const val INTRO_TITLE = "Walk around. It records what the radios see."
        const val TABS = "The tabs"
    }
}
