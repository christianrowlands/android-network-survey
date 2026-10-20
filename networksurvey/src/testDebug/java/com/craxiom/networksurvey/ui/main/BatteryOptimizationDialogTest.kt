package com.craxiom.networksurvey.ui.main

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.util.BatteryOptimizationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The battery optimization question: Later, Go to Settings and Back are three different answers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BatteryOptimizationDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val helper = BatteryOptimizationHelper(context)
    private val answers = mutableListOf<String>()

    @Before
    fun setUp() {
        context.getSharedPreferences("battery_optimization", 0).edit().clear().commit()
        compose.showPage {
            BatteryOptimizationDialog(
                onLater = { answers += "later" },
                onGoToSettings = { answers += "settings" },
                onDismiss = { answers += "dismiss" },
                batteryOptimizationHelper = helper,
            )
        }
    }

    @Test
    fun later_isItsOwnAnswer_andStartsTheQuietPeriod() {
        assertTrue(helper.shouldPromptForBatteryOptimization())

        compose.onNodeWithText("Later").performClick()

        assertEquals(listOf("later"), answers)
        assertFalse(helper.shouldPromptForBatteryOptimization())
    }

    @Test
    fun theDontShowLabel_togglesTheBox_andItIsKeptWithTheAnswer() {
        compose.onNodeWithText("Don't show this again").performClick()
        compose.onNode(isToggleable()).assertIsOn()

        compose.onNodeWithText("Later").performClick()

        assertEquals(listOf("later"), answers)
        assertTrue(context.getSharedPreferences("battery_optimization", 0).getBoolean("dont_show_battery_optimization_prompt", false))
    }

    @Test
    fun goToSettings_isItsOwnAnswer() {
        compose.onNodeWithText("Go to Settings").performClick()

        assertEquals(listOf("settings"), answers)
    }
}
