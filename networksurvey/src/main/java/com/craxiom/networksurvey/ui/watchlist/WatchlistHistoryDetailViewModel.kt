package com.craxiom.networksurvey.ui.watchlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for a single watched network's sighting-details screen. Loads the entry and its sightings
 * (newest first), tracks which sighting is selected so the map and list can stay in sync, and backs
 * the "clear this network's history" action (which leaves the watched entry in place).
 *
 * The target entry is supplied via [start] (from the navigation argument) rather than the constructor,
 * so the screen can use the default ViewModel factory.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistHistoryDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val hitDao = SurveyDatabase.getInstance(application).watchlistHitDao()
    private val entryDao = SurveyDatabase.getInstance(application).watchlistDao()

    private val entryId = MutableStateFlow<Long?>(null)
    private val selectedHitId = MutableStateFlow<Long?>(null)

    val uiState = entryId.flatMapLatest { id ->
        if (id == null) {
            flowOf(WatchlistDetailUiState())
        } else {
            combine(
                entryDao.observeById(id),
                hitDao.observeForEntry(id),
                selectedHitId
            ) { entry, hits, selected ->
                val located = hits.filter { it.latitude != null && it.longitude != null }
                val matchedByName = entry?.let { !it.ssid.isNullOrBlank() }
                    ?: (hits.firstOrNull()?.matchedField != WatchlistHitEntity.MATCHED_FIELD_BSSID)
                WatchlistDetailUiState(
                    title = entry?.label ?: entry?.ssid ?: entry?.bssid
                    ?: hits.firstOrNull()?.label ?: "",
                    matchedByName = matchedByName,
                    sightings = hits,
                    mapPoints = located.map { hit ->
                        WatchlistMapPoint(
                            id = hit.id.toString(),
                            latitude = hit.latitude!!,
                            longitude = hit.longitude!!,
                            colorHex = signalColorHex(hit.rssi)
                        )
                    },
                    selectedHitId = selected,
                    loaded = true
                )
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WatchlistDetailUiState()
    )

    fun start(id: Long) {
        if (entryId.value != id) entryId.value = id
    }

    fun selectHit(hitId: Long?) {
        selectedHitId.value = hitId
    }

    fun clearHistory() {
        val id = entryId.value ?: return
        viewModelScope.launch { hitDao.deleteForEntry(id) }
    }
}

/**
 * UI state for the sighting-details screen. [loaded] flips true once the entry's sightings have been
 * read, which the screen uses to detect "all cleared" and pop back.
 */
data class WatchlistDetailUiState(
    val title: String = "",
    val matchedByName: Boolean = true,
    val sightings: List<WatchlistHitEntity> = emptyList(),
    val mapPoints: List<WatchlistMapPoint> = emptyList(),
    val selectedHitId: Long? = null,
    val loaded: Boolean = false,
) {
    val sightingCount: Int get() = sightings.size
}
