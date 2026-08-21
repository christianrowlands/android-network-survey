package com.craxiom.networksurvey.services.watchlist

import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [WatchlistMatcher], the single matching rule shared by scan-time detection and the
 * Wi-Fi Details screen's "On watchlist" status. Covers the SSID-only, BSSID-only, and both-set
 * (AND) rules, case-insensitivity, trimming, and the hidden-SSID (empty candidate) behavior.
 */
class WatchlistMatcherTest {

    private fun entry(ssid: String? = null, bssid: String? = null) =
        WatchlistEntryEntity().apply {
            this.ssid = ssid
            this.bssid = bssid
        }

    @Test
    fun `ssid entry matches the same ssid on any bssid, case-insensitively`() {
        val entry = entry(ssid = "CoffeeShop")
        assertTrue(WatchlistMatcher.matches(entry, "CoffeeShop", "aa:bb:cc:dd:ee:ff"))
        assertTrue(WatchlistMatcher.matches(entry, "coffeeshop", "11:22:33:44:55:66"))
        assertFalse(WatchlistMatcher.matches(entry, "OtherNet", "aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun `ssid entry never matches a hidden network`() {
        val entry = entry(ssid = "CoffeeShop")
        assertFalse(WatchlistMatcher.matches(entry, "", "aa:bb:cc:dd:ee:ff"))
        assertFalse(WatchlistMatcher.matches(entry, null, "aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun `bssid entry matches that bssid regardless of ssid, including hidden`() {
        val entry = entry(bssid = "aa:bb:cc:dd:ee:ff")
        assertTrue(WatchlistMatcher.matches(entry, "AnyName", "aa:bb:cc:dd:ee:ff"))
        assertTrue(WatchlistMatcher.matches(entry, "", "AA:BB:CC:DD:EE:FF"))
        assertFalse(WatchlistMatcher.matches(entry, "AnyName", "11:22:33:44:55:66"))
    }

    @Test
    fun `entry with both identifiers requires both to match`() {
        val entry = entry(ssid = "CoffeeShop", bssid = "aa:bb:cc:dd:ee:ff")
        assertTrue(WatchlistMatcher.matches(entry, "coffeeshop", "AA:bb:cc:dd:ee:ff"))
        assertFalse(WatchlistMatcher.matches(entry, "CoffeeShop", "11:22:33:44:55:66"))
        assertFalse(WatchlistMatcher.matches(entry, "OtherNet", "aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun `values are trimmed before comparison`() {
        val entry = entry(ssid = " CoffeeShop ")
        assertTrue(WatchlistMatcher.matches(entry, "CoffeeShop ", "aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun `entry with no identifiers never matches`() {
        assertFalse(WatchlistMatcher.matches(entry(), "CoffeeShop", "aa:bb:cc:dd:ee:ff"))
        assertFalse(WatchlistMatcher.matches(entry(ssid = "  "), "CoffeeShop", "aa:bb:cc:dd:ee:ff"))
    }
}
