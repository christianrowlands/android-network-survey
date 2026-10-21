package com.craxiom.networksurvey.ui.mqtt

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.status.DestinationStatus
import com.craxiom.networksurvey.survey.status.StatusWord
import com.craxiom.networksurvey.ui.common.CameraPermissionNotice
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.common.PermissionRequest
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.survey.DestinationPageStatus
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The MQTT page on the stateless [MqttBrokersContent]: brokers, adding one, the organization's broker. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class MqttBrokersContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()
    private val lab = BrokerRowUi("a", "Lab", "10.0.0.40", 1883, tlsEnabled = false, username = "")
    private val acme = BrokerRowUi("b", "Acme", "broker.example.com", 8883, tlsEnabled = true, username = "surveyor-07")

    @Test
    fun noBrokers_explainsAndOffersAddAndScan() {
        show(MqttBrokersUiState(loading = false))
        compose.onNodeWithText("No broker saved yet. MQTT streams survey records live to a broker you or your team runs.")
            .assertIsDisplayed()
        compose.onNodeWithText("Add a broker").performClick()
        compose.onNodeWithText("Scan a QR code").performClick()
        assertEquals(listOf("add", "scan"), actions.calls)
    }

    @Test
    fun brokers_showTheSelectedOneWithItsLiveStatus_andSelectOrEdit() {
        show(
            MqttBrokersUiState(loading = false, brokers = listOf(lab, acme), activeId = "b", streaming = true),
            DestinationPageStatus(Destination.MQTT, DestinationStatus(StatusWord.LIVE), DestinationInfo(), nowMs = 0L),
        )
        compose.onNodeWithText("broker.example.com:8883 · TLS · surveyor-07").assertIsDisplayed()
        // The status panel and the selected broker's label say the same thing
        compose.onAllNodesWithText("Live").assertCountEquals(2)
        compose.onNodeWithText("Acme").assertIsSelected()

        compose.onNodeWithText("Lab").performClick()
        compose.onNodeWithContentDescription("Edit Acme").performClick()
        assertEquals(listOf("select:a", "edit:b"), actions.calls)
    }

    @Test
    fun brokersSavedButNoneSelected_asksToChooseOne() {
        show(MqttBrokersUiState(loading = false, brokers = listOf(lab)))
        compose.onNodeWithText("Choose a broker to use.").assertIsDisplayed()
    }

    @Test
    fun thisPhone_editsTheClientIdAndDeviceStatus() {
        show(MqttBrokersUiState(loading = false, brokers = listOf(lab), activeId = "a", clientId = "phone-1"))
        compose.onNodeWithText("Send device status").performScrollTo().performClick()
        compose.onNodeWithText("phone-1").performScrollTo().performClick()
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf("deviceStatus:false", "clientId:phone-1"), actions.calls)
    }

    @Test
    fun organizationBroker_isLocked_andTheSheetLeadsToTheOverride() {
        show(
            MqttBrokersUiState(
                loading = false,
                brokers = listOf(lab),
                organizationBroker = OrganizationBrokerUi("mqtt.example.org", 8883, true),
                hasOwnBrokers = true,
            )
        )
        compose.onNodeWithText("Your organization provides this broker; it cannot be edited or removed.").assertIsDisplayed()
        compose.onNodeWithText("Your own brokers are kept for when you use your own settings.").assertIsDisplayed()
        compose.onNodeWithText("Add a broker").assertDoesNotExist()

        compose.onNodeWithContentDescription("MQTT broker, mqtt.example.org, selected, set by your organization").performClick()
        compose.onNodeWithText("Use my own settings").performClick()
        compose.onNodeWithText("Use your own settings?").assertIsDisplayed()
    }

    @Test
    fun largeFonts_stillShowAddABroker() {
        compose.showPage(largeFont = true) {
            MqttBrokersContent(MqttBrokersUiState(loading = false, brokers = listOf(lab, acme), activeId = "a"), actions, SnackbarHostState())
        }
        compose.onNodeWithText("Add a broker").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun refusedCamera_bringsTheNoticeIntoView_whenScanSatAtTheBottomEdge() {
        val camera = PermissionRequest(launch = {}, grantedNow = { false }, onGranted = {})
        val brokers = (1..12).map { BrokerRowUi("id$it", "Broker $it", "10.0.0.$it", 1883, tlsEnabled = false, username = "") }
        compose.showPage {
            MqttBrokersContent(MqttBrokersUiState(loading = false, brokers = brokers, activeId = "id1"), actions, SnackbarHostState()) {
                CameraPermissionNotice(camera)
            }
        }
        // Scrolling to the row leaves it at the bottom edge, with the notice's place just below.
        compose.onNodeWithText("Scan a QR code").performScrollTo()

        camera.access = PermissionAccess.DENIED
        compose.onNodeWithText("The camera is needed to scan the code.").assertIsDisplayed()
    }

    private fun show(state: MqttBrokersUiState, status: DestinationPageStatus? = null) =
        compose.showPage { MqttBrokersContent(state, actions, SnackbarHostState(), status = status) }

    private class RecordingActions : MqttBrokersActions {
        val calls = mutableListOf<String>()
        override fun onSelect(id: String) { calls += "select:$id" }
        override fun onEdit(id: String) { calls += "edit:$id" }
        override fun onAdd() { calls += "add" }
        override fun onScan() { calls += "scan" }
        override fun onSetClientId(clientId: String) { calls += "clientId:$clientId" }
        override fun onSetDeviceStatus(enabled: Boolean) { calls += "deviceStatus:$enabled" }
        override fun onUseOwnSettings() { calls += "useOwn" }
    }
}
