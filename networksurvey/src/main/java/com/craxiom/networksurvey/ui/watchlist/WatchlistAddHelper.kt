package com.craxiom.networksurvey.ui.watchlist

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import com.craxiom.networksurvey.services.watchlist.WatchlistDetectionManager

/**
 * Screen-agnostic helper for adding a single watched network. Normalizes the identifiers, dedupes
 * against the database (the source of truth, not a UI snapshot), and inserts an entry built with the
 * app's standard defaults. Used by both the Watchlist screen's ViewModel and the Wi-Fi Details
 * screen's "Watch" action so an entry is created identically regardless of origin.
 *
 * Deliberately NOT a ViewModel: [WatchlistViewModel]'s init consumes pending deep-link imports, so
 * other screens must never instantiate it just to add an entry.
 */
object WatchlistAddHelper {

    /** The outcome of an [addEntry] attempt. */
    sealed interface AddResult {
        /** The entry was inserted; [label] is the resolved display label. */
        data class Added(val label: String) : AddResult

        /** An entry with the same identity already exists; nothing was inserted. */
        data object Duplicate : AddResult

        /** Both identifiers were blank; nothing was inserted. */
        data object Invalid : AddResult
    }

    /**
     * Normalize, dedupe, and insert a new watched network. Duplicate identity uses the same rule as
     * the deep-link import ([WatchlistImportPlanner.dedupKey]): both fields must match.
     */
    suspend fun addEntry(
        context: Context,
        label: String,
        ssid: String?,
        bssid: String?
    ): AddResult {
        val normalizedSsid = WatchlistImportPlanner.normalizeSsid(ssid)
        val normalizedBssid = WatchlistImportPlanner.normalizeBssid(bssid)
        if (normalizedSsid == null && normalizedBssid == null) return AddResult.Invalid

        val resolvedLabel =
            WatchlistImportPlanner.resolveLabel(label, normalizedSsid, normalizedBssid)
        val key = WatchlistImportPlanner.dedupKey(normalizedSsid, normalizedBssid)

        val watchlistDao = SurveyDatabase.getInstance(context).watchlistDao()
        val duplicate = watchlistDao.getAll().any { existing ->
            WatchlistImportPlanner.dedupKey(
                WatchlistImportPlanner.normalizeSsid(existing.ssid),
                WatchlistImportPlanner.normalizeBssid(existing.bssid)
            ) == key
        }
        if (duplicate) return AddResult.Duplicate

        watchlistDao.insert(buildEntry(context, resolvedLabel, normalizedSsid, normalizedBssid))
        return AddResult.Added(resolvedLabel)
    }

    /**
     * Build a new entry with the app's standard defaults and the user's configured default cooldown.
     * Shared with the deep-link import path so every new entry is constructed identically.
     */
    fun buildEntry(
        context: Context,
        label: String,
        ssid: String?,
        bssid: String?
    ): WatchlistEntryEntity = WatchlistEntryFactory.create(
        label = label,
        ssid = ssid,
        bssid = bssid,
        cooldownSeconds = readDefaultCooldownSeconds(context),
        createdAt = System.currentTimeMillis()
    )

    /**
     * Turn the watchlist feature on when it is currently off. Adding a network to watch is an
     * explicit opt-in (the same reasoning as the deep-link import's force-enable). Returns true when
     * this call flipped the feature from off to on, so the caller can mention it to the user.
     *
     * [addEntry] deliberately does NOT call this; each call site decides whether its add is an
     * opt-in. The Wi-Fi Details screen enables (its user cannot see the feature status), while the
     * Watchlist screen does not (its status hero makes the off state obvious).
     */
    fun enableWatchlistIfDisabled(context: Context): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val enabled = preferences.getBoolean(
            NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED,
            NetworkSurveyConstants.DEFAULT_WATCHLIST_ENABLED
        )
        if (enabled) return false

        preferences.edit {
            putBoolean(NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED, true)
        }
        return true
    }

    private fun readDefaultCooldownSeconds(context: Context): Int =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getInt(
                NetworkSurveyConstants.PROPERTY_WATCHLIST_DEFAULT_COOLDOWN_SECONDS,
                WatchlistDetectionManager.DEFAULT_COOLDOWN_SECONDS
            )
}
