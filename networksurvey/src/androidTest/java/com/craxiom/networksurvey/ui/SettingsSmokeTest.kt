package com.craxiom.networksurvey.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.SmokeTestSupport.activityIntent
import com.craxiom.networksurvey.ui.SmokeTestSupport.grantedPermissions
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The Settings page and the two pages it leads to (upload settings and map settings) open from
 * their user paths, survive a recreation (as a rotation), and close with Back.
 */
@RunWith(AndroidJUnit4::class)
class SettingsSmokeTest {

    private val compose: ComposeTestRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(SmokeTestSupport.SeededStartup())
        .around(GrantPermissionRule.grant(*grantedPermissions()))
        .around(compose)

    private val shell = ShellDriver(compose)
    private var scenario: ActivityScenario<NetworkSurveyActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun settings_opensFromMore() {
        launch()
        shell.tab("More").performClick()
        compose.onNodeWithText("Settings").performScrollTo().performClick()

        opensAndCloses(page = "Settings", row = "Scan rates", recreate = true)
    }

    @Test
    fun uploadSettings_openFromCommunityDatabases() {
        launch()
        shell.tab("More").performClick()
        compose.onNodeWithText("Community databases").performClick()
        compose.onNodeWithText("Upload settings").performScrollTo().performClick()

        opensAndCloses(page = "Upload settings", row = Application.get().getString(R.string.upload_retry_title))
    }

    @Test
    fun mapSettings_openFromTheMap() {
        launch()
        shell.tab("Map").performClick()
        compose.onNodeWithContentDescription("Map settings").performClick()

        opensAndCloses(page = "Map settings", row = Application.get().getString(R.string.show_zoom_controls_title))
    }

    /** Waits for [page]'s [row]; with [recreate], recreates the activity and checks it again; then Back closes it. */
    private fun opensAndCloses(page: String, row: String, recreate: Boolean = false) {
        shell.waitFor("the $page page") { compose.onAllNodes(hasText(row)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(row).assertIsDisplayed()
        if (recreate) {
            scenario!!.recreate()
            shell.waitFor("the $page page after a recreation") { compose.onAllNodes(hasText(row)).fetchSemanticsNodes().isNotEmpty() }
        }
        Espresso.pressBack()
        shell.waitFor("the $page page to close") { compose.onAllNodes(hasText(row)).fetchSemanticsNodes().isEmpty() }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(activityIntent())
        shell.waitForHeading("Survey")
    }
}
