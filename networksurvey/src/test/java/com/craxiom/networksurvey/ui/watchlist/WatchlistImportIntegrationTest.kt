package com.craxiom.networksurvey.ui.watchlist

import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.dao.WatchlistDao
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import com.craxiom.networksurvey.model.WatchlistImportEntry
import com.craxiom.networksurvey.model.WatchlistImportSet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Integration tests for the watchlist import insert path against a real Room database (Robolectric).
 * Exercises the same pieces the ViewModel's import does - [WatchlistImportPlanner] over the live DB,
 * [WatchlistEntryFactory] for the entity defaults, and the new [WatchlistDao] batch methods - so the
 * dedup, skip-existing, count, entity-default (enabled + EXACT + cooldown), and clear-all behavior is
 * verified deterministically without the ViewModel's coroutine wrapper.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchlistImportIntegrationTest {

    private lateinit var dao: WatchlistDao

    @Before
    fun setUp() = runBlocking {
        dao = SurveyDatabase.getInstance(RuntimeEnvironment.getApplication()).watchlistDao()
        dao.deleteAll() // the database is a singleton; isolate watchlist state per test
    }

    private fun entry(label: String? = null, ssid: String? = null, bssid: String? = null) =
        WatchlistImportEntry(label, ssid, bssid)

    private fun insertEntities(plan: WatchlistImportPlan) = runBlocking {
        dao.insertAll(plan.toAdd.map {
            WatchlistEntryFactory.create(
                it.label,
                it.ssid,
                it.bssid,
                cooldownSeconds = 900,
                createdAt = 1L
            )
        })
    }

    @Test
    fun `import inserts new entries enabled with exact match and the given cooldown`() =
        runBlocking {
            val set = WatchlistImportSet(
                "Acme",
                listOf(entry(label = "HQ", ssid = "HQ-Guest"), entry(bssid = "a4:b5:c6:d7:e8:f9"))
            )

            val plan = WatchlistImportPlanner.plan(dao.getAll(), set)
            assertEquals(2, plan.addedCount)
            insertEntities(plan)

            val all = dao.getAll()
            assertEquals(2, all.size)
            assertTrue("imported entries must be enabled so they alert", all.all { it.enabled })
            assertTrue(all.all { it.matchType == WatchlistEntryEntity.MATCH_TYPE_EXACT })
            assertTrue(all.all { it.cooldownSeconds == 900 })
        }

    @Test
    fun `import skips entries already in the database (case-insensitive) and reports counts`() =
        runBlocking {
            dao.insert(WatchlistEntryFactory.create("HQ", "HQ-Guest", null, 900, 1L))

            val set = WatchlistImportSet(
                null,
                listOf(entry(ssid = "hq-guest"), entry(ssid = "New-Net"))
            )
            val plan = WatchlistImportPlanner.plan(dao.getAll(), set)

            assertEquals(1, plan.addedCount)
            assertEquals(1, plan.duplicateCount)
            insertEntities(plan)

            val all = dao.getAll()
            assertEquals(2, all.size) // one pre-existing + one newly imported
            assertEquals(1, all.count { it.ssid == "New-Net" })
        }

    @Test
    fun `deleteAll clears every entry`() = runBlocking {
        dao.insert(WatchlistEntryFactory.create("HQ", "HQ-Guest", null, 900, 1L))
        dao.insert(WatchlistEntryFactory.create("X", null, "a4:b5:c6:d7:e8:f9", 900, 2L))
        assertEquals(2, dao.getAll().size)

        dao.deleteAll()

        assertEquals(0, dao.getAll().size)
    }
}
