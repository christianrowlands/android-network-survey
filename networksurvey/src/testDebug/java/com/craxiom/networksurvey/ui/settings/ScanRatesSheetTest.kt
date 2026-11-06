package com.craxiom.networksurvey.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The scan rates sheet's Wi-Fi interval, which means something else once an NS Scout is paired. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class ScanRatesSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val rates = ScanRate.entries.associateWith { SettingValue(it.defaultSeconds) }

    @Test
    fun withoutAScout_theWifiIntervalKeepsItsOwnDescription() {
        show(rates, scoutName = null)

        compose.onNodeWithText(string(R.string.wifi_scan_interval_description)).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("the Scout listens continuously", substring = true).assertCountEquals(0)
    }

    @Test
    fun withAScout_theWifiIntervalIsHowOftenEachNetworkIsSaved() {
        show(rates, scoutName = "NS-ABCD")

        compose.onNodeWithText("With NS-ABCD: how often each network and client is saved. During a survey the Scout listens continuously.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aLockedWifiInterval_saysItAlsoSetsTheScoutsCadence() {
        val locked = rates + (ScanRate.WIFI to SettingValue(30, locked = true))
        show(locked, scoutName = "NS-A1B2")

        compose.onNodeWithText(string(R.string.settings_wifi_rate_scout_locked, "NS-A1B2"), substring = true)
            .performScrollTo().assertIsDisplayed()
    }

    private fun show(rates: Map<ScanRate, SettingValue<Int>>, scoutName: String?) {
        compose.showPage { ScanRatesSheet(rates = rates, onSave = {}, onLocked = {}, onDismiss = {}, scoutName = scoutName) }
    }
}
