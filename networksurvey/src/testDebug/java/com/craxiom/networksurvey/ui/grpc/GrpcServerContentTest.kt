package com.craxiom.networksurvey.ui.grpc

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.status.DestinationStatus
import com.craxiom.networksurvey.survey.status.StatusDetail
import com.craxiom.networksurvey.survey.status.StatusWord
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.survey.DestinationPageStatus
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The gRPC page on the stateless [GrpcServerContent]. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class GrpcServerContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var form by mutableStateOf(GrpcServerForm(loading = false))
    private val actions = object : GrpcServerActions {
        override fun onEdit(change: (GrpcServerForm) -> GrpcServerForm) {
            form = change(form)
        }

        override fun onSave() {
            calls += "save"
        }

        override fun onSeeOrganizationSettings() {
            calls += "organization"
        }
    }
    private val configured = GrpcServerForm(
        loading = false, host = "grpc.example.net", port = "2621", configured = true, inSetup = true,
        status = GrpcStatus.Ready(ADDRESS),
    )

    @Test
    fun notSetUp_explainsTheStream_andSavesAndTurnsOn() {
        show(form)
        compose.onNodeWithText("gRPC stream").assertIsDisplayed()
        compose.onNodeWithText("Not set up").assertIsDisplayed()
        compose.onNodeWithText("Streams every record live to a gRPC server.").assertIsDisplayed()
        compose.onNodeWithText("What is sent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save and turn on").performClick()
        assertEquals(listOf("save"), calls)
    }

    @Test
    fun configured_showsTheAddress_andSavesChanges() {
        show(configured)
        compose.onNodeWithText("Ready to stream").assertIsDisplayed()
        compose.onNodeWithText(ADDRESS).assertIsDisplayed()
        compose.onNodeWithText("Save changes").assertIsDisplayed()
        compose.onNodeWithText("Saving a new address reconnects the stream. The survey keeps going.").assertDoesNotExist()
    }

    @Test
    fun configuredButOff_saysItIsOffOnTheSurveyScreen() {
        show(configured.copy(inSetup = false, status = GrpcStatus.OffInSurvey(ADDRESS)))
        compose.onNodeWithText("Set up, turned off on the Survey screen").assertIsDisplayed()
    }

    @Test
    fun fieldErrors_areShownUnderTheirFields() {
        show(form.copy(hostError = true, portError = true))
        compose.onNodeWithText("Enter the server's host name or address").assertIsDisplayed()
        compose.onNodeWithText("Enter a port from 1 to 65535").assertIsDisplayed()
    }

    @Test
    fun streaming_showsTheLiveStatus_andThatSavingReconnects() {
        show(configured.copy(status = GrpcStatus.Streaming(ADDRESS)), live(DestinationStatus(StatusWord.LIVE)))
        compose.onNodeWithText("Live").assertIsDisplayed()
        compose.onNodeWithText("Saving a new address reconnects the stream. The survey keeps going.").assertIsDisplayed()
    }

    @Test
    fun streamingToAServerItCantReach_saysWhatTheSurveyScreenSays() {
        val status = DestinationStatus(StatusWord.CANT_CONNECT, StatusDetail.StreamUnreachable(sinceMs = 0L, waiting = 3))
        show(configured.copy(status = GrpcStatus.Streaming(ADDRESS)), live(status))
        compose.onNodeWithText("Can't connect").assertIsDisplayed()
        compose.onNodeWithText("Can't reach $ADDRESS since", substring = true).assertIsDisplayed()
        compose.onNodeWithText("3 waiting to send", substring = true).assertIsDisplayed()
    }

    @Test
    fun unreachable_saysWhichServerAndWhatToCheck() {
        show(configured.copy(status = GrpcStatus.Unreachable(ADDRESS)))
        compose.onNodeWithText("Couldn't reach the server").assertIsDisplayed()
        compose.onNodeWithText("$ADDRESS. Check the address and that the server is running.").assertIsDisplayed()
    }

    @Test
    fun turnedOffByTheOrganization_explainsWhy() {
        show(form.copy(organizationOff = true))
        compose.onNodeWithText("gRPC is turned off by your organization").performClick()
        compose.onNodeWithText("Your organization chose this for this phone. Ask your administrator to change it.").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun typing_editsTheFields_andDoneOnTheLastFieldSaves() {
        compose.showPage { GrpcServerContent(form, actions, SnackbarHostState()) }
        compose.onNode(hasSetTextAction() and hasText("Host")).performTextInput("grpc.example.net")
        compose.onNode(hasSetTextAction() and hasText("Port")).performTextInput("9x")
        compose.onNode(hasSetTextAction() and hasText("Device name")).performScrollTo().performImeAction()
        assertEquals("grpc.example.net", form.host)
        // Only digits reach the port.
        assertEquals("92621", form.port)
        assertEquals(listOf("save"), calls)
    }

    @Test
    fun largeFonts_stillShowSave() {
        compose.showPage(largeFont = true) { GrpcServerContent(configured, actions, SnackbarHostState()) }
        compose.onNodeWithText("Save changes").assertIsDisplayed()
    }

    private fun show(state: GrpcServerForm, liveStatus: DestinationPageStatus? = null) {
        compose.showPage { GrpcServerContent(state, actions, SnackbarHostState(), liveStatus = liveStatus) }
    }

    private fun live(status: DestinationStatus) =
        DestinationPageStatus(Destination.GRPC, status, DestinationInfo(grpcAddress = ADDRESS), nowMs = 0L)

    private companion object {
        const val ADDRESS = "grpc.example.net:2621"
    }
}
