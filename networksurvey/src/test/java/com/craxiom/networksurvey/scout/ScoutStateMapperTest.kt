package com.craxiom.networksurvey.scout

import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.scout.link.ScoutCompatibility
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one Scout status: its precedence and the 30 s connect grace. */
class ScoutStateMapperTest {
    private val scout = PairedScout(
        id = "NS-02005E00ABCD", address = "02:00:5E:00:AB:CD", shortId = "ABCD", boardModel = 2,
        modelName = "", firmwareVersion = "0.1.0", protocolMajor = 1, bands = listOf(1, 2), pairedAtMs = 0,
    )

    private val ready = ScoutStateInputs(
        scout = scout,
        source = WifiSource.SCOUT,
        hasPermission = true,
        bluetoothOn = true,
        compatibility = ScoutCompatibility.COMPATIBLE,
        phase = ScoutLinkPhase.READY,
        nowMs = 100_000,
    )

    private fun map(inputs: ScoutStateInputs) = ScoutStateMapper.map(inputs)

    @Test
    fun noScout_isNotPaired() {
        assertEquals(ScoutState.NotPaired, map(ready.copy(scout = null, source = WifiSource.PHONE)))
    }

    @Test
    fun phoneSource_isNotInUse_evenWithBluetoothOffOrAProblem() {
        val inputs = ready.copy(source = WifiSource.PHONE, bluetoothOn = false, issue = ScoutIssue.PAIRING_LOST)
        assertEquals(ScoutState.NotInUse(scout), map(inputs))
    }

    @Test
    fun precedence_permissionThenBluetoothThenUpdateThenPairingThenProtocol() {
        val everything = ready.copy(
            hasPermission = false, bluetoothOn = false, updatingFirmware = true,
            issue = ScoutIssue.PAIRING_LOST, compatibility = ScoutCompatibility.NEEDS_FIRMWARE_UPDATE,
        )
        assertTrue(map(everything) is ScoutState.PermissionMissing)
        assertTrue(map(everything.copy(hasPermission = true)) is ScoutState.BluetoothOff)
        assertTrue(map(everything.copy(hasPermission = true, bluetoothOn = true)) is ScoutState.UpdatingFirmware)
        val noUpdate = everything.copy(hasPermission = true, bluetoothOn = true, updatingFirmware = false)
        assertTrue(map(noUpdate) is ScoutState.PairingLost)
        assertTrue(map(noUpdate.copy(issue = ScoutIssue.PAIRING_REFUSED)) is ScoutState.PairingRefused)
        assertEquals(ScoutState.NeedsFirmwareUpdate(scout, "0.1.0"), map(noUpdate.copy(issue = null)))
        assertEquals(
            ScoutState.AppUpdateNeeded(scout),
            map(noUpdate.copy(issue = null, compatibility = ScoutCompatibility.NEEDS_APP_UPDATE)),
        )
    }

    @Test
    fun idle_isNotConnected() {
        assertEquals(ScoutState.NotConnected(scout), map(ready.copy(phase = ScoutLinkPhase.IDLE)))
    }

    @Test
    fun connecting_readsConnectingForThirtySecondsThenOutOfRange() {
        val connecting = ready.copy(phase = ScoutLinkPhase.CONNECTING, connectingSinceMs = 100_000)
        assertEquals(ScoutState.Connecting(scout), map(connecting.copy(nowMs = 129_999)))
        assertEquals(130_000L, ScoutStateMapper.graceEndsAtMs(connecting))
        assertEquals(ScoutState.OutOfRange(scout, batteryMayBeEmpty = false), map(connecting.copy(nowMs = 130_000)))
        assertNull(ScoutStateMapper.graceEndsAtMs(connecting.copy(nowMs = 130_000)))
    }

    @Test
    fun threeAttemptsThatReachTheScoutAndFail_readCantConnect_evenWithinTheGrace() {
        val connecting = ready.copy(phase = ScoutLinkPhase.CONNECTING, connectingSinceMs = 100_000, nowMs = 110_000)
        assertEquals(ScoutState.Connecting(scout), map(connecting.copy(failedAfterConnect = 2)))
        assertEquals(ScoutState.CantConnect(scout), map(connecting.copy(failedAfterConnect = 3)))
        assertNull(ScoutStateMapper.graceEndsAtMs(connecting.copy(failedAfterConnect = 3)))
        assertEquals(ScoutState.CantConnect(scout), map(connecting.copy(nowMs = 200_000, failedAfterConnect = 4)))
        // Only a link that is still trying: Ready, idle and the user's problems come first
        assertTrue(map(ready.copy(failedAfterConnect = 3)) is ScoutState.Connected)
        assertEquals(ScoutState.NotConnected(scout), map(connecting.copy(phase = ScoutLinkPhase.IDLE, failedAfterConnect = 3)))
        assertEquals(ScoutState.BluetoothOff(scout), map(connecting.copy(bluetoothOn = false, failedAfterConnect = 3)))
        assertEquals(false, ScoutState.CantConnect(scout).isUsable)
    }

    @Test
    fun outOfRange_saysTheBatteryMayBeEmptyAtTenPercentOrLess() {
        val out = ready.copy(phase = ScoutLinkPhase.CONNECTING, connectingSinceMs = 0)
        assertEquals(ScoutState.OutOfRange(scout, batteryMayBeEmpty = true), map(out.copy(lastBatteryPercent = 10)))
        assertEquals(ScoutState.OutOfRange(scout, batteryMayBeEmpty = false), map(out.copy(lastBatteryPercent = 11)))
    }

    @Test
    fun connected_carriesBatteryChannelFramesAndDataMissed() {
        val status = BoardStatus.newBuilder().setCurrentChannel(36).setRxFramesPerSec(74).setBatteryMv(3900).build()
        val state = map(ready.copy(status = status, capturing = true, dataMissed = true))
        assertEquals(ScoutState.Connected(scout, 65, 36, 74, capturing = true, dataMissed = true), state)
        assertTrue(state.isUsable)
    }

    @Test
    fun connected_withoutABatteryReadingIsUsbPowered() {
        val status = BoardStatus.newBuilder().setCurrentChannel(6).build()
        assertNull((map(ready.copy(status = status)) as ScoutState.Connected).batteryPercent)
    }

    @Test
    fun aRefusedCapture_isNeverAHealthyConnected() {
        val refused = ready.copy(captureRejected = true)
        assertEquals(ScoutState.CantConnect(scout), map(refused))
        assertEquals(false, map(refused).isUsable)
        val catchingUp = ScoutCatchUpTracker.Snapshot(backlogRecords = 1240, ringFillPercent = 40)
        assertEquals(ScoutState.CantConnect(scout), map(refused.copy(catchUp = catchingUp)))
        // Only a link that is up: the user's problems and a link that is down say their own thing
        assertEquals(ScoutState.BluetoothOff(scout), map(refused.copy(bluetoothOn = false)))
        assertEquals(ScoutState.NotConnected(scout), map(refused.copy(phase = ScoutLinkPhase.IDLE)))
    }

    @Test
    fun catchUp_winsOverConnected() {
        val state = map(ready.copy(catchUp = ScoutCatchUpTracker.Snapshot(backlogRecords = 1240, ringFillPercent = 40)))
        assertEquals(ScoutState.CatchingUp(scout, 1240, 40, dataMissed = false), state)
    }
}
