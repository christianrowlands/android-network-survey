package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.networksurvey.ui.wifi.model.WifiRecords.wifi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Wi-Fi list's grouping and sorting with access points that have no signal measurement: an
 * unknown RSSI shows as unknown, never as 0 dBm, and never decides a group's signal.
 */
class WifiListSortersTest {

    private fun display(bssid: String, ssid: String, rssi: Float?): WifiAccessPointDisplay =
        WifiAccessPointDisplay.fromWrapper(
            wrapper = wifi(bssid = bssid, ssid = ssid, rssi = rssi),
            bandwidthLabel = "",
            standardLabel = "",
            encryptionLabel = "WPA2",
        )

    @Test
    fun anAccessPointWithoutASignal_hasAnUnknownRssi() {
        assertNull(display("aa:aa", "JetSet-Private", rssi = null).rssi)
    }

    @Test
    fun aGroupsSignal_ignoresAccessPointsWithoutOne() {
        val displays = listOf(
            display("aa:aa", "JetSet-Private", rssi = null),
            display("bb:bb", "JetSet-Private", rssi = -78f),
            display("cc:cc", "JetSet-Private", rssi = -84f),
        )

        val group = buildGroupParents(displays, sortIndex = 0, expandedGroupKeys = emptySet()).single()

        assertEquals(-78, group.bestRssi)
        assertEquals(3, group.apCount)
        // Strongest first; the unmeasured AP sorts last instead of first.
        assertEquals(listOf("bb:bb", "cc:cc", "aa:aa"), group.aps.map { it.bssid })
    }

    @Test
    fun aGroupWithNoMeasuredAccessPoint_hasAnUnknownSignal_andSortsLast() {
        val displays = listOf(
            display("aa:aa", "Unmeasured", rssi = null),
            display("bb:bb", "Weak", rssi = -90f),
        ).sortedWith(apComparator(0))

        val groups = buildGroupParents(displays, sortIndex = 0, expandedGroupKeys = emptySet())

        assertEquals(listOf("Weak", "Unmeasured"), groups.map { it.displaySsid })
        assertNull(groups.last().bestRssi)
    }
}
