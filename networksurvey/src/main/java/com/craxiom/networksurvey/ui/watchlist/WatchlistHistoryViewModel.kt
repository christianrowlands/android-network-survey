package com.craxiom.networksurvey.ui.watchlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Watchlist history screen. Streams the "Seen" log and supports clearing it.
 */
class WatchlistHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val watchlistHitDao = SurveyDatabase.getInstance(application).watchlistHitDao()

    val uiState = watchlistHitDao.observeAll()
        .map { WatchlistHistoryUiState(hits = it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            WatchlistHistoryUiState()
        )

    fun clearHistory() {
        viewModelScope.launch { watchlistHitDao.clearAll() }
    }
}

/**
 * UI state for the Watchlist history screen.
 */
data class WatchlistHistoryUiState(
    val hits: List<WatchlistHitEntity> = emptyList()
)
