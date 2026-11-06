package com.craxiom.networksurvey.ui.scout

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.board.v1.BoardModel
import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.scout.ScoutDrops
import com.craxiom.networksurvey.scout.firmware.FirmwareReleaseStatus
import com.craxiom.networksurvey.scout.link.ScoutAdvertisement
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure
import com.craxiom.networksurvey.scout.model.NearbyScout
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutLinkDetails
import com.craxiom.networksurvey.scout.model.ScoutPairingState
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.main.more.ExternalLinks
import com.craxiom.networksurvey.ui.plural
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.theme.NsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The NS Scout page in both shapes and its Add a Scout sheet, on the stateless [ScoutPageContent]. */
@RunWith(RobolectricTestRunner::class)
// Espresso reflects on InputManager.getInstance(), which API 37 removed; see SurveyScreenContentTest.
@Config(sdk = [35])
class ScoutPageContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : ScoutPageActions {
        override fun onSetSource(source: WifiSource) {
            calls += "source:$source"
        }

        override fun onRemove() {
            calls += "remove"
        }

        override fun onErase() {
            calls += "erase"
        }

        override fun onStopSurvey() {
            calls += "stop"
        }

        override fun onShareInstallLink() {
            calls += "share"
        }

        override fun onStartScan() {
            calls += "scan"
        }

        override fun onPair(nearby: NearbyScout) {
            calls += "pair:${nearby.name}"
        }

        override fun onUpdateFirmware() {
            calls += "update firmware"
        }

        override fun onAllowNearby() {
            calls += "allow"
        }

