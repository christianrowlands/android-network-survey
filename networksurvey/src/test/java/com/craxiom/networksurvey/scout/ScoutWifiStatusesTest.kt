package com.craxiom.networksurvey.scout

import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.survey.status.ScoutOutage
import com.craxiom.networksurvey.survey.status.ScoutWifiStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a running survey says about Wi-Fi from the Scout, with the 30 s start grace. */
class ScoutWifiStatusesTest {
    private val scout = PairedScout("NS-02005E005CFA", "02:00:5E:00:5C:FA", "5CFA", 2, "", "0.2.0", 1, listOf(1, 2), 0)

    private fun status(state: ScoutState, startMs: Long? = 0, nowMs: Long = 60_000, source: WifiSource = WifiSource.SCOUT) =
        ScoutWifiStatuses.of(state, source, startMs, nowMs)

    @Test
    fun `the phone as the source has no Scout status`() {
        assertNull(status(ScoutState.OutOfRange(scout, false), source = WifiSource.PHONE))
        assertNull(status(ScoutState.NotPaired))
    }

    @Test
    fun `each blocking state is an outage, a healthy or connecting Scout is not`() {
        assertEquals(ScoutWifiStatus("NS-5CFA", ScoutOutage.OUT_OF_RANGE), status(ScoutState.OutOfRange(scout, false)))
        assertEquals(ScoutOutage.BLUETOOTH_OFF, status(ScoutState.BluetoothOff(scout))?.outage)
        assertEquals(ScoutOutage.PERMISSION_MISSING, status(ScoutState.PermissionMissing(scout))?.outage)
        assertEquals(ScoutOutage.NEEDS_FIRMWARE_UPDATE, status(ScoutState.NeedsFirmwareUpdate(scout, "0.1.0"))?.outage)
        assertEquals(ScoutOutage.APP_UPDATE_NEEDED, status(ScoutState.AppUpdateNeeded(scout))?.outage)
        assertEquals(ScoutOutage.PAIRING_LOST, status(ScoutState.PairingLost(scout))?.outage)
        assertEquals(ScoutOutage.PAIRING_LOST, status(ScoutState.PairingRefused(scout))?.outage)
        assertEquals(ScoutOutage.CANT_CONNECT, status(ScoutState.CantConnect(scout))?.outage)
        assertEquals(ScoutOutage.UPDATING_FIRMWARE, status(ScoutState.UpdatingFirmware(scout))?.outage)
        assertEquals(ScoutWifiStatus("NS-5CFA", null), status(ScoutState.Connecting(scout)))
        assertNull(status(ScoutState.Connected(scout, null, 36, 74, true, false))?.outage)
        assertNull(status(ScoutState.CatchingUp(scout, 1_240, 30, false))?.outage)
    }

    @Test
    fun `nothing is called out within 30 s of the start, however the survey started`() {
        assertNull(status(ScoutState.BluetoothOff(scout), startMs = 0, nowMs = 29_999)?.outage)
        assertEquals(ScoutOutage.BLUETOOTH_OFF, status(ScoutState.BluetoothOff(scout), startMs = 0, nowMs = 30_000)?.outage)
        assertEquals(30_000L, ScoutWifiStatuses.graceEndsAtMs(0, 10_000))
        assertNull(ScoutWifiStatuses.graceEndsAtMs(0, 30_000))
        assertNull(ScoutWifiStatuses.graceEndsAtMs(null, 30_000))
    }
}
