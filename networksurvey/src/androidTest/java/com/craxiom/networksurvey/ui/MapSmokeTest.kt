package com.craxiom.networksurvey.ui

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
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
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The Map tab on the real activity: it mounts, a layer toggle is saved, the Cellular map action
 * and the new-tower notification open it, and the screen stays on across it while recording.
 * MapLibre needs no tiles for any of this; the checks are on the tab's own chrome.
 */
@RunWith(AndroidJUnit4::class)
class MapSmokeTest {

    private val compose: ComposeTestRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(SmokeTestSupport.SeededStartup())
        .around(GrantPermissionRule.grant(*grantedPermissions()))
        .around(compose)

    @get:Rule
    val serviceRule = ServiceTestRule()

    private val shell = ShellDriver(compose)
    private var scenario: ActivityScenario<NetworkSurveyActivity>? = null
    private var service: NetworkSurveyService? = null

    @After
    fun tearDown() {
        service?.let { DestinationTestSupport.stop(it) }
        scenario?.close()
    }

    @Test
    fun theMapTab_mounts_withItsChrome() {
        launch()
        shell.tab("Map").performClick()

        shell.mapChrome().assertIsDisplayed()
        listOf("Layers", "Map settings", "About the map").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        shell.tab("Map").assertIsSelected()
    }

    @Test
    fun aLayerToggle_isSavedToTheMapSettings() {
        launch()
        shell.tab("Map").performClick()
        shell.mapChrome().assertIsDisplayed()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context())
        assertTrue(preferences.getBoolean(NetworkSurveyConstants.PROPERTY_SHOW_TOWERS_LAYER, true))

        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithText("Towers").performClick()
        shell.waitFor("the towers layer saved as off") {
            !preferences.getBoolean(NetworkSurveyConstants.PROPERTY_SHOW_TOWERS_LAYER, true)
        }
        // Search needs the towers layer, so it leaves with it
        Espresso.pressBack()
        shell.waitFor("Search to hide") {
            compose.onAllNodes(hasContentDescription("Search for a tower"))
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun theNewTowerNotificationTap_coldStart_landsOnMap() {
        scenario = ActivityScenario.launch(newTowerTap())
        shell.waitForMap()
        shell.tab("Map").assertIsSelected()
    }

    @Test
    fun theNewTowerNotificationTap_onARunningApp_landsOnMap() {
        launch()
        shell.tab("More").performClick()
        shell.heading("More").assertIsDisplayed()

        context().startActivity(newTowerTap().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        shell.waitForMap()
        shell.tab("Map").assertIsSelected()
    }

    @Test
    fun recording_keepsTheScreenOn_fromMapBackToSurvey_untilStop() {
        // The map's own setting is off, so only the recording survey holds the screen
        PreferenceManager.getDefaultSharedPreferences(context()).edit()
            .putBoolean(NetworkSurveyConstants.PROPERTY_MAP_KEEP_SCREEN_ON, false).commit()
        val bound = DestinationTestSupport.bindService(serviceRule).also { service = it }
        DestinationTestSupport.start(bound, setOf(RecordType.CELLULAR), setOf(Destination.LOG_FILES))
        launch()
        shell.waitFor("the screen kept on") { keepScreenOnFlagSet() }

        shell.tab("Map").performClick()
        shell.mapChrome().assertIsDisplayed()
        assertTrue("Map while recording", keepScreenOnFlagSet())

        shell.tab("Survey").performClick()
        shell.heading("Survey").assertIsDisplayed()
        shell.waitFor("the screen kept on after Map") { keepScreenOnFlagSet() }

        DestinationTestSupport.stop(bound)
        service = null
        shell.waitFor("the screen released after Stop") { !keepScreenOnFlagSet() }
        assertFalse(keepScreenOnFlagSet())
    }

    @Test
    fun theMapsOwnSetting_keepsTheScreenOn_onlyOnTheMap() {
        launch()
        assertFalse("Survey, not recording", keepScreenOnFlagSet())

        shell.tab("Map").performClick()
        shell.mapChrome().assertIsDisplayed()
        shell.waitFor("the map keeping the screen on") { keepScreenOnFlagSet() }

        shell.tab("Survey").performClick()
        shell.heading("Survey").assertIsDisplayed()
        shell.waitFor("the screen released off the map") { !keepScreenOnFlagSet() }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(activityIntent())
        shell.waitForHeading("Survey")
    }

    /** What the new-tower notification's tap sends. */
    private fun newTowerTap(): Intent = activityIntent()
        .putExtra(NetworkSurveyConstants.EXTRA_NAVIGATE_TO_MAP, true)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
