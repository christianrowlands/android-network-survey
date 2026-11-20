package com.craxiom.networksurvey.ui.activesurvey

import android.content.Context
import androidx.room.Room
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.TowerKey
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

/**
 * Only a real answer from the tower service (200, 204 or 404) is cached; a rate limit, a server
 * error or no network is not, so the tower is asked about again next time. A tower the service has
 * is cached when it answers; one it does not have only once the caller remembers it.
 */
@RunWith(RobolectricTestRunner::class)
class TowerDetectionManagerTest {

    private lateinit var database: SurveyDatabase
    private lateinit var service: TowerServiceFake
    private lateinit var manager: TowerDetectionManager

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, SurveyDatabase::class.java).allowMainThreadQueries().build()
        service = TowerServiceFake()
        manager = TowerDetectionManager(service.api, { database.towerCacheDao() })
    }

    @After
    fun tearDown() {
        service.close()
        database.close()
    }

    @Test
    fun aTowerTheServiceHas_isCachedWhenItAnswers() = runBlocking {
        service.code = 200
        assertFalse(manager.checkIfTowerIsNew(key(1)))
        assertNotNull(cached(key(1)))
    }

    @Test
    fun aTowerTheServiceDoesNotHave_isCachedOnlyOnceRemembered() = runBlocking {
        for ((index, code) in listOf(204, 404).withIndex()) {
            service.code = code
            val key = key(index.toLong())
            assertTrue("HTTP $code reads as new", manager.checkIfTowerIsNew(key))
            assertNull("HTTP $code is not cached before its find is saved", cached(key))

            manager.rememberChecked(key)
            assertNotNull("HTTP $code is cached once remembered", cached(key))
        }
    }

    @Test
    fun aFailure_isNotCached() = runBlocking {
        for ((index, code) in listOf(429, 500, TowerServiceFake.DISCONNECT).withIndex()) {
            service.code = code
            val key = key(index.toLong())
            assertFalse("HTTP $code never reads as new", manager.checkIfTowerIsNew(key))
            assertNull("HTTP $code is not cached", cached(key))
        }
    }

    @Test
    fun aRememberedTower_comesFromTheCache() = runBlocking {
        service.code = 404
        assertTrue(manager.checkIfTowerIsNew(key(1)))
        manager.rememberChecked(key(1))
        assertFalse(manager.checkIfTowerIsNew(key(1)))
        assertEquals(1, service.requestCount)
    }

    @Test
    fun aTowerTheServiceDoesNotHave_isAskedAboutAgain_afterAFailure() = runBlocking {
        service.code = 500
        assertFalse(manager.checkIfTowerIsNew(key(1)))
        service.code = 204
        assertTrue(manager.checkIfTowerIsNew(key(1)))
    }

    @Test
    fun lookUp_readsTheTowersPosition() = runBlocking {
        service.code = 200
        assertEquals(
            TowerLookup.InDatabase(TowerServiceFake.TOWER_LATITUDE, TowerServiceFake.TOWER_LONGITUDE),
            manager.lookUp(key(1)),
        )
        service.code = 404
        assertEquals(TowerLookup.NotInDatabase, manager.lookUp(key(1)))
        service.code = 429
        assertEquals(TowerLookup.Failed, manager.lookUp(key(1)))
    }

    private fun key(cellId: Long) = TowerKey("310", "260", 12345, 25_600_000L + cellId, CellularProtocol.LTE)

    private fun cached(key: TowerKey) = database.towerCacheDao().getTower(key.mcc, key.mnc, key.area, key.cellId)
}
