package com.craxiom.networksurvey.ui.nsanalytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.LinkAnnotation
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.data.api.NsAnalyticsQrData
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.status.DeliveryQueues
import com.craxiom.networksurvey.survey.status.DestinationStatusRules
import com.craxiom.networksurvey.survey.status.NsAnalyticsQueue
import com.craxiom.networksurvey.ui.survey.DestinationPageStatus
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.ui.common.CameraPermissionNotice
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.common.PermissionRequest
import com.craxiom.networksurvey.ui.showPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The NS Analytics page: the connect walkthrough, a refused camera, the registration dialogs and
 * their errors, and the connected page.
 */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class NsAnalyticsPagesTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val qr = NsAnalyticsQrData("token", "ws-1", "https://api.example.com", "Acme")

    @Test
    fun walkthrough_showsTheThreeStepsWithTheWorkspaceAddress() {
        showWalkthrough()
        compose.onNodeWithText(
            "Go to analytics.networksurvey.app, open Devices and choose Register. No workspace yet? Create one there first."
        ).assertIsDisplayed()
        compose.onNodeWithText("Scan the QR code it shows").assertIsDisplayed()
        compose.onNodeWithText("Records upload about every 15 minutes while Upload automatically is on.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun refusedCamera_offersAllow_thenOpenSettingsOnceAndroidCannotAsk() {
        val request = PermissionRequest(launch = { calls += "ask" }, grantedNow = { false }, onGranted = {})
        request.access = PermissionAccess.DENIED
        compose.showPage { Column { CameraPermissionNotice(request) } }
        compose.onNodeWithText("The camera is needed to scan the code.").assertIsDisplayed()
        compose.onNodeWithText("Allow").performClick()
        assertEquals(listOf("ask"), calls)

        request.access = PermissionAccess.BLOCKED
        compose.onNodeWithText("Open Settings").assertIsDisplayed()
    }

    @Test
    fun noCamera_scanNeitherAsksNorOpensTheScanner_andSaysWhy() {
        val request = PermissionRequest(
            launch = { calls += "ask" },
            grantedNow = { true },
            onGranted = { calls += "scanner" },
            hardwarePresent = { false },
        )
        compose.showPage { Column { CameraPermissionNotice(request) } }
        request.request()
        compose.onNodeWithText("This device has no camera, so it cannot scan a code.").assertIsDisplayed()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun scannedCode_asksToConfirmTheWorkspace() {
        showDialogs(NsAnalyticsConnectionUiState(pendingQrData = qr, showRegistrationConfirmDialog = true))
        compose.onNodeWithText("Connect this phone to Acme?").assertIsDisplayed()
        compose.onNodeWithText("Connect").performClick()
        assertEquals("confirm", calls.first())
    }

    @Test
    fun eachRegistrationError_hasItsOwnExplanation_andTryAgain() {
        val cases = mapOf(
            RegistrationError.Network to "Couldn't reach NS Analytics. Check the connection and try again.",
            RegistrationError.CodeRejected to "This code has expired or was already used. Create a new one on the Devices page.",
            RegistrationError.DeviceLimit to "This workspace has no room for another phone. Ask the workspace owner.",
            RegistrationError.License to "This workspace's license has expired. Ask the workspace owner.",
            RegistrationError.Server to "NS Analytics had a problem. Try again in a few minutes.",
            RegistrationError.Other("Invalid request body") to "Invalid request body",
        )
        val state = androidx.compose.runtime.mutableStateOf(NsAnalyticsConnectionUiState())
        compose.showPage { NsAnalyticsDialogs(state.value, NsAnalyticsDialog.NONE, actions()) }
        for ((error, text) in cases) {
            state.value = NsAnalyticsConnectionUiState(pendingQrData = qr, registrationError = error)
            compose.onNodeWithText(text).assertIsDisplayed()
        }
        compose.onNodeWithText("Try again").performClick()
        assertEquals("retry", calls.first())
    }

    @Test
    fun connected_showsTheWorkspaceAndUploads_andDisconnectWaitsForTheSurvey() {
        compose.showPage {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NsAnalyticsConnectedContent(
                    NsAnalyticsConnectionUiState(isRegistered = true, workspaceName = "Acme", queuedRecords = 198, isSurveyActive = true),
                    object : NsAnalyticsConnectedActions {
                        override fun onUploadNow() { calls += "upload" }
                        override fun onDisconnect() { calls += "disconnect" }
                    },
                )
            }
        }
        compose.onNodeWithText("Connected to Acme").assertIsDisplayed()
        compose.onNodeWithText("198 records waiting").assertIsDisplayed()
        compose.onNodeWithText("Upload now").performClick()
        compose.onNodeWithText("Stop the survey first").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Disconnect this phone").performClick()
        assertEquals(listOf("upload", "disconnect"), calls)
    }

    @Test
    fun connected_countsTheRecordsTheStatusCounts_groupedForTheLanguage() {
        // The view model's own poll read the queue a moment later than the status did
        val queues = DeliveryQueues(nsAnalytics = NsAnalyticsQueue(pending = 8696, nextUploadMs = 16 * MINUTE_MS))
        val status = DestinationPageStatus(
            Destination.NS_ANALYTICS,
            DestinationStatusRules.nsAnalytics(queues.nsAnalytics, autoUpload = true),
            DestinationInfo(),
            nowMs = 0L,
            queues = queues,
        )
        val viewModelState = NsAnalyticsConnectionUiState(isRegistered = true, workspaceName = "Acme", queuedRecords = 8701)
        compose.showPage {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NsAnalyticsConnectedContent(viewModelState.withQueueOf(status), object : NsAnalyticsConnectedActions {}, status)
            }
        }
        compose.onNodeWithText("8,696 waiting · next upload in about 16 min").assertIsDisplayed()
        compose.onNodeWithText("8,696 records waiting").assertIsDisplayed()
    }

    @Test
    fun clearQueue_confirmsWithTheCountGrouped() {
        showDialogs(NsAnalyticsConnectionUiState(isRegistered = true, queuedRecords = 1006), NsAnalyticsDialog.CLEAR_QUEUE)
        compose.onNodeWithText("Delete 1,006 records waiting to upload? They won't be sent.").assertIsDisplayed()
    }

    @Test
    fun disconnect_confirmsAndKeepsTheQueue() {
        showDialogs(NsAnalyticsConnectionUiState(isRegistered = true, workspaceName = "Acme"), NsAnalyticsDialog.DISCONNECT)
        compose.onNodeWithText(
            "This phone stops uploading to Acme. Records waiting to upload stay on this phone and upload if you connect it again."
        ).assertIsDisplayed()
        compose.onNodeWithText("Disconnect").performClick()
        assertEquals("disconnect", calls.first())
    }

    @Test
    fun walkthrough_saysWhatNsAnalyticsIs_andLinksTheWebAppAndTheWebsite() {
        showWalkthrough()
        compose.onNodeWithText("See your phone's surveys on one map").assertIsDisplayed()

        // The address is a link inside step 1's text; tap it through its listener
        val step1 = compose.onNodeWithText(NsAnalyticsConstants.NS_ANALYTICS_WEB_HOST, substring = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].first()
        val link = step1.getLinkAnnotations(0, step1.length).single()
        assertEquals(NsAnalyticsConstants.NS_ANALYTICS_WEB_HOST, step1.substring(link.start, link.end))
        val url = link.item as LinkAnnotation.Url
        url.linkInteractionListener!!.onClick(url)

        compose.onNodeWithText("Learn more about NS Analytics").performScrollTo().performClick()
        assertEquals(listOf(NsAnalyticsConstants.NS_ANALYTICS_WEB_URL, NsAnalyticsConstants.NS_ANALYTICS_LEARN_MORE_URL), calls)
    }

    @Test
    fun largeFonts_stillShowTheLastStep() {
        showWalkthrough(largeFont = true)
        compose.onNodeWithText("Start surveying").performScrollTo().assertIsDisplayed()
    }

    private fun showWalkthrough(largeFont: Boolean = false) = compose.showPage(largeFont = largeFont) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            NsAnalyticsConnectWalkthrough(uploadFrequencyMinutes = 15, onOpenUrl = { calls += it })
        }
    }

    private fun showDialogs(state: NsAnalyticsConnectionUiState, dialog: NsAnalyticsDialog = NsAnalyticsDialog.NONE) =
        compose.showPage { NsAnalyticsDialogs(state, dialog, actions()) }

    private fun actions() = object : NsAnalyticsDialogActions {
        override fun onConfirmRegistration() { calls += "confirm" }
        override fun onRetryRegistration() { calls += "retry" }
        override fun onConfirmDisconnect() { calls += "disconnect" }
    }
}

private const val MINUTE_MS = 60_000L
