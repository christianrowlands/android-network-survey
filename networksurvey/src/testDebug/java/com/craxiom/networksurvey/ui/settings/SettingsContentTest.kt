package com.craxiom.networksurvey.ui.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.textLayout
import com.craxiom.networksurvey.ui.theme.NsAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Settings page: its groups, the rows that open pickers, and what the organization sets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class SettingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = object : SettingsActions {
        override fun onBack() { calls += "back" }
        override fun onSwitch(key: String, on: Boolean) { calls += "switch $key $on" }
        override fun onAutoStart(on: Boolean) { calls += "autoStart $on" }
        override fun onOverride(on: Boolean) { calls += "override $on" }
        override fun onOpen(picker: SettingsPicker) { calls += "open $picker" }
        override fun onLocked() { calls += "locked" }
        override fun onBlocked() { calls += "blocked" }
        override fun onOpenBatteryOptimization() { calls += "battery" }
        override fun onOpenNsAnalytics() { calls += "nsAnalytics" }
        override fun onOpenUploads() { calls += "uploads" }
        override fun onOpenMapSettings() { calls += "map" }
        override fun onOpenSsidExclusions() { calls += "ssids" }
        override fun onOpenOrganization() { calls += "organization" }
        override fun onCopy(label: String, value: String) { calls += "copy $value" }
    }

    private fun show(state: SettingsUiState, largeFont: Boolean = false) {
        compose.showPage(largeFont = largeFont) { SettingsContent(state, actions, SnackbarHostState()) }
    }

    @Test
    fun theGroups_andTheScanRatesSummary_show() {
        show(SettingsUiState())

        compose.onNodeWithText("SURVEY").assertIsDisplayed()
        compose.onNodeWithText("PHONE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cellular 5 s · Wi-Fi 15 s · Bluetooth 30 s · GNSS 45 s").assertIsDisplayed()
        compose.onNodeWithText("Scan rates").performClick()
        assertEquals(listOf("open SCAN_RATES"), calls)
    }

    @Test
    fun theAccentRow_namesTheAccent_andOpensItsPicker() {
        show(SettingsUiState(accent = NsAccent.GRAPHITE))

        compose.onNodeWithText("Graphite").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Accent color").performClick()

        assertEquals(listOf("open ACCENT"), calls)
    }

    @Test
    fun anUnmanagedPhone_hasNoOrganizationRows() {
        show(SettingsUiState())

        compose.onAllNodesWithText("Organization settings").assertCountEquals(0)
        compose.onAllNodesWithText("Set by your organization").assertCountEquals(0)
    }

    @Test
    fun aSettingTheOrganizationSet_explainsItselfInsteadOfChanging() {
        show(SettingsUiState(managed = true, autoStart = SettingValue(true, locked = true)))

        compose.onNodeWithText("Start survey automatically").performClick()

        assertEquals(listOf("locked"), calls)
        compose.onNodeWithText("Organization settings").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aLockedPicker_explainsItself_andAFreeOneOpens() {
        show(SettingsUiState(batteryThreshold = SettingValue(20, locked = true)))

        compose.onNodeWithText("Pause scanning on low battery").performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.location_provider_title)).performScrollTo().performClick()

        assertEquals(listOf("locked", "open LOCATION_SOURCE"), calls)
    }

    @Test
    fun aBlockedDestination_saysSo_withoutOfferingTheOverride() {
        show(SettingsUiState(managed = true, uploadsBlocked = true))

        compose.onNodeWithText(string(R.string.upload_disabled_via_mdm)).performScrollTo().performClick()

        assertEquals(listOf("blocked"), calls)
    }

    @Test
    fun theDeviceSerial_copiesOnATap() {
        show(SettingsUiState(deviceSerial = "abc123"))

        compose.onNodeWithText("abc123").performScrollTo().performClick()

        assertEquals(listOf("copy abc123"), calls)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atLargeText_longTitlesWrapRatherThanCut() {
        show(SettingsUiState(managed = true), largeFont = true)

        listOf(string(R.string.allow_intent_control_title), string(R.string.settings_upload_title)).forEach { title ->
            compose.onNodeWithText(title).performScrollTo()
            val layout = compose.textLayout(title)
            assertFalse(title, layout.isLineEllipsized(layout.lineCount - 1))
        }
    }
}
