package com.craxiom.networksurvey.ui.watchlist

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import com.craxiom.networksurvey.ui.showPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The watchlist rows and the History list say how long ago a network was seen with the app's one
 * relative age, so a sighting under a minute old reads "now", never "0 minutes ago".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WatchlistAgeTextTest {

    @get:Rule
    val compose = createComposeRule()

    private val entry = WatchlistEntryEntity().apply {
        label = "Example Guest"
        ssid = "Example Guest"
        enabled = true
    }

    @Test
    fun row_seenUnderAMinuteAgo_readsNow() {
        compose.showPage { WatchlistRow(WatchlistRowItem(entry, ago(SECONDS_10), -60, 1), {}, {}) }
        compose.onNodeWithText("seen 1×  ·  now", substring = true).assertExists()
        assertEquals(0, compose.onAllNodesWithText("0 minutes ago", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun row_seenMinutesAgo_saysHowMany() {
        compose.showPage { WatchlistRow(WatchlistRowItem(entry, ago(MINUTES_5), -60, 3), {}, {}) }
        compose.onNodeWithText("seen 3×  ·  5 minutes ago", substring = true).assertExists()
    }

    @Test
    fun historyGroup_seenUnderAMinuteAgo_readsNow() {
        compose.showPage { WatchlistHistoryGroupRow(WatchlistHistoryGroup(entry, 2, -60, ago(SECONDS_10), "#22AA88"), {}) }
        compose.onNodeWithText("now", substring = true).assertExists()
        assertEquals(0, compose.onAllNodesWithText("0 minutes ago", substring = true).fetchSemanticsNodes().size)
    }

    private fun ago(ms: Long) = System.currentTimeMillis() - ms

    private companion object {
        const val SECONDS_10 = 10_000L
        const val MINUTES_5 = 5 * 60_000L + 5_000L
    }
}
