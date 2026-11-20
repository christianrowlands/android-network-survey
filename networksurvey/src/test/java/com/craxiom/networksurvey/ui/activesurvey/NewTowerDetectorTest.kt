package com.craxiom.networksurvey.ui.activesurvey

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.NewTowerFindStore
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.model.TowerKey
import com.craxiom.networksurvey.waitFor
import com.craxiom.networksurvey.ui.map.CellRecords
import kotlinx.coroutines.Dispatchers
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The order of a serving-cell check: a saved tower never alerts again, a tower the tower service
 * does not have is saved, cached and alerted on, and a check whose upload session ended (or that
 * could not save its find) does none of that. Ending the session replaces the alert with a summary
 * of that session's finds, and posts nothing when no session ran or it found nothing.
 */
@RunWith(RobolectricTestRunner::class)
class NewTowerDetectorTest {

    private val servingCell = CellRecords.lte()
    private val key: TowerKey = TowerKey.from(servingCell)!!

    private lateinit var database: SurveyDatabase
    private lateinit var service: TowerServiceFake
    private lateinit var store: NewTowerFindStore
    private lateinit var detector: NewTowerDetector
    // Written from the serial dispatcher in the endSession tests
    private val alerts = CopyOnWriteArrayList<Pair<List<NewTowerFindEntity>, Int>>()
    private var now = 1_000_000L

