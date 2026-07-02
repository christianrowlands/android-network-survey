package com.craxiom.networksurvey.ui.watchlist

import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [WatchlistEntryFactory]. Pins the identity contract every new entry depends on: a freshly
 * minted uuid that is unique per entry, and an updatedAt equal to the creation time.
 */
class WatchlistEntryFactoryTest {

    @Test
    fun `every new entry is minted with a unique uuid`() {
        val first = WatchlistEntryFactory.create("HQ", "HQ-Guest", null, 900, 1L)
        val second = WatchlistEntryFactory.create("HQ", "HQ-Guest", null, 900, 1L)

        assertNotNull(first.uuid)
        assertNotNull(second.uuid)
        assertNotEquals("Each entry must get its own uuid", first.uuid, second.uuid)
    }

    @Test
    fun `a new entry starts enabled with exact matching and updatedAt equal to createdAt`() {
        val entry = WatchlistEntryFactory.create("HQ", "HQ-Guest", "68:7f:74:b0:14:98", 900, 42L)

        assertTrue(entry.enabled)
        assertEquals(WatchlistEntryEntity.MATCH_TYPE_EXACT, entry.matchType)
        assertEquals(42L, entry.createdAt)
        assertEquals("updatedAt must start equal to createdAt", 42L, entry.updatedAt)
        assertEquals(900, entry.cooldownSeconds)
        assertEquals("HQ", entry.label)
        assertEquals("HQ-Guest", entry.ssid)
        assertEquals("68:7f:74:b0:14:98", entry.bssid)
    }
}
