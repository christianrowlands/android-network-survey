package com.craxiom.networksurvey.ui.watchlist

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.dao.WatchlistDao
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Integration tests for [WatchlistAddHelper] against a real Room database (Robolectric). This is
 * the shared add path used by both the Watchlist screen and the Wi-Fi Details screen's "Watch"
 * action, so it verifies normalization, the DB-backed duplicate rule, the entity defaults, and the
 * opt-in [WatchlistAddHelper.enableWatchlistIfDisabled] preference flip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchlistAddHelperTest {

    private lateinit var dao: WatchlistDao

    @Before
    fun setUp() = runBlocking {
        dao = SurveyDatabase.getInstance(RuntimeEnvironment.getApplication()).watchlistDao()
        dao.deleteAll() // the database is a singleton; isolate watchlist state per test
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()).edit {
            remove(NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED)
        }
    }

    @Test
    fun `addEntry normalizes, resolves the label, and inserts with the standard defaults`() =
        runBlocking {
            val result = WatchlistAddHelper.addEntry(
                RuntimeEnvironment.getApplication(),
                label = "",
                ssid = null,
                bssid = "A4:B5:C6:D7:E8:F9"
            )

            assertTrue(result is WatchlistAddHelper.AddResult.Added)
            assertEquals("a4:b5:c6:d7:e8:f9", (result as WatchlistAddHelper.AddResult.Added).label)

            val all = dao.getAll()
            assertEquals(1, all.size)
            val entry = all.first()
            assertEquals("a4:b5:c6:d7:e8:f9", entry.bssid)
            assertTrue(entry.enabled)
            assertEquals(WatchlistEntryEntity.MATCH_TYPE_EXACT, entry.matchType)
        }

    @Test
    fun `addEntry rejects a duplicate identity case-insensitively`() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        WatchlistAddHelper.addEntry(app, "HQ", "HQ-Guest", null)

        val result = WatchlistAddHelper.addEntry(app, "Again", "hq-guest", null)

        assertTrue(result is WatchlistAddHelper.AddResult.Duplicate)
        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun `ssid-only and bssid-only entries for the same network are distinct identities`() =
        runBlocking {
            val app = RuntimeEnvironment.getApplication()
            WatchlistAddHelper.addEntry(app, "", "CoffeeShop", null)

            val result = WatchlistAddHelper.addEntry(app, "", null, "aa:bb:cc:dd:ee:ff")

            assertTrue(result is WatchlistAddHelper.AddResult.Added)
            assertEquals(2, dao.getAll().size)
        }

    @Test
    fun `addEntry with no identifiers is invalid and inserts nothing`() = runBlocking {
        val result =
            WatchlistAddHelper.addEntry(RuntimeEnvironment.getApplication(), "Label", " ", null)

        assertTrue(result is WatchlistAddHelper.AddResult.Invalid)
        assertEquals(0, dao.getAll().size)
    }

    @Test
    fun `addEntry does not enable the watchlist feature by itself`() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        WatchlistAddHelper.addEntry(app, "", "CoffeeShop", null)

        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        assertFalse(prefs.getBoolean(NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED, false))
    }

    @Test
    fun `enableWatchlistIfDisabled flips the feature on once`() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)

        assertTrue(WatchlistAddHelper.enableWatchlistIfDisabled(app))
        assertTrue(prefs.getBoolean(NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED, false))

        // Already on, so a second call reports no change (the caller uses this for its toast).
        assertFalse(WatchlistAddHelper.enableWatchlistIfDisabled(app))
        assertTrue(prefs.getBoolean(NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED, false))
    }
}
