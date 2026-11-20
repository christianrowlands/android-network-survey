package com.craxiom.networksurvey.logging.db.dao

import android.content.Context
import androidx.room.Room
import com.craxiom.networksurvey.logging.db.NewTowerFindStore
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.logging.db.model.TowerCacheEntity
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.TowerKey
import com.craxiom.networksurvey.ui.activesurvey.TowerLookup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The saved new tower finds: one row per tower, capped, re-checked, and cleared with their cache. */
@RunWith(RobolectricTestRunner::class)
class NewTowerFindDaoTest {

    private lateinit var database: SurveyDatabase
    private lateinit var dao: NewTowerFindDao
    private lateinit var store: NewTowerFindStore

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, SurveyDatabase::class.java).allowMainThreadQueries().build()
        dao = database.newTowerFindDao()
        store = NewTowerFindStore(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aSecondFindOfTheSameTower_keepsTheFirstFind_andMovesTheLastSighting() {
        assertTrue(dao.recordFind(find(1, seenAt = 1_000, session = 500), MAX))
        assertFalse(dao.recordFind(find(1, seenAt = 9_000, session = 8_000), MAX))

        val saved = dao.find("310", "260", 12345, 1, "LTE")!!
        assertEquals(1, dao.count())
        assertEquals(1_000L, saved.firstSeenAt)
        assertEquals(9_000L, saved.lastSeenAt)
        assertEquals("The session that first found it", 500L, saved.sessionStartedAt)
    }

    @Test
    fun theSameCellOnAnotherRadio_isAnotherTower() {
        assertTrue(dao.recordFind(find(1), MAX))
        assertTrue(dao.recordFind(find(1, radio = "NR"), MAX))
        assertEquals(2, dao.count())
    }

    @Test
    fun theLastSighting_neverMovesBack() {
        dao.recordFind(find(1, seenAt = 5_000), MAX)
        val id = dao.find("310", "260", 12345, 1, "LTE")!!.id

        assertEquals(0, dao.updateLastSeen(id, 4_000))
        assertEquals(1, dao.updateLastSeen(id, 6_000))
        assertEquals(6_000L, dao.find("310", "260", 12345, 1, "LTE")!!.lastSeenAt)
    }

    @Test
    fun pastTheCap_theOldestFirstFindsAreDeleted() {
        dao.recordFind(find(1, seenAt = 3_000), 2)
        dao.recordFind(find(2, seenAt = 1_000), 2)
        dao.recordFind(find(3, seenAt = 2_000), 2)

        assertEquals(2, dao.count())
        assertNull("The oldest first find is gone", dao.find("310", "260", 12345, 2, "LTE"))
        assertNotNull(dao.find("310", "260", 12345, 1, "LTE"))
        assertNotNull(dao.find("310", "260", 12345, 3, "LTE"))
    }

    @Test
    fun theSessionReads_onlySeeThatSession_newestFirst() = runBlocking {
        dao.recordFind(find(1, seenAt = 1_000, session = 100), MAX)
        dao.recordFind(find(2, seenAt = 3_000, session = 100), MAX)
        dao.recordFind(find(3, seenAt = 2_000, session = 100), MAX)
        dao.recordFind(find(4, seenAt = 4_000, session = 200), MAX)

        assertEquals(3, dao.countForSession(100))
        assertEquals(listOf(2L, 3L), dao.newestForSession(100, 2).map { it.cellId })
        assertEquals(listOf(4L, 2L, 3L, 1L), dao.observeAll().first().map { it.cellId })
        assertEquals(4, dao.observeCount().first())
    }

    @Test
    fun onlyFindsNotInTheDatabase_checkedBeforeTheCutoff_needARecheck() {
        dao.recordFind(find(1, seenAt = 1_000), MAX)
        dao.recordFind(find(2, seenAt = 1_000), MAX)
        dao.recordFind(find(3, seenAt = 50_000), MAX)
        val inDatabase = dao.find("310", "260", 12345, 2, "LTE")!!.id
        dao.markChecked(inDatabase, true, 1_000, 38.9, -77.0)

        assertEquals(listOf(1L), dao.needingRecheck(checkedBefore = 10_000, limit = 10).map { it.cellId })
    }

    @Test
    fun theRecheck_takesTheLongestUncheckedFirst_thenTheNewestFind() {
        dao.recordFind(find(1, seenAt = 5_000, checkedAt = 3_000), MAX)
        dao.recordFind(find(2, seenAt = 1_000, checkedAt = 1_000), MAX)
        dao.recordFind(find(3, seenAt = 2_000, checkedAt = 2_000), MAX)
        dao.recordFind(find(4, seenAt = 4_000, checkedAt = 2_000), MAX)
        // Found at the same moment as 4 but saved after it, so the higher id goes first
        dao.recordFind(find(5, seenAt = 4_000, checkedAt = 2_000), MAX)

        assertEquals(listOf(2L, 5L, 4L, 3L, 1L), dao.needingRecheck(checkedBefore = 10_000, limit = 10).map { it.cellId })
        assertEquals("The limit keeps the longest unchecked", listOf(2L, 5L), dao.needingRecheck(checkedBefore = 10_000, limit = 2).map { it.cellId })
    }

    @Test
    fun theStore_recordsALookup_andIgnoresAFailedOne() {
        dao.recordFind(find(1, seenAt = 1_000), MAX)
        val id = dao.find("310", "260", 12345, 1, "LTE")!!.id

        assertFalse(store.recordLookup(id, TowerLookup.Failed, 5_000))
        assertEquals(1_000L, dao.find("310", "260", 12345, 1, "LTE")!!.lastCheckedAt)

        assertFalse(store.recordLookup(id, TowerLookup.NotInDatabase, 6_000))
        assertEquals(6_000L, dao.find("310", "260", 12345, 1, "LTE")!!.lastCheckedAt)

        assertTrue(store.recordLookup(id, TowerLookup.InDatabase(38.9, -77.0), 7_000))
        val checked = dao.find("310", "260", 12345, 1, "LTE")!!
        assertTrue(checked.inDatabase)
        assertEquals(7_000L, checked.lastCheckedAt)
        assertEquals(38.9, checked.towerLatitude!!, 0.0)
        assertEquals(-77.0, checked.towerLongitude!!, 0.0)
    }

    @Test
    fun theStore_offersARecheck_onlyAfterTheRecheckAge() {
        dao.recordFind(find(1, seenAt = 1_000), MAX)

        assertTrue(store.needingRecheck(now = 1_000 + NewTowerFindStore.RECHECK_AGE_MS, limit = 10).isEmpty())
        assertEquals(1, store.needingRecheck(now = 1_001 + NewTowerFindStore.RECHECK_AGE_MS, limit = 10).size)
    }

    @Test
    fun clearingTheList_forgetsTheCachedChecksOfItsTowers_only() {
        dao.recordFind(find(1), MAX)
        cache(1)
        cache(99)

        dao.clearAll()

        assertEquals(0, dao.count())
        assertNull(database.towerCacheDao().getTower("310", "260", 12345, 1))
        assertNotNull("A tower that was never on the list", database.towerCacheDao().getTower("310", "260", 12345, 99))
    }

    @Test
    fun theStore_findsByTowerKey() {
        dao.recordFind(find(7), MAX)
        assertNotNull(store.find(TowerKey("310", "260", 12345, 7, CellularProtocol.LTE)))
        assertNull(store.find(TowerKey("310", "260", 12345, 7, CellularProtocol.NR)))
    }

    private fun find(cellId: Long, seenAt: Long = 1_000, session: Long = 100, radio: String = "LTE", checkedAt: Long = seenAt) =
        NewTowerFindEntity().apply {
            mcc = "310"
            mnc = "260"
            area = 12345
            this.cellId = cellId
            this.radio = radio
            firstSeenAt = seenAt
            lastSeenAt = seenAt
            sessionStartedAt = session
            lastCheckedAt = checkedAt
        }

    private fun cache(cellId: Long) = database.towerCacheDao().insert(TowerCacheEntity().apply {
        mcc = "310"
        mnc = "260"
        area = 12345
        cid = cellId
        timestamp = 1
        radio = "LTE"
    })

    private companion object {
        const val MAX = 1000
    }
}
