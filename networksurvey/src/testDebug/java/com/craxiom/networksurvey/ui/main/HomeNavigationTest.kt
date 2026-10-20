package com.craxiom.networksurvey.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.craxiom.networksurvey.ui.PageTheme
import com.craxiom.networksurvey.ui.signals.SignalsSubTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The four-tab shell on [HomeShell], with stand-ins for the tab screens. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class HomeNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController
    private var homeTabRequest by mutableStateOf<String?>(null)
    private var handledRequests = 0

    @Test
    fun fourTabs_withTheirLabels_startOnSurvey() {
        show()
        listOf("Survey", "Signals", "Map", "More").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText(SURVEY_SLOT).assertIsDisplayed()
        compose.onNodeWithText("Survey").assertIsSelected()
    }

    @Test
    fun selectionFollowsTheTabs_andBack() {
        show()
        compose.onNodeWithText("Map").performClick()
        compose.onNodeWithText(MAP_SLOT).assertIsDisplayed()
        compose.onNodeWithText("Map").assertIsSelected()

        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText(MORE_SLOT).assertIsDisplayed()

        pressBack()
        compose.onNodeWithText(SURVEY_SLOT).assertIsDisplayed()
        compose.onNodeWithText("Survey").assertIsSelected()
    }

    @Test
    fun backFromAnyOtherTab_landsOnSurvey_withNothingUnderIt() {
        show()
        compose.onNodeWithText("Signals").performClick()
        compose.onNodeWithText("Map").performClick()
        compose.onNodeWithText("More").performClick()

        pressBack()
        compose.onNodeWithText(SURVEY_SLOT).assertIsDisplayed()
        assertEquals(MainScreens.Survey.route, navController.currentDestination?.route)
        // Back from Survey leaves the app: there is no tab under it.
        assertNull(navController.previousBackStackEntry)
    }

    @Test
    fun aDeepLinkTabRequest_selectsTheTab_once() {
        show()
        homeTabRequest = MainScreens.Map.route
        compose.waitForIdle()
        compose.onNodeWithText(MAP_SLOT).assertIsDisplayed()
        compose.onNodeWithText("Map").assertIsSelected()
        assertEquals(1, handledRequests)
    }

    @Test
    fun theSignalsSubTab_isKeptAcrossTabSwitches() {
        show()
        compose.onNodeWithText("Signals").performClick()
        compose.onNodeWithText("Show Wi-Fi").performClick()
        compose.onNodeWithText("sub:WIFI").assertIsDisplayed()

        compose.onNodeWithText("Map").performClick()
        compose.onNodeWithText("Signals").performClick()
        compose.onNodeWithText("sub:WIFI").assertIsDisplayed()
    }

    @Test
    fun theTab_andTheSubTab_surviveRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PageTheme { Shell() } }
        compose.onNodeWithText("Signals").performClick()
        compose.onNodeWithText("Show Wi-Fi").performClick()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("sub:WIFI").assertIsDisplayed()
        compose.onNodeWithText("Signals").assertIsSelected()
    }

    @Test
    fun aPageOpenedAboveTheTabs_returnsToTheTabItWasOpenedFrom() {
        lateinit var outer: NavHostController
        compose.setContent {
            PageTheme {
                outer = rememberNavController()
                NavHost(outer, startDestination = HOME) {
                    composable(HOME) { Shell() }
                    composable(PAGE) { Text(PAGE) }
                }
            }
        }
        compose.onNodeWithText("More").performClick()
        compose.runOnUiThread { outer.navigate(PAGE) }
        compose.onNodeWithText(PAGE).assertIsDisplayed()

        pressBack()
        compose.onNodeWithText(MORE_SLOT).assertIsDisplayed()
        compose.onNodeWithText("More").assertIsSelected()
    }

    @Test
    fun onAWideScreen_theTabsAreARail() {
        show(useRail = true)
        val survey = compose.onNodeWithText("Survey").getUnclippedBoundsInRoot()
        val more = compose.onNodeWithText("More").getUnclippedBoundsInRoot()
        // A rail stacks the tabs down the side; a bar lays them side by side.
        assertTrue(more.top > survey.bottom)
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText(MORE_SLOT).assertIsDisplayed()
    }

    @Test
    fun onABottomBar_theTabsAreSideBySide() {
        show()
        val survey = compose.onNodeWithText("Survey").getUnclippedBoundsInRoot()
        val more = compose.onNodeWithText("More").getUnclippedBoundsInRoot()
        assertEquals(survey.top, more.top)
    }

    @Test
    fun atLargeFont_theLabelsStay() {
        show(fontScale = 1.3f)
        listOf("Survey", "Signals", "Map", "More").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun atTheLargestFont_onlyTheIconsShow_andTalkBackStillNamesThem() {
        show(fontScale = 1.5f)
        compose.onNodeWithText("Signals").assertDoesNotExist()
        compose.onNodeWithContentDescription("Signals").assertIsDisplayed()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText(MORE_SLOT).assertIsDisplayed()
    }

    private fun show(useRail: Boolean = false, fontScale: Float? = null) {
        compose.setContent { PageTheme(fontScale = fontScale) { Shell(useRail) } }
    }

    @Composable
    private fun Shell(useRail: Boolean = false) {
        navController = rememberNavController()
        HomeShell(
            survey = { Text(SURVEY_SLOT) },
            signals = { subTab, onSubTabChange, _ ->
                Column {
                    Text("sub:${subTab.name}")
                    Button(onClick = { onSubTabChange(SignalsSubTab.WIFI) }) { Text("Show Wi-Fi") }
                }
            },
            map = { Text(MAP_SLOT) },
            more = { Text(MORE_SLOT) },
            homeTabRequest = homeTabRequest,
            onHomeTabHandled = {
                handledRequests++
                homeTabRequest = null
            },
            useRail = useRail,
            navController = navController,
        )
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private companion object {
        const val SURVEY_SLOT = "survey-slot"
        const val MAP_SLOT = "map-slot"
        const val MORE_SLOT = "more-slot"
        const val HOME = "home"
        const val PAGE = "a page"
    }
}
