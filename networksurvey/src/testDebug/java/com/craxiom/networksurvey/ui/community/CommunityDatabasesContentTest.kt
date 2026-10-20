package com.craxiom.networksurvey.ui.community

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.plural
import com.craxiom.networksurvey.ui.showPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Community databases page on the stateless [CommunityDatabasesContent]. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class CommunityDatabasesContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : CommunityDatabasesActions {
        override fun onSetOpenCelliD(on: Boolean) { calls += "ocid:$on" }
        override fun onSetBeaconDb(on: Boolean) { calls += "beacondb:$on" }
        override fun onSetAutoUpload(on: Boolean) { calls += "auto:$on" }
        override fun onUploadNow() { calls += "upload" }
        override fun onCancelUpload() { calls += "cancel" }
    }
    private val ready = UploadUiState(
        cellularQueueCount = 1204, wifiQueueCount = 351, uploadButtonEnabled = true,
        openCelliDOn = true, openCelliDKey = OpenCelliDKey.ANONYMOUS, beaconDbOn = true,
    )

    @Test
    fun targetsAndUploads_toggleAndUpload() {
        show(ready)
        compose.onNodeWithText("Cellular · anonymous upload").assertIsDisplayed()
        compose.onNodeWithText("BeaconDB").performClick()
        compose.onNodeWithText("Upload automatically").performClick()
        compose.onNodeWithText(plural(R.plurals.community_ready_both, 351, "1,204", "351")).performClick()
        assertEquals(listOf("beacondb:false", "auto:true", "upload"), calls)
    }

    @Test
    fun onlyOneKindReady_saysJustThatKind() {
        show(ready.copy(cellularQueueCount = 0, wifiQueueCount = 1))
        compose.onNodeWithText(plural(R.plurals.community_ready_wifi, 1, "1")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun openCelliDWithoutAKey_saysWhatItNeeds() {
        show(ready.copy(openCelliDKey = OpenCelliDKey.MISSING))
        compose.onNodeWithText("Cellular · needs your API key").assertIsDisplayed()
    }

    @Test
    fun organizationSetsAutomaticUploads_theRowExplainsInsteadOfToggling() {
        show(ready.copy(autoUploadLocked = true, autoUploadEnabled = true))
        compose.onNodeWithText("Upload automatically").performClick()
        compose.onNodeWithText("Your organization chose this for this phone. Ask your administrator to change it.").assertIsDisplayed()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun runningUpload_canBeCancelled() {
        show(ready.copy(uploadProgress = UploadProgressState.InProgress(progress = 40)))
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertEquals(listOf("cancel"), calls)
    }

    @Test
    fun notAllowed_saysSo() {
        show(UploadUiState(visible = false))
        compose.onNodeWithText("Your organization does not allow uploads to public databases on this phone.").assertIsDisplayed()
        compose.onNodeWithText("Upload now").assertDoesNotExist()
    }

    @Test
    fun largeFonts_stillShowUploadSettings() {
        compose.showPage(largeFont = true) { CommunityDatabasesContent(ready, actions) }
        compose.onNodeWithText("Upload settings").performScrollTo().assertIsDisplayed()
    }

    private fun show(state: UploadUiState) = compose.showPage { CommunityDatabasesContent(state, actions) }
}
