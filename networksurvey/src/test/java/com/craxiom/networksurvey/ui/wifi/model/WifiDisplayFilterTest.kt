package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.networksurvey.util.WifiBand
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WifiDisplayFilter.matches] covering text search (SSID and BSSID,
 * case-insensitivity, separator stripping, hidden SSIDs) and band set constraints.
 */
class WifiDisplayFilterTest {

    private fun display(
        ssid: String = "MyHomeNetwork",
        bssid: String = "AA:BB:CC:DD:EE:FF",
        band: WifiBand? = WifiBand.GHZ_24,
    ): WifiAccessPointDisplay {
        return WifiAccessPointDisplay(
            bssid = bssid,
            ssid = ssid,
            ssidIsHidden = ssid.isEmpty(),
            rssi = -60,
            frequencyMhz = 2437,
            band = band,
            channel = 6,
            bandwidthLabel = "20 MHz",
            standardLabel = "11n",
            encryptionLabel = "WPA2",
            passpoint = false,
            isExcluded = false,
        )
    }

    @Test
    fun inactiveFilterMatchesEverything() {
        val filter = WifiDisplayFilter()
        assertFalse(filter.isActive)
        assertTrue(filter.matches(display()))
        assertTrue(filter.matches(display(ssid = "", band = null)))
    }

    @Test
    fun whitespaceOnlyQueryIsInert() {
        val filter = WifiDisplayFilter(searchQuery = "   ")
        assertFalse(filter.isActive)
        assertTrue(filter.matches(display()))
    }

    @Test
    fun ssidSubstringMatchIsCaseInsensitive() {
        assertTrue(WifiDisplayFilter(searchQuery = "homenet").matches(display()))
        assertTrue(WifiDisplayFilter(searchQuery = "MYHOME").matches(display()))
        assertFalse(WifiDisplayFilter(searchQuery = "Office").matches(display()))
    }

    @Test
    fun queryIsTrimmedBeforeMatching() {
        assertTrue(WifiDisplayFilter(searchQuery = "  homenet  ").matches(display()))
    }

    @Test
    fun bssidSubstringMatchIsCaseInsensitive() {
        assertTrue(WifiDisplayFilter(searchQuery = "bb:cc").matches(display()))
        assertTrue(WifiDisplayFilter(searchQuery = "DD:EE:FF").matches(display()))
    }

    @Test
    fun bssidMatchesWithSeparatorsStripped() {
        assertTrue(WifiDisplayFilter(searchQuery = "aabbcc").matches(display()))
        assertTrue(WifiDisplayFilter(searchQuery = "AA-BB-CC").matches(display()))
        assertFalse(WifiDisplayFilter(searchQuery = "aabbcd").matches(display()))
    }

    @Test
    fun hiddenSsidMatchesByBssidOnly() {
        val hidden = display(ssid = "")
        assertTrue(WifiDisplayFilter(searchQuery = "aa:bb").matches(hidden))
        assertFalse(WifiDisplayFilter(searchQuery = "MyHome").matches(hidden))
    }

    @Test
    fun separatorOnlyQueryDoesNotMatchEverything() {
        // ":" appears in the displayed BSSID, so a raw substring match is expected,
        // but the stripped-separator path must not treat an empty stripped query as a match.
        assertFalse(WifiDisplayFilter(searchQuery = "-").matches(display()))
    }

    @Test
    fun colonQueryMatchesAllStandardBssids() {
        // Documents known behavior: ":" is a substring of every standard BSSID, so it marks
        // the filter active without hiding anything. Accepted as plain substring semantics.
        val filter = WifiDisplayFilter(searchQuery = ":")
        assertTrue(filter.isActive)
        assertTrue(filter.matches(display()))
        assertTrue(filter.matches(display(ssid = "")))
    }

    @Test
    fun queryMatchingSsidOrBssidIsAnOrSemantic() {
        // SSID matches but BSSID does not, and vice versa; either alone is enough.
        assertTrue(WifiDisplayFilter(searchQuery = "MyHome").matches(display(bssid = "11:22:33:44:55:66")))
        assertTrue(
            WifiDisplayFilter(searchQuery = "112233")
                .matches(display(ssid = "Office", bssid = "11:22:33:44:55:66"))
        )
    }

    @Test
    fun emptyBandSetMatchesAllBands() {
        val filter = WifiDisplayFilter(selectedBands = emptySet())
        assertTrue(filter.matches(display(band = WifiBand.GHZ_5)))
        assertTrue(filter.matches(display(band = null)))
    }

    @Test
    fun selectedBandsConstrainMatches() {
        val filter = WifiDisplayFilter(selectedBands = setOf(WifiBand.GHZ_24, WifiBand.GHZ_5))
        assertTrue(filter.matches(display(band = WifiBand.GHZ_24)))
        assertTrue(filter.matches(display(band = WifiBand.GHZ_5)))
        assertFalse(filter.matches(display(band = WifiBand.GHZ_6)))
    }

    @Test
    fun nullBandFailsAnActiveBandFilter() {
        val filter = WifiDisplayFilter(selectedBands = setOf(WifiBand.GHZ_24))
        assertFalse(filter.matches(display(band = null)))
    }

    @Test
    fun textAndBandConstraintsCombineWithAnd() {
        val filter = WifiDisplayFilter(
            searchQuery = "MyHome",
            selectedBands = setOf(WifiBand.GHZ_5),
        )
        assertFalse(filter.matches(display(band = WifiBand.GHZ_24)))
        assertTrue(filter.matches(display(band = WifiBand.GHZ_5)))
    }

    @Test
    fun isActiveReflectsAnyConstraint() {
        assertTrue(WifiDisplayFilter(searchQuery = "a").isActive)
        assertTrue(WifiDisplayFilter(selectedBands = setOf(WifiBand.GHZ_6)).isActive)
        assertFalse(WifiDisplayFilter().isActive)
    }
}
