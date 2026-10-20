package com.craxiom.networksurvey.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.RestrictionsSource
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import com.craxiom.networksurvey.ui.SmokeTestSupport.activityIntent
import com.craxiom.networksurvey.ui.SmokeTestSupport.context
import com.craxiom.networksurvey.ui.SmokeTestSupport.grantedPermissions
import com.craxiom.networksurvey.ui.onboarding.OnboardingViewModel
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * First run on the real activity: a fresh install sees it once and lands on Survey with the
 * chosen setup, Back walks back through the cards, and a managed or NS Analytics registered phone
 * goes straight to Survey.
 *
 * The app's migration has already run in this process, so each test seeds what a fresh install's
 * migration writes (first run pending). The managed configuration can only be set by a device
 * policy app, so the managed test hands first run a configuration through its test override.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingSmokeTest {

    private val compose: ComposeTestRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(SmokeTestSupport.SeededStartup(onboardingPending = true))
        .around(GrantPermissionRule.grant(*grantedPermissions()))
        .around(compose)

    private val shell = ShellDriver(compose)
    private var scenario: ActivityScenario<NetworkSurveyActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        OnboardingViewModel.restrictionsOverride = null
        NsAnalyticsSecureStorage.clearAllCredentials(context())
    }

    @Test
    fun aFreshInstall_seesFirstRunOnce_andLandsOnSurveyWithTheChosenSetup() {
        launch()
        waitForText(INTRO_TITLE)
        compose.onNodeWithText(CONTINUE).performClick()
        waitForText(PERMISSIONS_TITLE)
        // Every launch permission is granted, so the card continues without asking
        compose.onNodeWithText(CONTINUE).performClick()
        waitForText(FILES)
        compose.onNodeWithText(FILES).performScrollTo().performClick()

        shell.waitForHeading("Survey")
        assertEquals(setOf(Destination.LOG_FILES), SurveySetupStore(context()).getCurrentSetup().destinations)
        assertFalse(SurveySetupStore(context()).onboardingPending)

        scenario?.close()
        launch()
        shell.waitForHeading("Survey")
        assertEquals(0, compose.onAllNodes(hasText(INTRO_TITLE)).fetchSemanticsNodes().size)
    }

    @Test
    fun back_walksBackThroughTheCards() {
        launch()
        waitForText(INTRO_TITLE)
        compose.onNodeWithText(CONTINUE).performClick()
        waitForText(PERMISSIONS_TITLE)
        compose.onNodeWithText(CONTINUE).performClick()
        waitForText(FILES)

        Espresso.pressBack()
        waitForText(PERMISSIONS_TITLE)
        Espresso.pressBack()
        waitForText(INTRO_TITLE)
        compose.onNodeWithText(INTRO_TITLE).assertIsDisplayed()
    }

    @Test
    fun aRegisteredPhone_goesStraightToSurvey() {
        NsAnalyticsSecureStorage.storeRegistrationData(
            context(), "token", "workspace", "http://127.0.0.1:9/", "device", "Workspace",
        )
        launch()
        shell.waitForHeading("Survey")
        assertFalse(SurveySetupStore(context()).onboardingPending)
    }

    @Test
    fun aManagedPhone_goesStraightToSurvey() {
        OnboardingViewModel.restrictionsOverride = RestrictionsSource {
            RestrictionsSnapshot(mapOf(SurveyMdmKeys.SURVEY_CELLULAR to SurveyMdmKeys.VALUE_ON))
        }
        launch()
        shell.waitForHeading("Survey")
        assertFalse(SurveySetupStore(context()).onboardingPending)
    }

    private fun launch() {
        scenario = ActivityScenario.launch(activityIntent())
    }

    private fun waitForText(text: String) = shell.waitFor(text) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val INTRO_TITLE = "Walk around. It records what the radios see."
        const val PERMISSIONS_TITLE = "Permissions, and why"
        const val CONTINUE = "Continue"
        const val FILES = "Files on this phone"
    }
}
