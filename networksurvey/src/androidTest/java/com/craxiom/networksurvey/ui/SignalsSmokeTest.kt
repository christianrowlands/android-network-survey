package com.craxiom.networksurvey.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.rule.ServiceTestRule
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.gpstest.ui.gnss.model.SignalInfoViewModel
import com.craxiom.networksurvey.services.DestinationTestSupport
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.SmokeTestSupport.activityIntent
import com.craxiom.networksurvey.ui.SmokeTestSupport.grantedPermissions
import com.craxiom.networksurvey.ui.SmokeTestSupport.shell
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The Signals sub-tabs on the real activity, showing live data. Records are injected through the
 * survey service's record processor, the way a scan delivers them (and satellites through the GNSS
 * view model, which reads the receiver directly), so the checks do not depend on what radios are
 * around the phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SignalsSmokeTest {

    private val compose: ComposeTestRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(SmokeTestSupport.SeededStartup())
        .around(GrantPermissionRule.grant(*grantedPermissions()))
        .around(compose)

    @get:Rule
    val serviceRule = ServiceTestRule()

    private val shell = ShellDriver(compose)
    private var scenario: ActivityScenario<NetworkSurveyActivity>? = null
    private var bluetoothWasOn = true

    @Before
    fun setUp() {
        bluetoothWasOn = shell("settings get global bluetooth_on").trim() == "1"
        shell("svc bluetooth enable")
    }

    @After
    fun tearDown() {
        scenario?.close()
        if (!bluetoothWasOn) shell("svc bluetooth disable")
    }

    @Test
    fun bluetooth_showsALiveDevice_filtersIt_andOpensItsDetails() {
        val service = DestinationTestSupport.bindService(serviceRule)
        launch()
        shell.tab("Signals").performClick()
        shell.tab("Bluetooth").performClick()
        shell.waitFor("the Bluetooth list") { has(hasText("Live view")) }

        // The injected device has no signal, so it sorts last, and the list only composes the rows on
        // screen; filtering to its address first keeps real devices nearby from hiding it.
        compose.onNodeWithContentDescription("Filter the Bluetooth device list").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(INJECTED_ADDRESS)
        closeFilterSheet()

        // The list registers once it has bound to the service, so keep sending until the row shows.
        shell.waitFor("the injected device") {
            DestinationTestSupport.injectBluetooth(service)
            has(hasContentDescription(INJECTED_ADDRESS, substring = true))
        }
        compose.onNodeWithText("1 of", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("No name", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("--", useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithContentDescription(INJECTED_ADDRESS, substring = true).performClick()
        shell.waitFor("the Bluetooth details") { has(hasText("Bluetooth device details")) }
        Espresso.pressBack()

        // The filter survives the details page, and clearing it brings every device back.
        compose.onNodeWithContentDescription("Filter the Bluetooth device list. Filter active.").performClick()
        compose.onNodeWithText("Clear filter").performClick()
        closeFilterSheet()
        shell.waitFor("the filter cleared") { !has(hasText("1 of", substring = true)) }
    }

    @Test
    fun wifi_showsAnInjectedNetwork_filtersIt_andOpensDetailsAndSpectrum() {
        val service = DestinationTestSupport.bindService(serviceRule)
        launch()
        shell.tab("Signals").performClick()
        shell.tab("Wi-Fi").performClick()
        shell.waitFor("the Wi-Fi list") { has(hasText("Live view")) }

        // Filter to the injected BSSID first, since the list only composes the rows on screen, and
        // show one row per BSSID.
        compose.onNodeWithContentDescription("Filter the Wi-Fi network list").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(INJECTED_BSSID)
        closeFilterSheet(Application.get().getString(R.string.wifi_filter_title))
        compose.onNodeWithText("Flat · BSSID").performClick()

        // The list registers once it has bound, and every real scan replaces the networks, so keep
        // sending until the row shows, then pause so the next scan does not take it away.
        shell.waitFor("the injected network") {
            DestinationTestSupport.injectWifi(service)
            has(hasContentDescription(INJECTED_BSSID, substring = true))
        }
        compose.onNodeWithContentDescription("Pause updates").performClick()
        compose.onNodeWithText("ns-test-ssid", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("--", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Updates paused", useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithContentDescription(INJECTED_BSSID, substring = true).performClick()
        shell.waitFor("the Wi-Fi details") { has(hasText(Application.get().getString(R.string.wifi_details_title))) }
        Espresso.pressBack()

        compose.onNodeWithContentDescription(Application.get().getString(R.string.open_wifi_spectrum)).performClick()
        shell.waitFor("the spectrum") { has(hasText("Wi-Fi spectrum")) && has(hasText("2.4 GHz")) }
        Espresso.pressBack()

        // The filter and the pause survive both pages; clearing the filter and resuming bring the
        // live list back.
        compose.onNodeWithContentDescription("Resume updates").performClick()
        compose.onNodeWithContentDescription("Filter the Wi-Fi network list. Filter active.").performClick()
        compose.onNodeWithText("Clear filter").performClick()
        closeFilterSheet(Application.get().getString(R.string.wifi_filter_title))
        shell.waitFor("the filter cleared") { !has(hasText(" of ", substring = true)) }
    }

    @Test
    fun cellular_showsAnInjectedServingCell() {
        val service = DestinationTestSupport.bindService(serviceRule)
        launch()
        shell.tab("Signals").performClick()
        shell.tab("Cellular").performClick()

        // Real scans replace the injected cell, and the view registers only once it has bound, so
        // keep sending until it shows.
        shell.waitFor("the injected serving cell") {
            DestinationTestSupport.injectServingLte(service)
            has(hasContentDescription("PCI, $INJECTED_PCI_TEXT"))
        }
        compose.onNodeWithText("Serving cell", useUnmergedTree = true).assertIsDisplayed()
        // The strip shows the scan interval; the state is only read out.
        compose.onNodeWithText("Scan every", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun gnss_mountsStatusAndSky_andOpensTheLegend() {
        launch()
        // A constellation filter left on the phone would hide the injected satellites.
        scenario!!.onActivity { PreferenceUtils.clearGnssFilter(it, Application.getPrefs()) }
        shell.tab("Signals").performClick()
        shell.tab("GNSS").performClick()

        // GNSS starts once the view resumes; real updates replace the injected satellites, so keep
        // sending until the Status page shows.
        shell.waitFor("the Status page") {
            injectSatellites()
            has(hasText(statusPageLabel()))
        }
        compose.onNodeWithText("Status").assertIsSelected()
        compose.onNodeWithContentDescription("Sort by").assertIsDisplayed()
        compose.onNodeWithContentDescription("Filter signals").assertIsDisplayed()

        compose.onNodeWithText("Sky").performClick()
        shell.waitFor("the Sky page") {
            injectSatellites()
            has(hasText("Average C/N0"))
        }
        // The Sky page sorts nothing, so only the filter is left beside the toggle.
        shell.waitFor("the sort action to go") { !has(hasContentDescription("Sort by")) }
        compose.onNodeWithContentDescription("Filter signals").assertIsDisplayed()

        compose.onNodeWithText("Legend").performClick()
        shell.waitFor("the legend") { has(hasText("NAVSTAR GPS (USA)")) }
        Espresso.pressBack()
        shell.waitFor("the legend to close") { !has(hasText("NAVSTAR GPS (USA)")) }

        compose.onNodeWithText("Status").performClick()
        shell.waitFor("the Status page again") { has(hasText(statusPageLabel())) }
    }

    /** The title of the Status page's Fix panel, which the Sky page does not show. */
    private fun statusPageLabel(): String = Application.get().getString(R.string.gnss_status_location_title)

    /** Hands the GNSS view two satellites, one used in the fix, as a GnssStatus update would. */
    private fun injectSatellites() {
        scenario!!.onActivity { activity ->
            val viewModel = ViewModelProvider(activity)[SignalInfoViewModel::class.java]
            val satellites = listOf(
                SatelliteStatus(INJECTED_SVID, GnssType.NAVSTAR, 38f, true, true, true, 55f, 120f),
                SatelliteStatus(INJECTED_SVID + 1, GnssType.GALILEO, 27f, true, true, false, 20f, 250f),
            )
            viewModel.updateStatus(activity, satellites, Application.getPrefs())
        }
    }

    /**
     * Closes the filter sheet and waits until it is gone, so its "Showing N of M" line is too. The
     * search field may have the keyboard up, which the first Back would only hide.
     */
    private fun closeFilterSheet(title: String = "Filter devices") {
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        shell.waitFor("the filter sheet to close") { !has(hasText(title)) }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(activityIntent())
        shell.waitForHeading("Survey")
    }

    private fun has(matcher: SemanticsMatcher): Boolean =
        compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        /** The BSSID `DestinationTestSupport.injectWifi` sends. */
        const val INJECTED_BSSID = "02:00:00:00:00:01"

        /** The address `DestinationTestSupport.injectBluetooth` sends. */
        const val INJECTED_ADDRESS = "02:00:00:00:00:02"

        /** How the Cellular view shows `DestinationTestSupport.INJECTED_PCI`, with its sync sequences. */
        const val INJECTED_PCI_TEXT = "503 (2/167)"

        /** The GPS satellite number the GNSS test injects. */
        const val INJECTED_SVID = 30
    }
}