        override fun onDismissPairing() {
            calls += "dismiss"
        }
    }

    @Test
    fun paired_firmwareLineSaysUpToDate_andTheRowsOpenTheUpdate() {
        show(paired(connected(capturing = false)).copy(firmwareStatus = FirmwareReleaseStatus.UpToDate))
        compose.onNodeWithText(string(R.string.scout_info_firmware_up_to_date, "0.1.0")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_fw_row_checks)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_fw_title)).performScrollTo().performClick()
        assertEquals(listOf("update firmware"), calls)
    }

    @Test
    fun paired_anAvailableUpdate_isNamed_andBlockedWhileRecording() {
        show(paired(connected(capturing = true), recording = true).copy(firmwareStatus = FirmwareReleaseStatus.UpdateAvailable("0.2.0")))
        compose.onNodeWithText(string(R.string.scout_info_firmware_available, "0.1.0")).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.scout_fw_row_recording))[0].performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_fw_title)).performScrollTo().performClick()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun noScout_explainsTheScout_andOffersAddAScout() {
        show(ScoutPageUi())
        compose.onNodeWithText(string(R.string.scout_intro_listens)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_install_heading)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_install_share)).performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.scout_add)).assertIsDisplayed().performClick()
        // The sheet starts the scan and says how to make a Scout show up
        compose.onNodeWithText(string(R.string.scout_pair_hint)).assertIsDisplayed()
        assertEquals(listOf("share", "scan"), calls)
    }

    @Test
    fun notAllowed_saysTheOrganizationDoesNotUseIt_andOffersNothing() {
        show(ScoutPageUi(allowed = false))
        compose.onNodeWithText(string(R.string.scout_organization_off)).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText(string(R.string.scout_add)).fetchSemanticsNodes().size)
    }

    @Test
    fun paired_showsStatusSourceNowAndDetails() {
        show(paired(connected(capturing = false)))
        compose.onNodeWithText(string(R.string.scout_word_connected)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_source_note)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_now_doing_waiting)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ESP32-C5-DevKitC-1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("USB powered").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText(string(R.string.scout_source_phone)).performScrollTo().performClick()
        assertEquals(listOf("source:PHONE"), calls)
    }

    @Test
    fun paired_dataMissed_listsWhatWasLost() {
        val drops = ScoutDrops(records = 3, missedNotifications = 1)
        show(paired(connected(capturing = true), details = details().copy(drops = drops)))
        val text = plural(R.plurals.scout_missed_records, 3, "3") + string(R.string.survey_dot_separator) +
            plural(R.plurals.scout_missed_link, 1, "1")
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun recording_locksTheSource_andOffersStopSurvey() {
        show(paired(connected(capturing = true), recording = true))
        compose.onNodeWithText(string(R.string.scout_source_locked_scout)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_source_phone)).assertIsNotEnabled()
        compose.onNodeWithText(string(R.string.survey_stop)).performClick()
        assertEquals(listOf("stop"), calls)
    }

    @Test
    fun remove_asksFirst_withTheBluetoothSettingsFollowUp() {
        show(paired(connected(capturing = false)))
        compose.onNodeWithText(string(R.string.scout_remove)).performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.scout_remove_bluetooth, NAME)).assertIsDisplayed()
        // No saved setup records a Scout-only type yet, so no count is shown
        assertEquals(0, compose.onAllNodesWithText("saved setup", substring = true).fetchSemanticsNodes().size)
        compose.onNodeWithText(string(R.string.remove)).performClick()
        assertEquals(listOf("remove"), calls)
    }

    @Test
    fun remove_whileRecording_goesStraightToTheViewModel_whichRefusesIt() {
        show(paired(connected(capturing = true), recording = true))
        compose.onNodeWithText(string(R.string.scout_remove_blocked)).performScrollTo().performClick()
        assertEquals(listOf("remove"), calls)
        assertEquals(0, compose.onAllNodesWithText(string(R.string.scout_remove_title, NAME)).fetchSemanticsNodes().size)
    }

    @Test
    fun erase_withoutALink_saysToConnectFirst() {
        show(paired(ScoutState.OutOfRange(SCOUT, batteryMayBeEmpty = false), details = ScoutLinkDetails()))
        compose.onNodeWithText(string(R.string.scout_erase_needs_link)).performScrollTo().performClick()
        assertEquals(listOf("erase"), calls)
    }

    @Test
    fun pairingSheet_rowsSayWhatEachScoutCanDo_andPairAsksToReplaceAnother() {
        val pairing = ScoutPairingUi(nearby = listOf(nearby("C3D4", pairable = true, hasBond = false), nearby("1234", pairable = false, hasBond = true)), scanning = true)
        show(paired(connected(capturing = false)), pairing = pairing, overlay = ScoutOverlay.PAIRING)
        compose.onNodeWithText(rowDetail(R.string.scout_pair_status_ready)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_pair_status_other_phone)).assertIsDisplayed()
        // Only the ready Scout has Pair; with another Scout paired, it asks first
        compose.onNodeWithText(string(R.string.scout_pair_action)).performClick()
        compose.onNodeWithText(string(R.string.scout_replace_title, NAME)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_replace_confirm)).performClick()
        assertEquals(listOf("scan", "pair:NS-C3D4"), calls)
    }

    @Test
    fun pairingSheet_whileRecording_replaceGoesStraightToTheViewModel_whichRefusesIt() {
        val pairing = ScoutPairingUi(nearby = listOf(nearby("C3D4", pairable = true, hasBond = false)), scanning = true)
        show(paired(connected(capturing = true), recording = true), pairing = pairing, overlay = ScoutOverlay.PAIRING)
        compose.onNodeWithText(string(R.string.scout_pair_action)).performClick()
        assertEquals(0, compose.onAllNodesWithText(string(R.string.scout_replace_title, NAME)).fetchSemanticsNodes().size)
        assertEquals(listOf("scan", "pair:NS-C3D4", "dismiss"), calls)
    }

    @Test
    fun pairingSheet_permissionMissing_asksFirst_thenOffersSettingsOnceDeniedForGood() {
        val blocked = ScoutPairingUi(block = ScoutScanBlock.PERMISSION, nearbyAccess = PermissionAccess.BLOCKED)
        show(ScoutPageUi(), pairing = blocked, overlay = ScoutOverlay.PAIRING)
        compose.onNodeWithText(string(R.string.scout_pair_needs_permission)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.open_settings)).performClick()
        assertEquals(listOf("scan", "allow"), calls)
    }

    @Test
    fun paired_permissionMissing_offersSettingsOnceDeniedForGood() {
        show(paired(ScoutState.PermissionMissing(SCOUT)).copy(nearbyAccess = PermissionAccess.BLOCKED))
        compose.onNodeWithText(string(R.string.open_settings)).performClick()
        assertEquals(listOf("allow"), calls)
    }

    @Test
    fun pairingSheet_bonding_saysWhereAndroidAsks_andThatContactsArentNeeded() {
        show(ScoutPageUi(), pairing = ScoutPairingUi(pairing = ScoutPairingState.Pairing("NS-C3D4")), overlay = ScoutOverlay.PAIRING)
        compose.onNodeWithText(string(R.string.scout_pair_bonding, "NS-C3D4")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_pair_contacts_note)).assertIsDisplayed()
    }

    @Test
    fun pairingSheet_refused_repeatsTheBootTip() {
        val failed = ScoutPairingState.Failed("NS-C3D4", ScoutLinkFailure.PAIRING_REJECTED)
        show(ScoutPageUi(), pairing = ScoutPairingUi(pairing = failed), overlay = ScoutOverlay.PAIRING)
        compose.onNodeWithText(string(R.string.scout_pair_refused_title, "NS-C3D4")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_pair_refused_body)).assertIsDisplayed()
    }

    @Test
    fun pairingSheet_emptyAfterTenSeconds_showsTheChecklist() {
        compose.mainClock.autoAdvance = false
        show(ScoutPageUi(), pairing = ScoutPairingUi(scanning = true), overlay = ScoutOverlay.PAIRING)
        compose.mainClock.advanceTimeBy(EMPTY_WAIT_MS)
        compose.onNodeWithText(string(R.string.scout_pair_check_software, ExternalLinks.NS_SCOUT_INSTALLER_HOST), substring = true).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_pair_check_power), substring = true).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.scout_pair_look_again)).assertIsDisplayed()
    }

    @Test
    fun pairingSheet_bluetoothOff_offersTurningItOn() {
        show(ScoutPageUi(), pairing = ScoutPairingUi(block = ScoutScanBlock.BLUETOOTH_OFF), overlay = ScoutOverlay.PAIRING)
        compose.onNodeWithText(string(R.string.scout_pair_needs_bluetooth)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wifi_scout_action_turn_on_bluetooth)).assertIsDisplayed()
    }

    private fun rowDetail(status: Int) = string(R.string.dot_join, string(status), string(R.string.scout_distance_very_close))

    private fun show(state: ScoutPageUi, pairing: ScoutPairingUi = ScoutPairingUi(), overlay: ScoutOverlay = ScoutOverlay.NONE) {
        compose.setContent {
            NsTheme(darkTheme = false) { ScoutPageContent(state, pairing, actions, SnackbarHostState(), initialOverlay = overlay) }
        }
    }

    private fun paired(state: ScoutState, recording: Boolean = false, details: ScoutLinkDetails = details()) =
        ScoutPageUi(state = state, details = details, source = WifiSource.SCOUT, recording = recording, nowMs = 0L)

    private fun details() = ScoutLinkDetails(firmwareVersion = "0.1.0", mtu = 517, status = BoardStatus.getDefaultInstance())

    private fun connected(capturing: Boolean) =
        ScoutState.Connected(SCOUT, batteryPercent = null, channel = 36, framesPerSecond = 74, capturing = capturing, dataMissed = false)

    private fun nearby(shortId: String, pairable: Boolean, hasBond: Boolean) = NearbyScout(
        address = "02:00:00:00:${shortId.take(2)}:${shortId.drop(2)}",
        shortId = shortId,
        rssi = -40,
        advertisement = ScoutAdvertisement(
            protocolMajor = 1, pairable = pairable, hasBond = hasBond, model = BoardModel.BOARD_MODEL_ESP32_C5_DEVKITC_1,
        ),
        pairedHere = false,
    )

    private companion object {
        const val NAME = "NS-A1B2"
        const val EMPTY_WAIT_MS = 10_500L
        val SCOUT = PairedScout(
            id = "NS-02000000A1B2", address = "02:00:00:00:A1:B2", shortId = "A1B2", boardModel = 2, modelName = "",
            firmwareVersion = "0.1.0", protocolMajor = 1, bands = listOf(1, 2), pairedAtMs = 0L,
        )
    }
}
