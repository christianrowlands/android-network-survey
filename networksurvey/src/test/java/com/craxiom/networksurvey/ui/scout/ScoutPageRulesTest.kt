package com.craxiom.networksurvey.ui.scout

import android.os.Build
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.board.v1.BoardModel
import com.craxiom.networksurvey.scout.link.ScoutAdvertisement
import com.craxiom.networksurvey.scout.model.NearbyScout
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.ui.common.PermissionAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Scout page's small rules: which permission it names, its fix's label, and Replace during a survey. */
class ScoutPageRulesTest {
    private val paired = PairedScout("NS-AABBCCDDABCD", "AA:BB:CC:DD:AB:CD", "ABCD", 0, "", "0.1.0", 1, emptyList(), 0L)

    @Test
    fun theSheetNamesNearbyDevicesFromAndroid12_andLocationBeforeIt() {
        assertEquals(R.string.scout_pair_needs_permission, scanPermissionText(Build.VERSION_CODES.S))
        assertEquals(R.string.scout_pair_needs_location, scanPermissionText(Build.VERSION_CODES.R))
    }

    @Test
    fun theFixSaysOpenSettings_onlyOnceDeniedForGood() {
        assertEquals(R.string.survey_action_allow, nearbyFixLabel(PermissionAccess.AVAILABLE))
        assertEquals(R.string.survey_action_allow, nearbyFixLabel(PermissionAccess.DENIED))
        assertEquals(R.string.open_settings, nearbyFixLabel(PermissionAccess.BLOCKED))
    }

    @Test
    fun anotherScout_isRefusedOnlyWhileRecording_withOneAlreadyPaired() {
        assertTrue(replacesWhileRecording(recording = true, paired, nearby(pairedHere = false)))
        assertFalse(replacesWhileRecording(recording = false, paired, nearby(pairedHere = false)))
    }

    @Test
    fun aFirstScout_orPairingTheSameOneAgain_isAllowedWhileRecording() {
        assertFalse(replacesWhileRecording(recording = true, paired = null, nearby(pairedHere = false)))
        assertFalse(replacesWhileRecording(recording = true, paired, nearby(pairedHere = true)))
    }

    private fun nearby(pairedHere: Boolean) = NearbyScout(
        address = "AA:BB:CC:DD:00:01",
        shortId = "0001",
        rssi = -40,
        advertisement = ScoutAdvertisement(protocolMajor = 1, pairable = true, hasBond = false, model = BoardModel.BOARD_MODEL_ESP32_C5_DEVKITC_1),
        pairedHere = pairedHere,
    )
}
