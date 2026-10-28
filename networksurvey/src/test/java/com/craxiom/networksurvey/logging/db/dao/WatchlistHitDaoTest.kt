package com.craxiom.networksurvey.logging.db.dao

import android.content.Context
import androidx.room.Room
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Tests for the grouped signal queries in [WatchlistHitDao]. A sighting of an access point that
 * reported no signal strength is stored with [WatchlistHitEntity.RSSI_UNKNOWN] (0), and these
 * guard that such a sighting never reads as a 0 dBm signal: it must not win the "best dBm"
 * aggregate and must come back as an unknown (null) latest signal.
 */
@RunWith(RobolectricTestRunner::class)
class WatchlistHitDaoTest {

    private lateinit var database: SurveyDatabase
    private lateinit var hitDao: WatchlistHitDao
    private lateinit var entryDao: WatchlistDao

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, SurveyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        hitDao = database.watchlistHitDao()
        entryDao = database.watchlistDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun newEntry(ssid: String): Long = runBlocking {
        entryDao.insert(WatchlistEntryEntity().apply {
            this.ssid = ssid
            matchType = WatchlistEntryEntity.MATCH_TYPE_EXACT
            enabled = true
            createdAt = 1L
        })
    }

    private fun insertHit(entryId: Long, rssi: Int, timestamp: Long) = runBlocking {
        hitDao.insert(WatchlistHitEntity().apply {
            this.entryId = entryId
            matchedField = WatchlistHitEntity.MATCHED_FIELD_SSID
            ssid = "Net"
            bssid = "12:34:56:78:9a:bc"
            this.rssi = rssi
            this.timestamp = timestamp
        })
    }

    @Test
    fun bestSignalIgnoresSightingsWithNoSignal() = runBlocking {
        val entryId = newEntry("Net")
        insertHit(entryId, rssi = -70, timestamp = 100)
        insertHit(entryId, rssi = WatchlistHitEntity.RSSI_UNKNOWN, timestamp = 200)

        val summary = hitDao.observeGroupSummaries().first().single()

        assertEquals(2, summary.sightings)
        assertEquals(-70, summary.bestRssi)
    }

    @Test
    fun bestSignalIsUnknownWhenNoSightingHasASignal() = runBlocking {
        val entryId = newEntry("Net")
        insertHit(entryId, rssi = WatchlistHitEntity.RSSI_UNKNOWN, timestamp = 100)

        val summary = hitDao.observeGroupSummaries().first().single()

        assertEquals(1, summary.sightings)
        assertNull(summary.bestRssi)
    }

    @Test
    fun latestSignalIsUnknownWhenTheNewestSightingHasNoSignal() = runBlocking {
        val entryId = newEntry("Net")
        insertHit(entryId, rssi = -60, timestamp = 100)
        insertHit(entryId, rssi = WatchlistHitEntity.RSSI_UNKNOWN, timestamp = 200)

        val lastSeen = hitDao.observeLastSeen().first().single()

        assertEquals(200L, lastSeen.lastSeen)
        assertNull(lastSeen.lastRssi)
    }

    @Test
    fun latestSignalIsTheNewestSightingsSignal() = runBlocking {
        val entryId = newEntry("Net")
        insertHit(entryId, rssi = WatchlistHitEntity.RSSI_UNKNOWN, timestamp = 100)
        insertHit(entryId, rssi = -55, timestamp = 200)

        val lastSeen = hitDao.observeLastSeen().first().single()

        assertEquals(-55, lastSeen.lastRssi)
    }
}
