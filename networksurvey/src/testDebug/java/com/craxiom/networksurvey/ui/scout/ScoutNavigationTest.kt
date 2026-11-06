package com.craxiom.networksurvey.ui.scout

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.craxiom.networksurvey.ui.main.NavOption
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Going to the Scout page from its firmware update never leaves it on the back stack twice. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class ScoutNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = HOME) {
                composable(HOME) {}
                composable(SCOUT) {}
                composable(FIRMWARE) {}
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun fromAnUpdateOpenedOnTheScoutPage_itGoesBackToThatPage() {
        compose.runOnIdle {
            nav.navigate(SCOUT)
            nav.navigate(FIRMWARE)
            nav.openScoutPage()

            assertEquals(SCOUT, nav.currentBackStackEntry?.destination?.route)
            assertEquals(HOME, nav.previousBackStackEntry?.destination?.route)
        }
    }

    @Test
    fun fromAnUpdateOpenedElsewhere_itPushesThePage() {
        compose.runOnIdle {
            nav.navigate(FIRMWARE)
            nav.openScoutPage()

            assertEquals(SCOUT, nav.currentBackStackEntry?.destination?.route)
            assertEquals(FIRMWARE, nav.previousBackStackEntry?.destination?.route)
        }
    }

    private companion object {
        const val HOME = "home"
        val SCOUT = NavOption.NsScout.name
        val FIRMWARE = NavOption.ScoutFirmware.name
    }
}
