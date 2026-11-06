package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Wi-Fi screens show while the NS Scout is the source: its name, its blocks and their fixes. */
class WifiScoutSourceTest {

    private val scout = PairedScout("NS-02000000A1B2", "02:00:00:00:A1:B2", "A1B2", 0, "", "0.1.0", 1, emptyList(), 0L)

    /** What the block's one button offers, as the Wi-Fi list asks for it. */
    private fun WifiScoutUi?.offeredFix(): ScoutWifiFix? = this?.block?.fix(recording)

    @Test
    fun thePhoneSource_hasNoScoutPart() {
        assertNull(WifiScoutSource.of(connected(), WifiSource.PHONE))
        assertNull(WifiScoutSource.of(ScoutState.NotPaired, WifiSource.SCOUT))
        assertNull(WifiScoutSource.of(ScoutState.NotInUse(scout), WifiSource.SCOUT))
    }

    @Test
    fun aConnectedScout_namesItself_andBlocksNothing() {
        assertEquals(WifiScoutUi("NS-A1B2", null), WifiScoutSource.of(connected(), WifiSource.SCOUT))
        assertNull(WifiScoutSource.block(ScoutState.CatchingUp(scout, 10, 5, dataMissed = false)))
    }

    @Test
    fun theBlocks_followTheScoutStatesOrder_withOneFixEach() {
        val cases = listOf(
            ScoutState.PermissionMissing(scout) to (ScoutWifiBlock.ALLOW_NEARBY to ScoutWifiFix.ALLOW_NEARBY),
            ScoutState.BluetoothOff(scout) to (ScoutWifiBlock.BLUETOOTH_OFF to ScoutWifiFix.TURN_ON_BLUETOOTH),
            ScoutState.NeedsFirmwareUpdate(scout, "0.1.0") to (ScoutWifiBlock.FIRMWARE_UPDATE to ScoutWifiFix.UPDATE_FIRMWARE),
            ScoutState.AppUpdateNeeded(scout) to (ScoutWifiBlock.APP_UPDATE to ScoutWifiFix.SCOUT_PAGE),
            ScoutState.UpdatingFirmware(scout) to (ScoutWifiBlock.UPDATING_FIRMWARE to ScoutWifiFix.SCOUT_PAGE),
            ScoutState.PairingLost(scout) to (ScoutWifiBlock.PAIRING_LOST to ScoutWifiFix.USE_PHONE_WIFI),
            ScoutState.PairingRefused(scout) to (ScoutWifiBlock.PAIRING_LOST to ScoutWifiFix.USE_PHONE_WIFI),
            ScoutState.Connecting(scout) to (ScoutWifiBlock.CONNECTING to ScoutWifiFix.USE_PHONE_WIFI),
            ScoutState.OutOfRange(scout, batteryMayBeEmpty = false) to (ScoutWifiBlock.NOT_CONNECTED to ScoutWifiFix.USE_PHONE_WIFI),
            ScoutState.NotConnected(scout) to (ScoutWifiBlock.NOT_CONNECTED to ScoutWifiFix.USE_PHONE_WIFI),
            ScoutState.CantConnect(scout) to (ScoutWifiBlock.CANT_CONNECT to ScoutWifiFix.USE_PHONE_WIFI),
        )
        cases.forEach { (state, expected) ->
            val block = WifiScoutSource.block(state)
            assertEquals(state.toString(), expected.first, block)
            assertEquals(state.toString(), expected.second, block?.fix(recording = false))
        }
        // The enum order is the list's precedence, the same as the Scout state's (Out of range only while recording)
        assertEquals(cases.map { it.second.first }.distinct() + ScoutWifiBlock.OUT_OF_RANGE, ScoutWifiBlock.entries.toList())
    }

    @Test
    fun whileRecording_theBlockNeverOffersPhoneWifi_andOutOfRangeReadsAsOnTheSurveyTab() {
        val outOfRange = WifiScoutSource.of(ScoutState.OutOfRange(scout, batteryMayBeEmpty = false), WifiSource.SCOUT, recording = true)
        assertEquals(ScoutWifiBlock.OUT_OF_RANGE, outOfRange?.block)
        assertEquals(ScoutWifiFix.SCOUT_PAGE, outOfRange.offeredFix())

        listOf(
            ScoutState.PairingLost(scout), ScoutState.Connecting(scout), ScoutState.NotConnected(scout), ScoutState.CantConnect(scout),
        ).forEach { state ->
            assertEquals(state.toString(), ScoutWifiFix.SCOUT_PAGE, WifiScoutSource.of(state, WifiSource.SCOUT, recording = true).offeredFix())
            assertEquals(state.toString(), ScoutWifiFix.USE_PHONE_WIFI, WifiScoutSource.of(state, WifiSource.SCOUT).offeredFix())
        }
    }

    @Test
    fun phoneConditions_neverBlockTheScout() {
        WifiScanState.entries.forEach { phone ->
            assertEquals(WifiScanState.READY, WifiScoutSource.phoneScanState(phone, WifiSource.SCOUT, scoutPaired = true))
        }
        assertEquals(WifiScanState.WIFI_OFF, WifiScoutSource.phoneScanState(WifiScanState.WIFI_OFF, WifiSource.PHONE, scoutPaired = true))
        assertEquals(WifiScanState.WIFI_OFF, WifiScoutSource.phoneScanState(WifiScanState.WIFI_OFF, WifiSource.SCOUT, scoutPaired = false))
    }

    @Test
    fun theThrottleNotice_suggestsAScout_onlyWithNonePaired_andWhenAllowed() {
        assertTrue(WifiScoutSource.suggestsScout(ScoutState.NotPaired, allowed = true))
        assertFalse(WifiScoutSource.suggestsScout(ScoutState.NotPaired, allowed = false))
        assertFalse(WifiScoutSource.suggestsScout(ScoutState.NotInUse(scout), allowed = true))
        assertFalse(WifiScoutSource.suggestsScout(connected(), allowed = true))
    }

    private fun connected() = ScoutState.Connected(scout, null, 36, 74, capturing = true, dataMissed = false)
}
