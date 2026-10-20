package com.craxiom.networksurvey.ui.survey

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ActivityScenario
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.theme.NsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A rotation while the battery question is open must not lose the survey start the user asked
 * for: the question's state lives in a view model, so the answer given after the activity is
 * recreated still starts the survey.
 */
@RunWith(RobolectricTestRunner::class)
// The Compose test rule drives input through Espresso, which needs API 35 or lower (see SurveyScreenContentTest)
@Config(sdk = [35])
class SurveyPermissionRotationTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun aBatteryAnswerAfterARotation_stillStartsTheSurvey() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { StartScreen() } }
            compose.onNodeWithText(START).performClick()
            compose.onNodeWithText(string(R.string.battery_optimization_title)).assertIsDisplayed()

            scenario.recreate()
            scenario.onActivity { it.setContent { StartScreen() } }

            // The question is still up after the rotation, and its answer reaches the start
            compose.onNodeWithText(string(R.string.battery_optimization_later)).performClick()
            compose.waitForIdle()
            scenario.onActivity { assertEquals(1, ViewModelProvider(it)[StartRecorder::class.java].starts) }
        }
    }

    /** The survey start as the Survey screen wires it: the battery question first, then a view model call. */
    @Composable
    private fun StartScreen() {
        val permissions = rememberSurveyPermissionFlow(battery = DueBatteryPrompt)
        val recorder: StartRecorder = viewModel()
        NsTheme(darkTheme = false) {
            Button(onClick = { permissions.ensureBatteryChoice { recorder.start() } }) { Text(START) }
        }
    }

    /** Stands in for the Survey view model, which survives the rotation like this one. */
    class StartRecorder : ViewModel() {
        var starts = 0
            private set

        fun start() {
            starts++
        }
    }

    /** The battery question is always due. */
    private object DueBatteryPrompt : BatteryPrompt {
        override fun isDue() = true
        override fun openSettings() = Unit
    }

    private companion object {
        const val START = "Start"
    }
}