    @Before
    fun setUp() {
        NewTowerRegistry.endSession()
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, SurveyDatabase::class.java).allowMainThreadQueries().build()
        service = TowerServiceFake()
        store = NewTowerFindStore(database.newTowerFindDao())
        detector = NewTowerDetector(
            TowerDetectionManager(service.api, { database.towerCacheDao() }, { now }),
            store,
            { newest, count -> alerts += newest to count },
            clock = { now },
        )
    }

    @After
    fun tearDown() {
        NewTowerRegistry.endSession()
        service.close()
        database.close()
    }

    @Test
    fun aTowerTheServiceDoesNotHave_isSavedAndAlertedOn() = runBlocking {
        val session = NewTowerRegistry.startSession(500L)
        service.code = 404

        detector.process(key, servingCell, session)

        val saved = store.find(key)!!
        assertEquals(session, saved.sessionStartedAt)
        assertEquals(now, saved.firstSeenAt)
        assertEquals("Mint", saved.operatorName)
        assertEquals(-79, saved.signalDbm)
        assertFalse(saved.inDatabase)
        assertEquals(1, alerts.size)
        assertEquals(1, alerts.single().second)
        assertEquals(listOf(key.cellId), alerts.single().first.map { it.cellId })
        assertTrue(key in NewTowerRegistry.newTowers.value)
        assertNotNull("Cached once its find is saved", cached())
    }

    @Test
    fun aTowerTheServiceHas_isNeitherSavedNorAlertedOn() = runBlocking {
        val session = NewTowerRegistry.startSession()
        service.code = 200

        detector.process(key, servingCell, session)

        assertNull(store.find(key))
        assertTrue(alerts.isEmpty())
        assertNotNull(cached())
    }

    @Test
    fun aCheckThatFinishesAfterTheUploadStopped_neitherSavesNorAlertsNorCaches() = runBlocking {
        val session = NewTowerRegistry.startSession()
        service.code = 404
        service.onRequest = { NewTowerRegistry.endSession() }

        detector.process(key, servingCell, session)

        assertNull(store.find(key))
        assertTrue(alerts.isEmpty())
        assertNull(cached())
    }

    @Test
    fun aCheckFromAStoppedSession_isDropped_afterAQuickRestart() = runBlocking {
        val stopped = NewTowerRegistry.startSession(100L)
        service.code = 404
        service.onRequest = { NewTowerRegistry.startSession(200L) }

        detector.process(key, servingCell, stopped)

        assertNull(store.find(key))
        assertTrue(alerts.isEmpty())
        assertNull(cached())
        assertFalse(key in NewTowerRegistry.newTowers.value)

        // The new session asks again and finds it for itself
        service.onRequest = {}
        detector.process(key, servingCell, 200L)
        assertEquals(200L, store.find(key)!!.sessionStartedAt)
        assertEquals(1, alerts.size)
        assertEquals(2, service.requestCount)
    }

    @Test
    fun aCheckQueuedBeforeTheUploadStopped_neverAsksTheService() = runBlocking {
        val session = NewTowerRegistry.startSession()
        NewTowerRegistry.endSession()

        detector.process(key, servingCell, session)

        assertEquals(0, service.requestCount)
    }

    @Test
    fun aFindThatCannotBeSaved_isNeitherAlertedNorCachedNorTagged() = runBlocking {
        val broken = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication() as Context, SurveyDatabase::class.java)
            .allowMainThreadQueries().build()
        val brokenDao = broken.newTowerFindDao()
        broken.close()
        val failing = NewTowerDetector(
            TowerDetectionManager(service.api, { database.towerCacheDao() }, { now }),
            NewTowerFindStore(brokenDao),
            { newest, count -> alerts += newest to count },
            clock = { now },
        )
        val session = NewTowerRegistry.startSession()
        service.code = 404

        failing.process(key, servingCell, session)

        assertTrue(alerts.isEmpty())
        assertNull("Asked about again next time", cached())
        assertFalse("Untagged, so the next check runs in full", key in NewTowerRegistry.newTowers.value)
    }

    @Test
    fun aSavedTower_neverAlertsAgain_butIsTaggedOnTheMap_andItsLastSightingMoves() = runBlocking {
        service.code = 404
        detector.process(key, servingCell, NewTowerRegistry.startSession(100L))

        // A later upload session, after the tower cache has forgotten the tower
        val later = NewTowerRegistry.startSession(200L)
        database.towerCacheDao().deleteOldEntries(Long.MAX_VALUE)
        now += 60_000
        val requests = service.requestCount
        detector.process(key, servingCell, later)

        assertEquals(1, alerts.size)
        assertEquals("Checked recently, so not asked about again", requests, service.requestCount)
        assertEquals(now, store.find(key)!!.lastSeenAt)
        assertTrue(key in NewTowerRegistry.newTowers.value)
    }

    @Test
    fun aSavedTower_isRecheckedSilently_onceTheRecheckAgePasses() = runBlocking {
        service.code = 404
        detector.process(key, servingCell, NewTowerRegistry.startSession(100L))

        val later = NewTowerRegistry.startSession(200L)
        now += NewTowerFindStore.RECHECK_AGE_MS + 1
        service.code = 200
        detector.process(key, servingCell, later)

        val saved = store.find(key)!!
        assertTrue(saved.inDatabase)
        assertEquals(now, saved.lastCheckedAt)
        assertNotNull(saved.towerLatitude)
        assertEquals(1, alerts.size)
        assertFalse("Now in the tower database, so not tagged NEW", key in NewTowerRegistry.newTowers.value)
    }

    @Test
    fun theAlert_countsOnlyThisSessionsFinds() = runBlocking {
        service.code = 404
        detector.process(key, servingCell, NewTowerRegistry.startSession(100L))

        val other = CellRecords.lte(eci = 25_600_104)
        now += 1_000
        detector.process(TowerKey.from(other)!!, other, NewTowerRegistry.startSession(200L))

        assertEquals(1, alerts.last().second)
        assertEquals(1, store.countForSession(200L))
    }

    @Test
    fun endSession_withNoSessionRunning_postsNothing() {
        val notifications = notifications()

        NewTowerDetector.endSession(RuntimeEnvironment.getApplication())

        drainSerialQueue()
        assertNull(notifications.getNotification(NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_ID))
    }

    @Test
    fun endSession_afterASessionWithFinds_postsItsSummary() {
        val notifications = notifications()
        val session = NewTowerRegistry.startSession(700L)
        onAppStore { store ->
            store.recordFind(savedFind(1, session))
            store.recordFind(savedFind(2, session))
            store.recordFind(savedFind(3, session = 300L))
            assertEquals(2, store.countForSession(session))
        }

        NewTowerDetector.endSession(RuntimeEnvironment.getApplication())

        val summary = waitFor { notifications.getNotification(NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_ID) }
        assertNotNull("No summary was shown", summary)
        val context: Context = RuntimeEnvironment.getApplication()
        assertEquals(context.getString(R.string.new_tower_summary_title), summary!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Only this session's finds",
            context.resources.getQuantityString(R.plurals.new_towers_found_count, 2, 2),
            summary.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertNull("The session is over", NewTowerRegistry.sessionStartedAt)
    }

    @Test
    fun endSession_afterASessionWithNoFinds_postsNothing() {
        val notifications = notifications()
        onAppStore { store -> assertTrue(store.recordFind(savedFind(3, session = 300L))) }
        NewTowerRegistry.startSession(800L)

        NewTowerDetector.endSession(RuntimeEnvironment.getApplication())

        drainSerialQueue()
        assertNull(notifications.getNotification(NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_ID))
    }

    /**
     * Grants the notification permission, empties the app database's new tower list (which
     * [NewTowerDetector.endSession] reads), and returns the notification manager's shadow.
     */
    private fun notifications(): ShadowNotificationManager {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        onAppStore { store -> store.clearAll() }
        return shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
    }

    /**
     * Runs [block] against the app database's store, off the main thread: unlike the in-memory
     * test database, the app database refuses main-thread queries, and the store would only log it.
     */
    private fun onAppStore(block: (NewTowerFindStore) -> Unit) = runBlocking(Dispatchers.IO) {
        block(NewTowerFindStore.from(RuntimeEnvironment.getApplication()))
    }

    /**
     * Waits until the serial dispatcher has run everything queued before this call: queues a check
     * behind it (through [detector], which shares that dispatcher) and waits for its alert.
     */
    private fun drainSerialQueue() {
        service.code = 404
        val before = alerts.size
        NewTowerRegistry.startSession(now)
        detector.check(key, servingCell)
        assertNotNull("The serial dispatcher never reached the queued check", waitFor { if (alerts.size > before) true else null })
        NewTowerRegistry.endSession()
    }

    private fun savedFind(cellId: Long, session: Long) = NewTowerFindEntity().apply {
        mcc = key.mcc
        mnc = key.mnc
        area = key.area
        this.cellId = cellId
        radio = key.radio
        firstSeenAt = now
        lastSeenAt = now
        sessionStartedAt = session
        lastCheckedAt = now
    }

    private fun cached() = database.towerCacheDao().getTower(key.mcc, key.mnc, key.area, key.cellId)
}
