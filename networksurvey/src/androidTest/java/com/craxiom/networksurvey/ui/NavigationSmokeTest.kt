package com.craxiom.networksurvey.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.rule.ServiceTestRule
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.services.DestinationTestSupport
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.SmokeTestSupport.activityIntent
import com.craxiom.networksurvey.ui.SmokeTestSupport.context
import com.craxiom.networksurvey.ui.SmokeTestSupport.grantedPermissions
import com.craxiom.networksurvey.ui.SmokeTestSupport.keepScreenOnFlagSet
import com.craxiom.networksurvey.ui.main.SharedViewModel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The four-tab shell on the real activity: every tab and sub-tab opens, Back returns where it
 * should, deep links and notification taps are handled once, and the screen stays on across tabs
 * while a survey records.
 *
 * Preferences are seeded before launch so the startup prompts (background location rationale,
 * battery optimization, GNSS failure, Wi-Fi scan throttling) are already answered, and Wi-Fi and
 * location are switched on so no system panel or GPS prompt takes the screen.
 */
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    private val compose: ComposeTestRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(SmokeTestSupport.SeededStartup())
        .around(GrantPermissionRule.grant(*grantedPermissions()))
        .around(compose)

    private val shell = ShellDriver(compose)

    @get:Rule
    val serviceRule = ServiceTestRule()

    private var scenario: ActivityScenario<NetworkSurveyActivity>? = null
    private var service: NetworkSurveyService? = null

    @After
    fun tearDown() {
        service?.let { DestinationTestSupport.stop(it) }
        scenario?.close()
    }

    @Test
    fun everyTabAndSubTab_opens() {
        launch()
        tab("Signals").performClick()
        shell.signalsChrome().assertIsDisplayed()
        listOf("Wi-Fi", "Bluetooth", "GNSS", "Cellular").forEach { label ->
            tab(label).performClick()
            tab(label).assertIsSelected()
        }

        tab("Map").performClick()
        shell.mapChrome().assertIsDisplayed()

        tab("More").performClick()
        heading("More").assertIsDisplayed()
        compose.onNodeWithText("Help and about").performScrollTo().assertIsDisplayed()

        tab("Survey").performClick()
        heading("Survey").assertIsDisplayed()
    }

    @Test
    fun aPageOpenedFromMore_goesBackToMore() {
        launch()
        tab("More").performClick()
        compose.onNodeWithText("MQTT stream").performClick()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()

        Espresso.pressBack()
        heading("More").assertIsDisplayed()
        tab("More").assertIsSelected()
    }

    @Test
    fun seeOnMap_fromCommunity_selectsTheMapTab_andBackGoesToSurvey() {
        launch()
        tab("More").performClick()
        compose.onNodeWithText("Community databases").performClick()
        compose.waitForIdle()
        // What Community's "See on map" button does; the button only shows with upload results.
        scenario!!.onActivity { ViewModelProvider(it)[SharedViewModel::class.java].triggerNavigationToMapTab() }
        shell.waitForMap()
        tab("Map").assertIsSelected()

        Espresso.pressBack()
        heading("Survey").assertIsDisplayed()
    }

    @Test
    fun theSurveyNotificationTap_fromSignals_landsOnSurvey() {
        launch()
        tab("Signals").performClick()
        shell.signalsChrome().assertIsDisplayed()

        context().startActivity(
            activityIntent().putExtra(NetworkSurveyConstants.EXTRA_NAVIGATE_TO_DASHBOARD, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        waitForHeading("Survey")
        tab("Survey").assertIsSelected()
    }

    @Test
    fun theGrpcLink_coldStart_opensItsPageOnce_andNotAgainAfterRotation() {
        scenario = ActivityScenario.launch(grpcLink())
        waitForGrpcPage()

        Espresso.pressBack()
        heading("Survey").assertIsDisplayed()

        // A recreation (rotation, theme change) must not handle the link again.
        scenario!!.recreate()
        heading("Survey").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun theGrpcLink_onARunningApp_opensItsPageOnce() {
        launch()
        // Tapped while the app runs: singleTask hands the link to the running activity's onNewIntent.
        scenario!!.onActivity { it.startActivity(grpcLink()) }
        waitForGrpcPage()

        Espresso.pressBack()
        heading("Survey").assertIsDisplayed()
    }

    @Test
    fun recording_keepsTheScreenOn_acrossTabs_untilStop() {
        val bound = DestinationTestSupport.bindService(serviceRule).also { service = it }
        DestinationTestSupport.start(bound, setOf(RecordType.CELLULAR), setOf(Destination.LOG_FILES))
        launch()

        waitFor("the screen kept on") { keepScreenOnFlagSet() }
        tab("Signals").performClick()
        shell.signalsChrome().assertIsDisplayed()
        assertTrue("Signals while recording", keepScreenOnFlagSet())

        tab("Survey").performClick()
        heading("Survey").assertIsDisplayed()
        waitFor("the screen kept on after Signals") { keepScreenOnFlagSet() }

        // A pause and resume, as a notification tap does, must not leave the screen held after Stop.
        scenario!!.moveToState(Lifecycle.State.STARTED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        waitFor("the screen kept on after a resume") { keepScreenOnFlagSet() }

        DestinationTestSupport.stop(bound)
        service = null
        waitFor("the screen released after Stop") { !keepScreenOnFlagSet() }
        assertFalse(keepScreenOnFlagSet())
    }

    private fun launch() {
        scenario = ActivityScenario.launch(activityIntent())
        waitForHeading("Survey")
    }

    /** The gRPC page: its title, with Back (no tab has Back). */
    private fun waitForGrpcPage() = waitFor("the gRPC page") {
        compose.onAllNodes(hasText("gRPC stream")).fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodes(hasContentDescription("Back")).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForHeading(title: String) = shell.waitForHeading(title)

    private fun tab(label: String) = shell.tab(label)

    private fun heading(title: String) = shell.heading(title)

    private fun waitFor(what: String, condition: () -> Boolean) = shell.waitFor(what, condition)

    private fun grpcLink(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://craxiom.com/grpc_server_connection"))
        .setClass(context(), NetworkSurveyActivity::class.java)
}
