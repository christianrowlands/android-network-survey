package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.theme.NsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Start and Stop bar's guard against a double tap: Stop stays disabled for a moment after it
 * replaces Start, but works right away when the screen opens on a survey that is already recording.
 */
@RunWith(RobolectricTestRunner::class)
// Espresso needs API 35 or lower under Robolectric (see SurveyScreenContentTest)
@Config(sdk = [35])
class SurveyCtaBarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun stopIsDisabledForAMomentAfterItReplacesStart() {
        var recording by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent { NsTheme { Bar(recording) } }
        compose.onNodeWithText(string(R.string.survey_start)).assertIsEnabled()

        recording = true
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(string(R.string.survey_stop)).assertIsNotEnabled()

        compose.mainClock.advanceTimeBy(STOP_GUARD_MS + FRAME_MS)
        compose.onNodeWithText(string(R.string.survey_stop)).assertIsEnabled()
    }

    @Test
    fun stopWorksRightAwayOnASurveyThatWasAlreadyRecording() {
        compose.setContent { NsTheme { Bar(recording = true) } }
        compose.onNodeWithText(string(R.string.survey_stop)).assertIsEnabled()
    }

    @Composable
    private fun Bar(recording: Boolean) = SurveyCtaBar(
        recording = recording,
        startBlock = null,
        onStart = {},
        onStop = {},
        onUseLogFiles = {},
        onConnectNsAnalytics = {},
    )

    private companion object {
        const val FRAME_MS = 32L
    }
}
