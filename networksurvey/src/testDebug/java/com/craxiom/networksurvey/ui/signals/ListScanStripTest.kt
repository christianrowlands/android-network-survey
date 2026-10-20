package com.craxiom.networksurvey.ui.signals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun countsAndStatus_areOneTalkBackSentence() {
        show(counts = listOf("Scan #12", "APs: 30"), status = "Live view")

        compose.onNodeWithContentDescription("Scan #12. APs: 30. Live view").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sort by: Signal strength. Tap to change.").assertIsDisplayed()
    }

    @Test
    fun thePauseButton_saysWhatItWillDo() {
        var paused by mutableStateOf(false)
        compose.showPage {
            ListScanStrip(
                counts = listOf("4 devices"),
                status = "Live view",
                live = !paused,
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
            ListScanStrip(listOf("4 devices"), "Live view", true, "Name", { sorts++ }, false, {})
        }

        compose.onNodeWithContentDescription("Sort by: Name. Tap to change.").performClick()

        assertEquals(1, sorts)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atLargeText_theWifiStripKeepsItsStatus() {
        show(counts = listOf("Scan #1234", "APs: 120 of 145"), status = "Not in this survey", largeFont = true)

        assertFalse(compose.textLayout("Not in this survey").isLineEllipsized(0))
        assertFalse(compose.textLayout("APs: 120 of 145", substring = true).isLineEllipsized(0))
        assertFalse(compose.textLayout("Security type", substring = true).isLineEllipsized(0))
        compose.onNodeWithContentDescription("Pause updates").assertIsDisplayed()
    }

    private fun show(counts: List<String>, status: String, largeFont: Boolean = false) {
        compose.showPage(largeFont = largeFont) {
            Column(Modifier.fillMaxWidth()) {
                ListScanStrip(
                    counts = counts,
                    status = status,
                    live = true,
                    sortLabel = if (largeFont) "Security type" else "Signal strength",
                    onSortClick = {},
                    paused = false,
                    onTogglePause = {},
                )
            }
        }
    }
}
