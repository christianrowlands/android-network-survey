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
import com.craxiom.networksurvey.model.WatchlistImportSet
import com.craxiom.networksurvey.services.watchlist.WatchlistDetectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.core.content.edit

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
        // Consume a pending import deep link here rather than from the screen's ON_RESUME: because the
        // watchlist navigation recreates this ViewModel, only the freshly created instance reads the
        // holder, which avoids a soon-to-be-destroyed instance swallowing the import on a re-tap.
        consumePendingImport()
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
                    .areNotificationsEnabled()
            )
        }
    }

    fun setWatchlistEnabled(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit {
                putBoolean(NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED, enabled)
            }
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
        val normalizedSsid = WatchlistImportPlanner.normalizeSsid(ssid)
        val normalizedBssid = WatchlistImportPlanner.normalizeBssid(bssid)
        if (normalizedSsid == null && normalizedBssid == null) return

        val resolvedLabel =
            WatchlistImportPlanner.resolveLabel(label, normalizedSsid, normalizedBssid)
        val key = WatchlistImportPlanner.dedupKey(normalizedSsid, normalizedBssid)

        val duplicate = _uiState.value.items.any { row ->
            WatchlistImportPlanner.dedupKey(
                WatchlistImportPlanner.normalizeSsid(row.entry.ssid),
                WatchlistImportPlanner.normalizeBssid(row.entry.bssid)
            ) == key
        }
        if (duplicate) {
            showToast(getApplication<Application>().getString(R.string.watchlist_validation_duplicate))
            return
        }

        viewModelScope.launch {
            watchlistDao.insert(buildEntry(resolvedLabel, normalizedSsid, normalizedBssid))
            showToast(
                getApplication<Application>().getString(
                    R.string.watchlist_added,
                    resolvedLabel
                )
            )
        }
    }

    /**
     * Pick up a watchlist import stashed by the Activity from a deep link, if any. Reads the current
     * entries from the database (not the Flow snapshot, which may be empty on a cold-start deep link)
     * to compute the new-vs-duplicate preview. Does nothing when there is no pending import, so a later
     * call (for example on a subsequent ON_RESUME) never clears an import dialog already on screen.
     */
    fun consumePendingImport() {
        val set = WatchlistImportHolder.consume() ?: return
        viewModelScope.launch {
            val plan = WatchlistImportPlanner.plan(watchlistDao.getAll(), set)
            _uiState.update {
                it.copy(
                    pendingImport = PendingWatchlistImport(
                        name = set.name,
                        previewRows = plan.previewRows,
                        addedCount = plan.addedCount,
                        duplicateCount = plan.duplicateCount,
                        set = set
                    )
                )
            }
        }
    }

    /** Insert the new networks from the pending import, re-deduping against the live database. */
    fun confirmPendingImport() {
        val pending = _uiState.value.pendingImport ?: return
        viewModelScope.launch {
            val plan = WatchlistImportPlanner.plan(watchlistDao.getAll(), pending.set)
            val newEntries = plan.toAdd.map { buildEntry(it.label, it.ssid, it.bssid) }
            if (newEntries.isNotEmpty()) {
                watchlistDao.insertAll(newEntries)
            }
            // Importing a list is an explicit opt-in, so turn the feature on (after the insert, so the
            // detection manager's enabled-entry snapshot already includes the new rows). This may
            // re-enable a feature the user previously turned off; that is intended for a deep-link import.
            setWatchlistEnabled(true)
            _uiState.update { it.copy(pendingImport = null) }
            showImportResultToast(plan.addedCount, plan.duplicateCount)
        }
    }

    /** Dismiss the import prompt without adding anything. */
    fun dismissPendingImport() {
        _uiState.update { it.copy(pendingImport = null) }
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

    /**
     * Delete every watched network. This is the deliberate, link-independent way to "start fresh"
     * before importing. Mirrors [deleteEntry] (a bulk version of the same per-row delete).
     */
    fun clearAll() {
        viewModelScope.launch { watchlistDao.deleteAll() }
    }

    private fun buildEntry(label: String, ssid: String?, bssid: String?): WatchlistEntryEntity =
        WatchlistEntryFactory.create(
            label = label,
            ssid = ssid,
            bssid = bssid,
            cooldownSeconds = readDefaultCooldownSeconds(),
            createdAt = System.currentTimeMillis()
        )

    private fun showImportResultToast(added: Int, skipped: Int) {
        val res = getApplication<Application>().resources
        val message = if (skipped > 0) {
            res.getQuantityString(R.plurals.watchlist_import_result, added, added, skipped)
        } else {
            res.getQuantityString(R.plurals.watchlist_import_result_no_skips, added, added)
        }
        showToast(message)
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
    val pendingImport: PendingWatchlistImport? = null
)

/**
 * A watchlist import awaiting the user's confirmation, with everything the import prompt needs: the
 * optional (sanitized) set name, the per-row preview, the new/duplicate counts, and the original set
 * retained so the insert can re-dedupe against the live database at confirm time.
 */
data class PendingWatchlistImport(
    val name: String?,
    val previewRows: List<WatchlistImportPreviewRow>,
    val addedCount: Int,
    val duplicateCount: Int,
    val set: WatchlistImportSet
) {
    /** True when every network in the link is already saved, so there is nothing to add. */
    val allDuplicates: Boolean get() = addedCount == 0

    /** The total number of networks the link carried. */
    val totalCount: Int get() = previewRows.size
}
