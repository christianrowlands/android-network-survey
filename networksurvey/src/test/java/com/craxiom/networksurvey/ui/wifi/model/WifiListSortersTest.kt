package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.networksurvey.ui.wifi.model.WifiRecords.wifi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    @Test
    fun theConnectedAccessPoint_isMarked_whateverTheCase_andItsGroupSaysSo() {
        val cache = mutableMapOf<String, WifiAccessPointDisplay>()
        val displays = cachedDisplays(
            listOf(wifi("02:00:A4:11:F0:21", ssid = "Acme-Corp"), wifi("02:00:A4:11:F0:22", ssid = "Acme-Corp"), wifi("02:00:D8:4E:9A:0C", ssid = "Lab")),
            cache,
            connectedBssid = "02:00:a4:11:f0:22",
        )

        assertEquals(listOf(false, true, false), displays.map { it.isConnected })
        val groups = buildGroupParents(displays, sortIndex = 0, expandedGroupKeys = emptySet())
        assertEquals(mapOf("Acme-Corp" to true, "Lab" to false), groups.associate { it.displaySsid to it.containsConnected })
    }

    @Test
    fun aRoam_movesTheMark_andKeepsEveryOtherRowTheSameInstance() {
        val cache = mutableMapOf<String, WifiAccessPointDisplay>()
        val before = cachedDisplays(
            listOf(wifi("02:00:A4:11:F0:21"), wifi("02:00:A4:11:F0:22"), wifi("02:00:A4:11:F0:23")),
            cache,
            connectedBssid = "02:00:a4:11:f0:21",
        )

        val after = markConnected(before, cache, "02:00:a4:11:f0:22")

        assertEquals(listOf(false, true, false), after.map { it.isConnected })
        assertSame(before[2], after[2])
        // The cache holds the new marks, so the next scan reuses them rather than flipping back
        assertTrue(cache.getValue("02:00:A4:11:F0:22").isConnected)
        assertFalse(cache.getValue("02:00:A4:11:F0:21").isConnected)
    }

    @Test
    fun noConnection_marksNothing() {
        val displays = cachedDisplays(listOf(wifi("02:00:A4:11:F0:21")), mutableMapOf(), connectedBssid = null)

        assertFalse(displays.single().isConnected)
    }
}
