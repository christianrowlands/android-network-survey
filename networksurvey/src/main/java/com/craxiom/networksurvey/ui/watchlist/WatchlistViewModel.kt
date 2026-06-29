package com.craxiom.networksurvey.ui.watchlist

import android.app.Application
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import com.craxiom.networksurvey.services.watchlist.WatchlistDetectionManager
import com.craxiom.networksurvey.util.MdmUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Watchlist management screen. Exposes the user's watched networks (with each
 * entry's most recent "Seen" time from a single grouped query) and the feature's status, and
 * handles add, enable/disable, and delete actions.
 */
class WatchlistViewModel(application: Application) : AndroidViewModel(application) {

    private val watchlistDao = SurveyDatabase.getInstance(application).watchlistDao()
    private val watchlistHitDao = SurveyDatabase.getInstance(application).watchlistHitDao()

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                watchlistDao.observeAll(),
                watchlistHitDao.observeLastSeen()
            ) { entries, lastSeenList ->
                val lastSeenByEntry = lastSeenList.associate { it.entryId to it.lastSeen }
                entries.map { WatchlistRowItem(it, lastSeenByEntry[it.id]) }
            }.collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
        refreshStatus()
    }

    /**
     * Re-read the enabled and notifications-permission status. Call when the screen resumes so a
     * permission change made in system settings is reflected.
     */
    fun refreshStatus() {
        _uiState.update {
            it.copy(
                watchlistEnabled = readEnabled(),
                notificationsEnabled = NotificationManagerCompat.from(getApplication())
                    .areNotificationsEnabled(),
                mdmControlled = MdmUtils.isUnderMdmControlAndEnabled(
                    getApplication(),
                    NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED
                )
            )
        }
    }

    fun setWatchlistEnabled(enabled: Boolean) {
        // Respect MDM: when a device administrator controls this setting, the user cannot change it.
        if (MdmUtils.isUnderMdmControlAndEnabled(
                getApplication(),
                NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED
            )
        ) {
            return
        }
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit()
            .putBoolean(NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED, enabled)
            .apply()
        _uiState.update { it.copy(watchlistEnabled = enabled) }
    }

    /**
     * Add a new watched network. Returns nothing; surfaces duplicate errors as a toast. The dialog
     * is responsible for field-level validation (required field, BSSID format) before calling this.
     *
     * @param label the user-facing name (falls back to the SSID, then the BSSID, when blank)
     * @param ssid the SSID to watch, or null/blank to watch by BSSID only
     * @param bssid the BSSID to watch, or null/blank to watch by SSID only
     */
    fun addEntry(label: String, ssid: String?, bssid: String?) {
        val normalizedSsid = ssid?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedBssid = bssid?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (normalizedSsid == null && normalizedBssid == null) return

        val resolvedLabel = label.trim().ifEmpty { normalizedSsid ?: normalizedBssid ?: "" }

        val duplicate = _uiState.value.items.any { row ->
            val existingSsid = row.entry.ssid?.trim()?.takeIf { it.isNotEmpty() }
            val existingBssid = row.entry.bssid?.trim()?.takeIf { it.isNotEmpty() }
            existingSsid.equals(normalizedSsid, ignoreCase = true) &&
                    existingBssid.equals(normalizedBssid, ignoreCase = true)
        }
        if (duplicate) {
            showToast(getApplication<Application>().getString(R.string.watchlist_validation_duplicate))
            return
        }

        viewModelScope.launch {
            val entry = WatchlistEntryEntity().apply {
                this.label = resolvedLabel
                this.ssid = normalizedSsid
                this.bssid = normalizedBssid
                this.matchType = WatchlistEntryEntity.MATCH_TYPE_EXACT
                this.enabled = true
                this.createdAt = System.currentTimeMillis()
                this.cooldownSeconds = readDefaultCooldownSeconds()
            }
            watchlistDao.insert(entry)
            showToast(
                getApplication<Application>().getString(
                    R.string.watchlist_added,
                    resolvedLabel
                )
            )
        }
    }

    fun setEntryEnabled(entry: WatchlistEntryEntity, enabled: Boolean) {
        viewModelScope.launch {
            entry.enabled = enabled
            watchlistDao.update(entry)
        }
    }

    fun deleteEntry(entry: WatchlistEntryEntity) {
        viewModelScope.launch { watchlistDao.delete(entry) }
    }

    private fun readEnabled(): Boolean =
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .getBoolean(
                NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED,
                NetworkSurveyConstants.DEFAULT_WATCHLIST_ENABLED
            )

    private fun readDefaultCooldownSeconds(): Int =
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .getInt(
                NetworkSurveyConstants.PROPERTY_WATCHLIST_DEFAULT_COOLDOWN_SECONDS,
                WatchlistDetectionManager.DEFAULT_COOLDOWN_SECONDS
            )

    private fun showToast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * A single row on the management screen: a watched network plus its most recent "Seen" time.
 */
data class WatchlistRowItem(
    val entry: WatchlistEntryEntity,
    val lastSeen: Long?
)

/**
 * UI state for the Watchlist management screen.
 */
data class WatchlistUiState(
    val items: List<WatchlistRowItem> = emptyList(),
    val watchlistEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val mdmControlled: Boolean = false
)
