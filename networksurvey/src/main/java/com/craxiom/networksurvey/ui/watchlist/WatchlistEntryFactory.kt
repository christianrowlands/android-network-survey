package com.craxiom.networksurvey.ui.watchlist

import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity

/**
 * Builds [WatchlistEntryEntity] instances with the app's standard defaults (exact match, enabled, and
 * the caller-supplied cooldown and creation time). Pure and side-effect-free so the entity defaults
 * are unit-testable; the time and cooldown source stay with the caller. Used by both the manual-add
 * and deep-link-import paths so a new entry is constructed identically regardless of origin.
 */
object WatchlistEntryFactory {

    fun create(
        label: String,
        ssid: String?,
        bssid: String?,
        cooldownSeconds: Int,
        createdAt: Long
    ): WatchlistEntryEntity = WatchlistEntryEntity().apply {
        this.label = label
        this.ssid = ssid
        this.bssid = bssid
        this.matchType = WatchlistEntryEntity.MATCH_TYPE_EXACT
        this.enabled = true
        this.createdAt = createdAt
        this.cooldownSeconds = cooldownSeconds
    }
}
