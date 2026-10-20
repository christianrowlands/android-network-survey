package com.craxiom.networksurvey.ui.more

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.main.NavDrawerOption
import com.craxiom.networksurvey.ui.main.NavOption
import com.craxiom.networksurvey.ui.main.more.ExternalLinks
import com.craxiom.networksurvey.ui.main.more.MoreActions
import com.craxiom.networksurvey.ui.main.more.MoreContent
import com.craxiom.networksurvey.ui.main.more.MoreItems
import com.craxiom.networksurvey.ui.main.more.MoreUiState
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The More tab on the stateless [MoreContent]. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class MoreContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : MoreActions {
        override fun onOpen(route: String) {
            calls += "open:$route"
        }

        override fun onOpenExternal(url: String) {
            calls += "link:$url"
        }

        override fun onCopyVersion() {
            calls += "copy"
        }
    }

    private val info = DestinationInfo(
        nsAnalyticsRegistered = true,
        workspaceName = "Field team",
        mqttConfigured = true,
        mqttBrokerLabel = "Depot · broker.example.net",
        communityTargets = setOf(RecordType.CELLULAR),
        openCelliDSelected = true,
        beaconDbSelected = true,
    )

    @Test
    fun everyGroup_withItsRows_andTheSurveyScreensWords() {
        show(info)
        listOf(
            "Destinations", "Field team", "Depot · broker.example.net", "gRPC stream", "Not set up",
            "Cellular to OpenCelliD and BeaconDB · Wi-Fi to BeaconDB", "Tools", "Help and about",
        ).forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("Scan rates, location, battery").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1.62.0").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun destinationsTheOrganizationBlocks_areNotListed_andItsSettingsAre() {
        val policy = SurveyPolicyState.UNMANAGED.copy(
            isManaged = true,
            blockedDestinations = setOf(Destination.NS_ANALYTICS, Destination.COMMUNITY),
        )
        show(info, policy)
        compose.onNodeWithText("NS Analytics").assertDoesNotExist()
        compose.onNodeWithText("Community databases").assertDoesNotExist()
        compose.onNodeWithText("MQTT stream").assertIsDisplayed()
        compose.onNodeWithText("Organization settings").performScrollTo().performClick()
        assertEquals(listOf("open:${NavOption.OrganizationSettings.name}"), calls)
    }

    @Test
    fun eachRow_opensItsPage() {
        show(info)
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("MQTT stream").performScrollTo().performClick()
        compose.onNodeWithText("Wi-Fi spectrum").performScrollTo().performClick()
        compose.onNodeWithText("Acknowledgments").performScrollTo().performClick()
        assertEquals(
            listOf(
                "open:${NavDrawerOption.Settings.name}",
                "open:${NavDrawerOption.MqttBrokerConnection.name}",
                "open:${NavOption.WifiSpectrum.name}",
                "open:${NavOption.Acknowledgments.name}",
            ),
            calls,
        )
    }

    @Test
    fun theHelpLinks_openInTheBrowser_andSaySo() {
        show(info)
        compose.onNodeWithText("User manual").performScrollTo()
            .assert(SemanticsMatcher("opens in the browser") { it.config[SemanticsActions.OnClick].label == "open in your browser" })
            .performClick()
        compose.onNodeWithText("Messaging docs").performScrollTo().performClick()
        compose.onNodeWithText("Report an issue").performScrollTo().performClick()
        compose.onNodeWithText("Privacy policy").performScrollTo().performClick()
        assertEquals(
            listOf(
                "link:${ExternalLinks.USER_MANUAL}",
                "link:${ExternalLinks.MESSAGING_DOCS}",
                "link:${ExternalLinks.REPORT_ISSUE}",
                "link:${ExternalLinks.PRIVACY_POLICY}",
            ),
            calls,
        )
    }

    @Test
    fun theVersionRow_copiesTheVersion() {
        show(info)
        compose.onNodeWithText("Version").performScrollTo()
            .assert(SemanticsMatcher("copies") { it.config[SemanticsActions.OnClick].label == "copy version" })
            .performClick()
        assertEquals(listOf("copy"), calls)
    }

    @Test
    fun atLargeFont_everyRowIsStillThere() {
        show(info, largeFont = true)
        listOf("NS Analytics", "Cellular tools", "OUI lookup", "Watchlist", "Source code", "Version").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }

    private fun show(info: DestinationInfo, policy: SurveyPolicyState = SurveyPolicyState.UNMANAGED, largeFont: Boolean = false) {
        val state = MoreUiState(MoreItems.build(info, policy, "1.62.0"), info)
        compose.showPage(largeFont = largeFont) { MoreContent(state, actions, SnackbarHostState()) }
    }
}
