package com.craxiom.networksurvey.ui.scout.firmware

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.scout.firmware.FirmwareComparison
import com.craxiom.networksurvey.scout.firmware.FirmwareConsent
import com.craxiom.networksurvey.scout.firmware.FirmwareInstallPhase
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.scout.firmware.FirmwareUpdateStates.NAME
import com.craxiom.networksurvey.ui.theme.NsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The firmware update screen's stages on the stateless [FirmwareUpdateContent]. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class FirmwareUpdateContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : FirmwareUpdateActions {
        override fun onBack() {
            calls += "back"
        }

        override fun onUpdate() {
            calls += "update"
        }

        override fun onCancel() {
            calls += "cancel"
        }

        override fun onResume() {
            calls += "resume"
        }

        override fun onStartOver() {
            calls += "start over"
        }

        override fun onDone() {
            calls += "done"
        }

        override fun onAccept(consent: FirmwareConsent, accepted: Boolean) {
            calls += "accept $consent $accepted"
        }

        override fun onFixBlocker(blocker: FirmwareBlocker) {
            calls += "fix $blocker"
        }
    }

    private fun show(state: FirmwareUpdateUi) {
        compose.setContent { NsTheme(darkTheme = false) { FirmwareUpdateContent(state, actions) } }
    }

    @Test
    fun aPermissionBlocker_offersAllow_untilOnlySettingsCanGrantIt() {
        val state = FirmwareUpdateStates.ui(FirmwareStage.Offer(FirmwareUpdateStates.offer())).copy(blocker = FirmwareBlocker.PERMISSION)
        var access by mutableStateOf(PermissionAccess.DENIED)
        compose.setContent { NsTheme(darkTheme = false) { FirmwareUpdateContent(state.copy(nearbyAccess = access), actions) } }
        compose.onNodeWithText("Allow").assertIsDisplayed()

        access = PermissionAccess.BLOCKED

        compose.onNodeWithText("Open Settings").assertIsDisplayed()
    }

    @Test
    fun aNewerRelease_showsFromAndTo_thePreflight_andUpdate() {
        show(FirmwareUpdateStates.ui(FirmwareStage.Offer(FirmwareUpdateStates.offer())))

        compose.onNodeWithContentDescription("From firmware 0.1.0 to 0.2.0").assertIsDisplayed()
        compose.onNodeWithText("Released", substring = true).assertIsDisplayed()
        compose.onNodeWithText("then takes about a minute. Keep $NAME powered and next to this phone", substring = true).assertIsDisplayed()
        compose.onNode(hasText("Update") and hasClickAction()).assertIsEnabled().performClick()
        assertEquals(listOf("update"), calls)
    }

    @Test
    fun theSameVersion_needsTheExplicitReinstallConsent() {
        val offer = FirmwareUpdateStates.offer(FirmwareComparison.SAME)
        val state = FirmwareUpdateStates.ui(FirmwareStage.Offer(offer), installed = "0.2.0")
        show(state)

        compose.onNodeWithText("$NAME already runs 0.2.0, the latest release").assertIsDisplayed()
        // The header never reads "0.2.0 → 0.2.0"
        compose.onNodeWithText("0.2.0 · latest release").assertIsDisplayed()
        compose.onAllNodesWithText("0.2.0 → 0.2.0").assertCountEquals(0)
        compose.onNodeWithText("Reinstall 0.2.0").assertIsNotEnabled()
        compose.onNodeWithText("Reinstall the version the Scout already runs").performScrollTo().performClick()
        assertEquals(listOf("accept ${FirmwareConsent.NotNewer(FirmwareComparison.SAME)} true"), calls)
    }

    @Test
    fun theSameVersion_isOfferedOnceAccepted() {
        val offer = FirmwareUpdateStates.offer(FirmwareComparison.SAME)
        show(FirmwareUpdateStates.ui(FirmwareStage.Offer(offer), installed = "0.2.0").copy(accepted = setOf(FirmwareConsent.NotNewer(FirmwareComparison.SAME))))

        compose.onNodeWithText("Reinstall 0.2.0").assertIsEnabled()
    }

    @Test
    fun recording_blocksTheUpdate_withNoAction() {
        show(FirmwareUpdateStates.ui(FirmwareStage.Offer(FirmwareUpdateStates.offer()), blocker = FirmwareBlocker.RECORDING))

        compose.onNodeWithText("Can't update now").assertIsDisplayed()
        compose.onNodeWithText("Stop the survey before updating $NAME.").assertIsDisplayed()
        compose.onNode(hasText("Update") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun bluetoothOff_offersItsOneFix() {
        show(FirmwareUpdateStates.ui(FirmwareStage.Checking, blocker = FirmwareBlocker.BLUETOOTH_OFF))

        compose.onNodeWithText("Turn on Bluetooth").performClick()
        assertEquals(listOf("fix BLUETOOTH_OFF"), calls)
    }

    @Test
    fun theTransfer_showsItsStepsAndPercent_andCancelAsksFirst() {
        show(FirmwareUpdateStates.ui(FirmwareUpdateStates.installing(FirmwareInstallPhase.Transferring(500_000, 1_000_000))))

        compose.onNodeWithText("Downloading").assertIsDisplayed()
        compose.onNodeWithText("Sending firmware to the Scout").assertIsDisplayed()
        compose.onNodeWithText("50% sent").assertIsDisplayed()
        compose.onNodeWithText("Keep the phone next to $NAME until the update finishes.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel update").performClick()
        compose.onNodeWithText("Cancel the update?").assertIsDisplayed()
        compose.onNodeWithText("Keep updating").assertIsDisplayed()
        compose.onAllNodesWithText("Cancel update")[1].performClick()
        assertEquals(listOf("cancel"), calls)
    }

    @Test
    fun afterOtaEnd_nothingCanCancel() {
        show(FirmwareUpdateStates.ui(FirmwareUpdateStates.installing(FirmwareInstallPhase.Restarting)))

        compose.onNodeWithText("The Scout is restarting").assertIsDisplayed()
        compose.onAllNodesWithText("Cancel update").assertCountEquals(0)
    }

    @Test
    fun aFinishedUpdate_saysTheNewVersion_andDone() {
        show(FirmwareUpdateStates.ui(FirmwareUpdateStates.finished))

        compose.onNodeWithText("Updated to 0.2.0").assertIsDisplayed()
        compose.onNodeWithText("$NAME is ready to survey.").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf("done"), calls)
    }

    @Test
    fun aRollback_isReported_withStartOver() {
        show(FirmwareUpdateStates.ui(FirmwareUpdateStates.rolledBack))

        compose.onNodeWithText("Update not installed").assertIsDisplayed()
        compose.onNodeWithText("$NAME went back to firmware 0.1.0: the new firmware didn't start correctly.").assertIsDisplayed()
        compose.onAllNodesWithText("Resume update").assertCountEquals(0)
        compose.onNodeWithText("Start over").performClick()
        assertEquals(listOf("start over"), calls)
    }

    @Test
    fun aLostConnection_canResume() {
        show(FirmwareUpdateStates.ui(FirmwareUpdateStates.connectionLost))

        compose.onNodeWithText("Resume update").performClick()
        assertEquals(listOf("resume"), calls)
    }

    @Test
    fun theTopBarBack_asksFirst_whileTheTransferCanStop() {
        show(FirmwareUpdateStates.ui(FirmwareUpdateStates.installing(FirmwareInstallPhase.Transferring(500_000, 1_000_000))))

        compose.onNodeWithContentDescription(BACK).performClick()

        compose.onNodeWithText("Cancel the update?").assertIsDisplayed()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun theTopBarBack_staysPut_afterOtaEnd() {
        show(FirmwareUpdateStates.ui(FirmwareUpdateStates.installing(FirmwareInstallPhase.Restarting)))

        compose.onNodeWithContentDescription(BACK).performClick()

        compose.onAllNodesWithText("Cancel the update?").assertCountEquals(0)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun theTopBarBack_leaves_whenNothingRuns() {
        show(FirmwareUpdateStates.ui(FirmwareStage.Offer(FirmwareUpdateStates.offer())))

        compose.onNodeWithContentDescription(BACK).performClick()

        assertEquals(listOf("back"), calls)
    }

    @Test
    fun aDownload_canBeCanceled() {
        show(FirmwareUpdateStates.ui(FirmwareStage.Downloading(FirmwareUpdateStates.offer())))

        compose.onNodeWithText("Cancel update").performClick()
        compose.onNodeWithText("Cancel the update?").assertIsDisplayed()
        compose.onAllNodesWithText("Cancel update")[1].performClick()

        assertEquals(listOf("cancel"), calls)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals(count: Int) =
        assertEquals(count, fetchSemanticsNodes().size)

    private companion object {
        const val BACK = "Back"
    }
}
