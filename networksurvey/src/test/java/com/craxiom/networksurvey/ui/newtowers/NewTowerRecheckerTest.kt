package com.craxiom.networksurvey.ui.newtowers

import androidx.room.Room
import com.craxiom.networksurvey.logging.db.NewTowerFindStore
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.model.TowerKey
import com.craxiom.networksurvey.ui.activesurvey.TowerLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The New towers page's re-check on open: limited, one at a time, stopped by a failure, and worked
 * through over later opens when more finds are due than one open checks.
 */
@RunWith(RobolectricTestRunner::class)
class NewTowerRecheckerTest {

    private lateinit var database: SurveyDatabase
    private lateinit var store: NewTowerFindStore
    private val asked = mutableListOf<Long>()
    private var inFlight = 0
    private var mostInFlight = 0
    private var now = NOW

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SurveyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = NewTowerFindStore(database.newTowerFindDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun atMostTenFinds_areCheckedPerOpen_newestFirst_oneAtATime() = runBlocking {
        (1L..15L).forEach { save(it, firstSeen = it * 1_000) }

        val answered = rechecker { TowerLookup.NotInDatabase }.recheckDue()

        assertEquals(NewTowerRechecker.MAX_PER_OPEN, answered)
        assertEquals((15L downTo 6L).toList(), asked)
        assertEquals("No two checks at once", 1, mostInFlight)
    }

    @Test
    fun theFindsOneOpenSkipped_goFirstOnTheNextOpen() = runBlocking {
        (1L..12L).forEach { save(it, firstSeen = it * 1_000) }
        val rechecker = rechecker { TowerLookup.NotInDatabase }

        rechecker.recheckDue()
        assertEquals((12L downTo 3L).toList(), asked)

        asked.clear()
        now += NewTowerFindStore.RECHECK_AGE_MS + 1
        rechecker.recheckDue()

        assertEquals("The two skipped first, then the longest unchecked", listOf(2L, 1L) + (12L downTo 5L), asked)
    }

    @Test
    fun aTowerNowInTheDatabase_isMarked_withItsPosition() = runBlocking {
        save(1)

        rechecker { TowerLookup.InDatabase(51.5, -0.12) }.recheckDue()

        val find = only()
        assertTrue(find.inDatabase)
        assertEquals(NOW, find.lastCheckedAt)
        assertEquals(51.5, find.towerLatitude!!, 0.0)
        assertEquals(-0.12, find.towerLongitude!!, 0.0)
    }

    @Test
    fun aStillMissingTower_onlyMovesItsLastCheck() = runBlocking {
        save(1)

        rechecker { TowerLookup.NotInDatabase }.recheckDue()

        assertFalse(only().inDatabase)
        assertEquals(NOW, only().lastCheckedAt)
    }

    @Test
    fun aFailedCheck_leavesTheFind_andStopsTheRun() = runBlocking {
        save(1, firstSeen = 1_000)
        save(2, firstSeen = 2_000)

        val answered = rechecker { TowerLookup.Failed }.recheckDue()

        assertEquals(0, answered)
        assertEquals("Only the newest was asked", listOf(2L), asked)
        val lastChecks = database.newTowerFindDao().needingRecheck(Long.MAX_VALUE, 10).map { it.lastCheckedAt }
        assertEquals(listOf(STALE_CHECK, STALE_CHECK), lastChecks)
    }

    @Test
    fun recentlyCheckedFinds_andTowersAlreadyInTheDatabase_areNotAskedAbout() = runBlocking {
        save(1, lastChecked = NOW - NewTowerFindStore.RECHECK_AGE_MS + 60_000)
        save(2, inDatabase = true)

        assertEquals(0, rechecker { TowerLookup.NotInDatabase }.recheckDue())
        assertEquals(emptyList<Long>(), asked)
    }

    private fun rechecker(answer: () -> TowerLookup) = NewTowerRechecker(
        store = store,
        lookUp = { key: TowerKey ->
            inFlight++
            mostInFlight = maxOf(mostInFlight, inFlight)
            asked += key.cellId
            yield()
            inFlight--
            answer()
        },
        clock = { now },
        io = Dispatchers.Unconfined,
    )

    private fun only(): NewTowerFindEntity = database.newTowerFindDao().find("310", "260", 12345, 1, "LTE")!!

    private fun save(
        cellId: Long,
        firstSeen: Long = 1_000,
        lastChecked: Long = STALE_CHECK,
        inDatabase: Boolean = false,
    ) {
        store.recordFind(NewTowerFindEntity().apply {
            mcc = "310"
            mnc = "260"
            area = 12345
            this.cellId = cellId
            radio = "LTE"
            firstSeenAt = firstSeen
            lastSeenAt = firstSeen
            sessionStartedAt = 100
            lastCheckedAt = lastChecked
            this.inDatabase = inDatabase
        })
    }

    private companion object {
        const val NOW = 100_000_000_000L
        const val STALE_CHECK = NOW - NewTowerFindStore.RECHECK_AGE_MS - 1
    }
}
