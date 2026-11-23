package com.craxiom.networksurvey.logging.db.uploader.beacondb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The BeaconDB opt-out rule: hidden networks and SSIDs containing `_nomap` or `_optout` (any case,
 * anywhere, like the BeaconDB server) are excluded; everything else is kept.
 */
class WifiOptOutTest {

    @Test
    fun `hidden networks are excluded`() {
        assertTrue(WifiOptOut.isExcludedFromBeaconDb(null))
        assertTrue(WifiOptOut.isExcludedFromBeaconDb(""))
        assertTrue(WifiOptOut.isExcludedFromBeaconDb("\u0000"))
        assertTrue(WifiOptOut.isExcludedFromBeaconDb("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"))
    }

    @Test
    fun `opt-out markers are excluded in any case and anywhere in the name`() {
        assertTrue(WifiOptOut.isExcludedFromBeaconDb("Home_nomap"))
        assertTrue(WifiOptOut.isExcludedFromBeaconDb("Home_NOMAP"))
        assertTrue(WifiOptOut.isExcludedFromBeaconDb("Home_NoMap"))
        assertTrue(WifiOptOut.isExcludedFromBeaconDb("_nomap"))
        assertTrue(WifiOptOut.isExcludedFromBeaconDb("Office_optout"))
        assertTrue(WifiOptOut.isExcludedFromBeaconDb("Office_OptOut"))
        assertTrue(WifiOptOut.isExcludedFromBeaconDb("Home_optout_nomap"))
        assertTrue(WifiOptOut.isExcludedFromBeaconDb("Home_nomap_5G"))
        assertTrue(WifiOptOut.isExcludedFromBeaconDb("Home_optout guest"))
    }

    @Test
    fun `ordinary networks are kept`() {
        assertFalse(WifiOptOut.isExcludedFromBeaconDb("Cafe"))
        assertFalse(WifiOptOut.isExcludedFromBeaconDb(" "))
        assertFalse(WifiOptOut.isExcludedFromBeaconDb("Home-nomap"))
        assertFalse(WifiOptOut.isExcludedFromBeaconDb("Homenomap"))
        assertFalse(WifiOptOut.isExcludedFromBeaconDb("Cafe\u0000"))
    }
}
