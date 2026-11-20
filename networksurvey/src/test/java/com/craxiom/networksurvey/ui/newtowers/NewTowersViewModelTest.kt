package com.craxiom.networksurvey.ui.newtowers

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.NewTowerFindStore
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.ui.activesurvey.NewTowerNotificationHelper
import com.craxiom.networksurvey.ui.activesurvey.TowerLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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

/**
 * The New towers page's view model: grouping, selection, Show on map, clear (back to the list, and
 * the alert or summary removed), alerts and re-check on open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NewTowersViewModelTest {

    private val app: Application = RuntimeEnvironment.getApplication()
    private lateinit var database: SurveyDatabase
    private lateinit var store: NewTowerFindStore
    private lateinit var preferences: SharedPreferences
    private val checked = mutableListOf<Long>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(app, SurveyDatabase::class.java).allowMainThreadQueries().build()
        store = NewTowerFindStore(database.newTowerFindDao())
        preferences = PreferenceManager.getDefaultSharedPreferences(app)
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun theState_groupsTheFindsBySession_newestFirst() = runBlocking {
        val finds = listOf(find(1, firstSeen = 1_100, session = 1_000), find(2, firstSeen = 5_100, session = 5_000), find(3, firstSeen = 1_200, session = 1_000))
        val viewModel = viewModel(MutableStateFlow(finds))

        val state = awaitState(viewModel) { it.loaded }

        assertEquals(3, state.count)
        assertEquals(listOf(5_000L, 1_000L), state.sessions.map { it.startedAt })
        assertEquals(listOf(3L, 1L), state.sessions.last().finds.map { it.id })
    }

    @Test
    fun aRowTap_opensItsDetail_andShowOnMap_closesItForTheCenteredMap() = runBlocking {
        val viewModel = viewModel(MutableStateFlow(listOf(find(1), find(2))))

        viewModel.openDetail(2)
        assertEquals(2L, awaitState(viewModel) { it.detailFindId != null }.detailFind?.id)

        viewModel.showOnMap(2)
        val state = awaitState(viewModel) { it.mode == NewTowersViewMode.MAP }
        assertNull(state.detailFindId)
        assertEquals(2L, state.mapFocusFindId)
    }

    @Test
    fun showOnMap_forTheFindAlreadyInFocus_asksTheMapToCenterAgain() = runBlocking {
        val viewModel = viewModel(MutableStateFlow(listOf(find(1), find(2))))

        viewModel.showOnMap(2)
        val first = awaitState(viewModel) { it.mapFocusFindId == 2L }.mapFocusRequest

        viewModel.openDetail(2)
        viewModel.showOnMap(2)
        val again = awaitState(viewModel) { it.mapFocusRequest != first && it.detailFindId == null }
        assertEquals(2L, again.mapFocusFindId)
        assertEquals(NewTowersViewMode.MAP, again.mode)
    }

    @Test
    fun aSelectionWhoseFindIsGone_isDropped() = runBlocking {
        val finds = MutableStateFlow(listOf(find(1)))
        val viewModel = viewModel(finds)
        viewModel.openDetail(1)
        awaitState(viewModel) { it.detailFind != null }

        finds.value = emptyList()

        val state = awaitState(viewModel) { it.count == 0 }
        assertNull(state.detailFindId)
        assertNull(state.detailFind)
    }

    @Test
    fun clear_emptiesTheSavedList() = runBlocking {
        store.recordFind(find(1, id = 0))
        val viewModel = viewModel(database.newTowerFindDao().observeAll())
        awaitState(viewModel) { it.count == 1 }

        viewModel.clearAll()

        assertEquals(0, awaitState(viewModel) { it.loaded && it.count == 0 }.count)
        assertEquals(0, database.newTowerFindDao().count())
    }

    @Test
    fun clear_afterShowOnMap_returnsToTheList() = runBlocking {
        val viewModel = viewModel(MutableStateFlow(listOf(find(1))))
        viewModel.showOnMap(1)
        awaitState(viewModel) { it.mode == NewTowersViewMode.MAP }

        viewModel.clearAll()

        val state = awaitState(viewModel) { it.mode == NewTowersViewMode.LIST }
        assertNull(state.mapFocusFindId)
    }

    @Test
    fun clear_removesTheAlertOrSummary() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifications = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        NewTowerNotificationHelper.showSessionSummary(app, 3)
        assertNotNull(notifications.getNotification(NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_ID))

        viewModel(MutableStateFlow(listOf(find(1)))).clearAll()

        assertNull(notifications.getNotification(NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_ID))
    }

    @Test
    fun theAlertsSetting_isFollowed_andTurnOnWritesIt() = runBlocking {
        val viewModel = viewModel(MutableStateFlow(emptyList()))
        assertFalse(awaitState(viewModel) { it.loaded }.alertsEnabled)

        viewModel.enableAlerts()

        assertTrue(awaitState(viewModel) { it.alertsEnabled }.alertsEnabled)
        assertTrue(preferences.getBoolean(NetworkSurveyConstants.PROPERTY_NEW_TOWER_ALERTS_ENABLED, false))
    }

    @Test
    fun openingThePage_reChecksTheDueFinds() = runBlocking {
        store.recordFind(find(7, id = 0).apply { lastCheckedAt = 0 })

        viewModel(MutableStateFlow(emptyList()))

        assertEquals(listOf(7L), checked)
    }

    private fun viewModel(finds: Flow<List<NewTowerFindEntity>>) = NewTowersViewModel(
        app,
        store,
        finds,
        preferences,
        NewTowerRechecker(
            store,
            lookUp = { key ->
                checked += key.cellId
                TowerLookup.NotInDatabase
            },
            clock = { NOW },
            io = Dispatchers.Unconfined,
        ),
        io = Dispatchers.Unconfined,
    )

    /** Waits for a state that matches [predicate]; collecting it is what starts the state. */
    private suspend fun awaitState(viewModel: NewTowersViewModel, predicate: (NewTowersUiState) -> Boolean): NewTowersUiState =
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first(predicate) }

    private fun find(cellId: Long, firstSeen: Long = 1_000, session: Long = 100, id: Long = cellId) = NewTowerFindEntity().apply {
        this.id = id
        mcc = "310"
        mnc = "260"
        area = 12345
        this.cellId = cellId
        radio = "LTE"
        firstSeenAt = firstSeen
        lastSeenAt = firstSeen
        sessionStartedAt = session
        lastCheckedAt = firstSeen
    }

    private companion object {
        const val NOW = 100_000_000_000L
        const val TIMEOUT_MS = 5_000L
    }
}
