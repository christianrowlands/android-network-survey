package com.craxiom.networksurvey.ui.newtowers

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.NewTowerFindStore
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.ui.activesurvey.NewTowerNotificationHelper
import com.craxiom.networksurvey.ui.activesurvey.TowerDetectionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** Whether the New towers page shows its list or its map. */
enum class NewTowersViewMode { LIST, MAP }

/**
 * What the New towers page shows.
 *
 * @property loaded False until the saved finds have been read once, so the page does not flash
 * an empty state.
 * @property alertsEnabled Whether the "New tower alerts" setting is on; nothing new is found while
 * it is off.
 * @property finds Every saved find, newest first. The Map view plots the ones with a location.
 * @property sessions [finds] grouped by the session that found them, for the List view.
 * @property detailFindId The find whose detail sheet is open, or null.
 * @property mapFocusFindId The find the Map view centers on and highlights ("Show on map"), or null.
 * @property mapFocusRequest Goes up with every "Show on map", so the Map view centers again even
 * when [mapFocusFindId] is already that find.
 */
data class NewTowersUiState(
    val loaded: Boolean = false,
    val alertsEnabled: Boolean = false,
    val finds: List<NewTowerFindEntity> = emptyList(),
    val sessions: List<NewTowerSession> = emptyList(),
    val mode: NewTowersViewMode = NewTowersViewMode.LIST,
    val detailFindId: Long? = null,
    val mapFocusFindId: Long? = null,
    val mapFocusRequest: Int = 0,
) {
    /** How many towers the list holds. */
    val count: Int get() = finds.size

    /** The find whose detail sheet is open, or null when none is (or it was cleared). */
    val detailFind: NewTowerFindEntity? get() = detailFindId?.let { id -> finds.firstOrNull { it.id == id } }
}

/**
 * Backs the New towers page: the saved finds grouped by session, the List/Map choice, the open
 * detail sheet, clearing the list, and turning the alerts on. Opening the page (creating this
 * view model) re-checks the finds that are due, through [NewTowerRechecker].
 */
class NewTowersViewModel internal constructor(
    application: Application,
    private val store: NewTowerFindStore,
    findsFlow: Flow<List<NewTowerFindEntity>>,
    private val preferences: SharedPreferences,
    rechecker: NewTowerRechecker,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        NewTowerFindStore.from(application),
        SurveyDatabase.getInstance(application).newTowerFindDao().observeAll(),
        PreferenceManager.getDefaultSharedPreferences(application),
        NewTowerRechecker(NewTowerFindStore.from(application), TowerDetectionManager(application)::lookUp),
    )

    private val mode = MutableStateFlow(NewTowersViewMode.LIST)
    private val detailFindId = MutableStateFlow<Long?>(null)
    private val mapFocus = MutableStateFlow<MapFocus?>(null)

    private val finds = findsFlow.catch { e ->
        Timber.e(e, "Could not read the new tower finds")
        emit(emptyList())
    }

    val uiState: StateFlow<NewTowersUiState> =
        combine(finds, alertsEnabledFlow(), mode, detailFindId, mapFocus) { all, alertsOn, viewMode, detailId, focus ->
            NewTowersUiState(
                loaded = true,
                alertsEnabled = alertsOn,
                finds = all,
                sessions = groupBySession(all),
                mode = viewMode,
                // A selection whose find was cleared is dropped
                detailFindId = detailId?.takeIf { id -> all.any { it.id == id } },
                mapFocusFindId = focus?.findId?.takeIf { id -> all.any { it.id == id } },
                mapFocusRequest = focus?.request ?: 0,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NewTowersUiState())

    init {
        viewModelScope.launch { rechecker.recheckDue() }
    }

    /** Shows the list or the map. */
    fun setMode(value: NewTowersViewMode) {
        mode.value = value
    }

    /** Opens the detail sheet of the find [id] (a row tap, or a marker tap on the map). */
    fun openDetail(id: Long) {
        detailFindId.value = id
    }

    /** Closes the detail sheet. */
    fun closeDetail() {
        detailFindId.value = null
    }

    /**
     * The detail sheet's "Show on map": closes the sheet and shows the map centered on the find
     * [id], centering again even when it is already the focus.
     */
    fun showOnMap(id: Long) {
        detailFindId.value = null
        mapFocus.update { MapFocus(id, (it?.request ?: 0) + 1) }
        mode.value = NewTowersViewMode.MAP
    }

    /** Clears the list, so its towers can alert again. */
    fun clearAll() {
        detailFindId.value = null
        mapFocus.value = null
        // The empty page shows the list; the next find should not land on the map
        mode.value = NewTowersViewMode.LIST
        // The alert or summary would otherwise point at a list that is now empty
        NewTowerNotificationHelper.cancel(getApplication())
        viewModelScope.launch(io) { store.clearAll() }
    }

    /** Turns the "New tower alerts" setting on, from the page's empty state. */
    fun enableAlerts() {
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_NEW_TOWER_ALERTS_ENABLED, true) }
    }

    private fun alertsEnabled(): Boolean = preferences.getBoolean(
        NetworkSurveyConstants.PROPERTY_NEW_TOWER_ALERTS_ENABLED,
        NetworkSurveyConstants.DEFAULT_NEW_TOWER_ALERTS_ENABLED,
    )

    /** The alerts setting now and on every change, wherever it is changed. */
    private fun alertsEnabledFlow(): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == NetworkSurveyConstants.PROPERTY_NEW_TOWER_ALERTS_ENABLED) trySend(alertsEnabled())
        }
        trySend(alertsEnabled())
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** One "Show on map" request: the find, and a number that is new for every request. */
    private data class MapFocus(val findId: Long, val request: Int)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
