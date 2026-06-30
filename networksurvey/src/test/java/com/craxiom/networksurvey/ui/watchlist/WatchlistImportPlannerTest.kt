package com.craxiom.networksurvey.ui.watchlist

import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import com.craxiom.networksurvey.model.WatchlistImportEntry
import com.craxiom.networksurvey.model.WatchlistImportSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WatchlistImportPlanner]. Pure logic (no Android dependencies): it dedupes incoming
 * entries against the existing watchlist and within the batch, resolves display labels, and reports
 * the counts the import dialog shows.
 */
class WatchlistImportPlannerTest {

    private fun existing(ssid: String?, bssid: String?): WatchlistEntryEntity =
        WatchlistEntryEntity().apply {
            this.ssid = ssid
            this.bssid = bssid
        }

    private fun entry(label: String? = null, ssid: String? = null, bssid: String? = null) =
        WatchlistImportEntry(label, ssid, bssid)

    private fun importSet(vararg entries: WatchlistImportEntry) =
        WatchlistImportSet(name = null, entries = entries.toList())

    @Test
    fun `plan adds all entries when watchlist is empty`() {
        val set = importSet(entry(ssid = "A"), entry(ssid = "B"), entry(ssid = "C"))

        val plan = WatchlistImportPlanner.plan(emptyList(), set)

        assertEquals(3, plan.addedCount)
        assertEquals(0, plan.duplicateCount)
        assertEquals(3, plan.toAdd.size)
        assertTrue(plan.previewRows.none { it.isDuplicate })
    }

    @Test
    fun `plan marks an existing ssid as duplicate case-insensitively`() {
        val set = importSet(entry(ssid = "hq"))

        val plan = WatchlistImportPlanner.plan(listOf(existing("HQ", null)), set)

        assertEquals(0, plan.addedCount)
        assertEquals(1, plan.duplicateCount)
        assertTrue(plan.toAdd.isEmpty())
        assertTrue(plan.previewRows[0].isDuplicate)
    }

    @Test
    fun `plan marks an existing bssid as duplicate case-insensitively`() {
        val set = importSet(entry(bssid = "AA:BB:CC:DD:EE:FF"))

        val plan = WatchlistImportPlanner.plan(listOf(existing(null, "aa:bb:cc:dd:ee:ff")), set)

        assertEquals(0, plan.addedCount)
        assertEquals(1, plan.duplicateCount)
    }

    @Test
    fun `plan collapses within-batch duplicates to a single insert`() {
        val set = importSet(entry(ssid = "Dup"), entry(ssid = "dup"))

        val plan = WatchlistImportPlanner.plan(emptyList(), set)

        assertEquals(1, plan.addedCount)
        assertEquals(1, plan.duplicateCount)
        assertEquals(1, plan.toAdd.size)
        assertFalse(plan.previewRows[0].isDuplicate)
        assertTrue(plan.previewRows[1].isDuplicate)
    }

    @Test
    fun `plan treats ssid-only and ssid-plus-bssid as different entries`() {
        val set = importSet(entry(ssid = "Net", bssid = "a4:b5:c6:d7:e8:f9"))

        val plan = WatchlistImportPlanner.plan(listOf(existing("Net", null)), set)

        assertEquals(1, plan.addedCount)
        assertEquals(0, plan.duplicateCount)
    }

    @Test
    fun `plan resolves the display label from label then ssid then bssid`() {
        val set = importSet(
            entry(label = "  Custom  ", ssid = "X"),
            entry(label = null, ssid = "HQ-Guest"),
            entry(label = null, bssid = "a4:b5:c6:d7:e8:f9")
        )

        val plan = WatchlistImportPlanner.plan(emptyList(), set)

        assertEquals("Custom", plan.previewRows[0].label)
        assertEquals("HQ-Guest", plan.previewRows[1].label)
        assertEquals("a4:b5:c6:d7:e8:f9", plan.previewRows[2].label)
    }

    @Test
    fun `plan carries resolved label and identity into toAdd`() {
        val set = importSet(entry(label = null, ssid = "HQ-Guest", bssid = "a4:b5:c6:d7:e8:f9"))

        val plan = WatchlistImportPlanner.plan(emptyList(), set)

        assertEquals(1, plan.toAdd.size)
        val added = plan.toAdd[0]
        assertEquals("HQ-Guest", added.label)
        assertEquals("HQ-Guest", added.ssid)
        assertEquals("a4:b5:c6:d7:e8:f9", added.bssid)
    }
}
