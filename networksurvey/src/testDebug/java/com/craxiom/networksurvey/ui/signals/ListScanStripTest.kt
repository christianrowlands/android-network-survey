package com.craxiom.networksurvey.ui.signals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The strip at the top of the Wi-Fi and Bluetooth lists, at a small phone's width. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class ListScanStripTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun countsAndStatus_areOneTalkBackSentence_butTheStateIsNotDrawn() {
        show(counts = listOf("Scan #12", "30 APs"))

        compose.onNodeWithContentDescription("Scan #12. 30 APs. Live view").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sort by: Signal strength. Tap to change.").assertIsDisplayed()
        compose.onNodeWithText("Scan #12 · 30 APs", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("Live view", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aWarning_isDrawn() {
        show(counts = listOf("Scan #12", "30 APs"), warning = "Survey paused", spoken = "Survey paused")

        compose.onNodeWithText("Survey paused", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun thePauseButton_saysWhatItWillDo() {
        var paused by mutableStateOf(false)
        compose.showPage {
            ListScanStrip(
                counts = listOf("4 devices"),
                warning = null,
                spokenStatus = "Live view",
                sortLabel = "Name",
                onSortClick = {},
                paused = paused,
                onTogglePause = { paused = !paused },
            )
        }

        compose.onNodeWithContentDescription("Pause updates").performClick()
        compose.onNodeWithContentDescription("Resume updates").assertIsDisplayed()
    }

    @Test
    fun theSortPill_callsBack() {
        var sorts = 0
        compose.showPage {
            ListScanStrip(listOf("4 devices"), null, "Live view", "Name", { sorts++ }, false, {})
        }

        compose.onNodeWithContentDescription("Sort by: Name. Tap to change.").performClick()

        assertEquals(1, sorts)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atLargeText_theWifiStripKeepsItsWarning() {
        show(counts = listOf("Scan #1234", "120 of 145 APs"), warning = "Survey paused", spoken = "Survey paused", largeFont = true)

        assertFalse(compose.textLayout("Survey paused").isLineEllipsized(0))
        assertFalse(compose.textLayout("120 of 145 APs", substring = true).isLineEllipsized(0))
        assertFalse(compose.textLayout("Security type", substring = true).isLineEllipsized(0))
        compose.onNodeWithContentDescription("Pause updates").assertIsDisplayed()
    }

    private fun show(counts: List<String>, warning: String? = null, spoken: String = "Live view", largeFont: Boolean = false) {
        compose.showPage(largeFont = largeFont) {
            Column(Modifier.fillMaxWidth()) {
                ListScanStrip(
                    counts = counts,
                    warning = warning,
                    spokenStatus = spoken,
                    sortLabel = if (largeFont) "Security type" else "Signal strength",
                    onSortClick = {},
                    paused = false,
                    onTogglePause = {},
                )
            }
        }
    }
}
