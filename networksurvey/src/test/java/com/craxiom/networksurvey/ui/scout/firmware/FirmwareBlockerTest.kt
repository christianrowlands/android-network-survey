package com.craxiom.networksurvey.ui.scout.firmware

import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What blocks a firmware update: the link counts only while the Scout is connected (never from
 * leftover link details), and Bluetooth turned off while the screen is open says so at once.
 */
class FirmwareBlockerTest {
    private val scout = PairedScout("NS-02000000A1B2", "02:00:00:00:A1:B2", "A1B2", 0, "", "0.1.0", 1, emptyList(), 0L)
    private val connected = ScoutState.Connected(scout, null, 36, 74, capturing = false, dataMissed = false)

    private fun blocker(state: ScoutState, hasInfo: Boolean = true, recording: Boolean = false, bluetooth: Boolean = true) =
        firmwareBlocker(state, hasInfo, recording, permission = true, bluetooth = bluetooth)

    @Test
    fun `a connected Scout with its Info can update`() {
        assertNull(blocker(connected))
    }

    @Test
    fun `info left from an earlier link does not count while the Scout is not connected`() {
        listOf(ScoutState.Connecting(scout), ScoutState.NotConnected(scout), ScoutState.CantConnect(scout)).forEach { state ->
            assertEquals(state.toString(), FirmwareBlocker.WAITING, blocker(state, hasInfo = true))
        }
    }

    @Test
    fun `Bluetooth turned off while the screen is open is reported from the Scout state`() {
        // The environment read on resume still says Bluetooth is on
        assertEquals(FirmwareBlocker.BLUETOOTH_OFF, blocker(ScoutState.BluetoothOff(scout), bluetooth = true))
    }

    @Test
    fun `recording comes first`() {
        assertEquals(FirmwareBlocker.RECORDING, blocker(ScoutState.BluetoothOff(scout), recording = true))
    }
}
