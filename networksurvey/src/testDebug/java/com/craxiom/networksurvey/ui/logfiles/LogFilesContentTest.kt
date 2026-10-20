package com.craxiom.networksurvey.ui.logfiles

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.craxiom.networksurvey.ui.showPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The Log files page on the stateless [LogFilesContent]. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class LogFilesContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : LogFilesActions {
        override fun onSetRollover(megabytes: Int) { calls += "rollover:$megabytes" }
        override fun onShare(files: List<LogFile>) { calls += "share:${files.joinToString(",") { it.name }}" }
        override fun onOpenFolder() { calls += "openFolder" }
    }
    private val choices = listOf(0, 1, 5, 10, 50, 100, 250, 1000)
    private val lte = file("craxiom-lte-20261006-091204.csv", started = NOW - 12 * MINUTE, written = NOW - 5 * MINUTE)

    @Test
    fun noFiles_explainsHowToGetSome() {
        show(LogFilesUiState(loading = false, rolloverChoices = choices))
        compose.onNodeWithText("No log files yet. Turn on Log files on the Survey screen and start a survey.").assertIsDisplayed()
    }

    @Test
    fun files_showTheirSurveyAndWhenTheyWereWritten_andShareWhenTapped() {
        show(listed(lte))
        compose.onNodeWithText("12 minutes ago").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 file · 3.1 MB").assertIsDisplayed()
        compose.onNodeWithText("3.1 MB · 5 minutes ago").assertIsDisplayed()
        compose.onNodeWithText(lte.name).performClick()
        assertEquals(listOf("share:${lte.name}"), calls)
    }

    @Test
    fun theLocationRow_opensTheFolder() {
        show(listed(lte))
        compose.onNodeWithText("Location").performClick()
        assertEquals(listOf("openFolder"), calls)
    }

    @Test
    fun manySurveys_showTwentyAtFirst_andShowMoreAddsTheRest() {
        // Each written for a second, two minutes apart, so each file is its own survey
        val files = (0 until 25).map {
            val started = NOW - it * 2 * MINUTE - HOUR
            file("craxiom-lte-$it.csv", started = started, written = started + 1_000L)
        }
        show(listed(*files.toTypedArray()))
        compose.onNodeWithText("Showing the 20 newest of 25 surveys.").performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("craxiom-lte-24.csv").fetchSemanticsNodes().size)
        compose.onNodeWithText("Show more").performScrollTo().performClick()
        compose.onNodeWithText("craxiom-lte-24.csv").performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Show more").fetchSemanticsNodes().size)
    }

    @Test
    fun select_sharesAWholeSurveyAndSingleFiles_atOnce() {
        val wifi = file("craxiom-wifi-20261006-091204.csv", started = NOW - 12 * MINUTE, written = NOW - 5 * MINUTE)
        val older = file("craxiom-gnss-20261005-080000.csv", started = NOW - 3 * HOUR, written = NOW - 2 * HOUR)
        show(listed(lte, wifi, older))

        compose.onNodeWithText("Select").performClick()
        compose.onNodeWithText("Share 0 files").assertIsNotEnabled()
        compose.onNodeWithText("12 minutes ago").performClick()
        compose.onNodeWithText(older.name).performScrollTo().performClick()
        compose.onNodeWithText("Share 3 files").assertIsEnabled().performClick()
        assertEquals(listOf("share:${lte.name},${wifi.name},${older.name}"), calls)
        // Sharing ends select mode, as in the Files app
        assertEquals(0, compose.onAllNodesWithText("Share 3 files").fetchSemanticsNodes().size)
        compose.onNodeWithText("Select").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cancel_leavesSelectMode() {
        show(listed(lte))
        compose.onNodeWithText("Select").performClick()
        compose.onNodeWithText(lte.name).performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertEquals(0, compose.onAllNodesWithText("Share 1 file").fetchSemanticsNodes().size)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun aLongPress_startsSelectingWithThatFile() {
        show(listed(lte))
        compose.onNodeWithText(lte.name).performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("Share 1 file").assertIsEnabled()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun rollover_choosesASize() {
        show(LogFilesUiState(loading = false, rolloverMb = 10, rolloverChoices = choices))
        compose.onNodeWithText("10 MB").performClick()
        compose.onNodeWithText("50 MB").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("rollover:50"), calls)
    }

    @Test
    fun rollover_setByTheOrganization_showsItsValueAndExplains() {
        show(LogFilesUiState(loading = false, rolloverMb = 37, rolloverLocked = true, rolloverChoices = choices))
        compose.onNodeWithText("37 MB").performClick()
        compose.onNodeWithText("Your organization chose this for this phone. Ask your administrator to change it.").assertIsDisplayed()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun unreadableFolder_saysFilesMayStillBeSaved() {
        show(LogFilesUiState(loading = false, listing = LogFilesListing.Unreadable, rolloverChoices = choices))
        compose.onNodeWithText("Can't read the log files folder right now. Files may still be saved; check again after the survey.")
            .assertIsDisplayed()
    }

    @Test
    fun largeFonts_stillShowRecentFiles() {
        compose.showPage(largeFont = true) {
            LogFilesContent(LogFilesUiState(loading = false, rolloverChoices = choices), actions, SnackbarHostState())
        }
        compose.onNodeWithText("Recent files").performScrollTo().assertIsDisplayed()
    }

    private fun show(state: LogFilesUiState) = compose.showPage { LogFilesContent(state, actions, SnackbarHostState()) }

    private fun listed(vararg files: LogFile) = LogFilesUiState(
        loading = false,
        listing = LogFilesListing.Files(LogFileGroups.group(files.toList())),
        nowMs = NOW,
        rolloverChoices = choices,
    )

    private fun file(name: String, started: Long, written: Long) = LogFile(File(name), name, 3_100_000L, written, started)

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
