package com.craxiom.networksurvey.ui.wifi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.plural
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.wifi.components.WifiFilterSheet
import com.craxiom.networksurvey.ui.wifi.model.WifiDisplayFilter
import com.craxiom.networksurvey.util.WifiBand
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Wi-Fi filter sheet on the shared list filter sheet. No native graphics here: a text field in
 * a sheet never goes idle under Robolectric's native graphics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class WifiFilterSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private var filter by mutableStateOf(WifiDisplayFilter())
    private var managed = false

    @Test
    fun anInactiveFilter_hasNoClear() {
        show()

        compose.onNodeWithText(string(R.string.wifi_filter_title)).assertIsDisplayed()
        compose.onNodeWithText(plural(R.plurals.wifi_filter_showing_count, 12, 9, 12)).assertIsDisplayed()
        compose.onAllNodesWithText("Clear filter").assertCountEquals(0)
    }

    @Test
    fun typing_updatesTheQuery() {
        show()

        compose.onNodeWithText("Search SSID or BSSID").performTextInput("acme")

        assertEquals("acme", filter.searchQuery)
    }

    @Test
    fun theBandPills_switchOnAndOff() {
        show()

        compose.onNodeWithText("5 GHz").assertIsOff().performClick()
        compose.onNodeWithText("5 GHz").assertIsOn()
        compose.onNodeWithText("6 GHz").performClick()

        assertEquals(setOf(WifiBand.GHZ_5, WifiBand.GHZ_6), filter.selectedBands)
    }

    @Test
    fun clear_resetsEverything() {
        filter = WifiDisplayFilter(searchQuery = "acme", selectedBands = setOf(WifiBand.GHZ_24))
        show()

        compose.onNodeWithText("Clear filter").performClick()

        assertEquals(WifiDisplayFilter(), filter)
    }

    @Test
    fun theExclusionListLink_opensIt() {
        show()

        compose.onNodeWithText(string(R.string.wifi_filter_manage_exclusions)).performClick()

        assertEquals(true, managed)
    }

    private fun show() {
        compose.showPage {
            WifiFilterSheet(
                filter = filter,
                visibleApCount = 9,
                totalApCount = 12,
                onQueryChanged = { filter = filter.copy(searchQuery = it) },
                onBandToggled = { band ->
                    filter = filter.copy(selectedBands = if (band in filter.selectedBands) filter.selectedBands - band else filter.selectedBands + band)
                },
                onClear = { filter = WifiDisplayFilter() },
                onManageExclusionList = { managed = true },
                onDismiss = {},
            )
        }
    }
}
