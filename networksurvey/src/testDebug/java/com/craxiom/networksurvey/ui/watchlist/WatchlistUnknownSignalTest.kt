package com.craxiom.networksurvey.ui.watchlist

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity
import com.craxiom.networksurvey.ui.showPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A sighting of an access point that reported no signal strength reads as an unknown signal on the
 * History list and the sighting details, never as 0 dBm.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WatchlistUnknownSignalTest {

    @get:Rule
    val compose = createComposeRule()

    private val entry = WatchlistEntryEntity().apply {
        label = "Example Guest"
        ssid = "Example Guest"
        enabled = true
    }

    @Test
    fun historyGroup_noMeasuredSignal_showsADashForTheBestSignal() {
        compose.showPage { WatchlistHistoryGroupRow(WatchlistHistoryGroup(entry, 1, null, System.currentTimeMillis(), "#22AA88"), {}) }
        compose.onNodeWithText("--").assertExists()
        assertEquals(0, compose.onAllNodesWithText("0").fetchSemanticsNodes().size)
    }

    @Test
    fun sightingRow_noMeasuredSignal_showsAnUnknownSignal() {
        compose.showPage { SightingRow(hit(WatchlistHitEntity.RSSI_UNKNOWN), selected = false, onClick = {}) }
        compose.onNodeWithText("- dBm").assertExists()
        assertEquals(0, compose.onAllNodesWithText("0 dBm").fetchSemanticsNodes().size)
    }

    @Test
    fun sightingRow_measuredSignal_showsItsDbm() {
        compose.showPage { SightingRow(hit(-63), selected = false, onClick = {}) }
        compose.onNodeWithText("-63 dBm").assertExists()
    }

    private fun hit(rssi: Int) = WatchlistHitEntity().apply {
        id = 1
        entryId = 1
        matchedField = WatchlistHitEntity.MATCHED_FIELD_SSID
        ssid = "Example Guest"
        bssid = "12:34:56:78:9a:bc"
        this.rssi = rssi
        timestamp = System.currentTimeMillis()
    }
}
