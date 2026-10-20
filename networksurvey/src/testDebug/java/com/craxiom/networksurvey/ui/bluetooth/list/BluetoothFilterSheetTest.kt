package com.craxiom.networksurvey.ui.bluetooth.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothDisplayFilter
import com.craxiom.networksurvey.ui.showPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Bluetooth filter sheet. No native graphics here: a text field in a sheet never goes idle
 * under Robolectric's native graphics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class BluetoothFilterSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private var filter by mutableStateOf(BluetoothDisplayFilter())

    @Test
    fun anInactiveFilter_hasNoClear() {
        show()

        compose.onNodeWithText("Filter devices").assertIsDisplayed()
        compose.onNodeWithText("Showing 5 of 5 devices").assertIsDisplayed()
        compose.onAllNodesWithText("Clear filter").assertCountEquals(0)
    }

    @Test
    fun typing_updatesTheQuery() {
        show()

        compose.onNodeWithText("Search name, address or company").performTextInput("keys")

        assertEquals("keys", filter.query)
    }

    @Test
    fun theToggles_switchOnAndOff() {
        show()

        compose.onNodeWithText("Named only").assertIsOff().performClick()
        compose.onNodeWithText("Named only").assertIsOn()
        compose.onNodeWithText("Trackers only").performClick()

        assertEquals(BluetoothDisplayFilter(namedOnly = true, trackersOnly = true), filter)
    }

    @Test
    fun clear_resetsEverything() {
        filter = BluetoothDisplayFilter(query = "keys", trackersOnly = true)
        show()

        compose.onNodeWithText("Clear filter").performClick()

        assertEquals(BluetoothDisplayFilter(), filter)
    }

    private fun show() {
        compose.showPage {
            BluetoothFilterSheet(
                filter = filter,
                visibleCount = 5,
                totalCount = 5,
                onQueryChanged = { filter = filter.copy(query = it) },
                onNamedOnlyChanged = { filter = filter.copy(namedOnly = it) },
                onTrackersOnlyChanged = { filter = filter.copy(trackersOnly = it) },
                onClear = { filter = BluetoothDisplayFilter() },
                onDismiss = {},
            )
        }
    }
}
