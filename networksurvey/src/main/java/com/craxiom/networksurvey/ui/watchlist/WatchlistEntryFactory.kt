package com.craxiom.networksurvey.ui.watchlist

import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import java.util.UUID

/**
 * Builds [WatchlistEntryEntity] instances with the app's standard defaults (exact match, enabled, and
 * the caller-supplied cooldown and creation time). Pure and side-effect-free so the entity defaults
 * are unit-testable; the time and cooldown source stay with the caller. Used by both the manual-add
 * and deep-link-import paths so a new entry is constructed identically regardless of origin.
 * <p>
 * Every new entry is minted with a stable [WatchlistEntryEntity.uuid] (device-local; the deep-link
 * import contract is intentionally unchanged) and an [WatchlistEntryEntity.updatedAt] equal to its
 * creation time, so the entry can be published and correlated over MQTT from the moment it is created.
 */
object WatchlistEntryFactory {

    fun create(
        label: String,
        ssid: String?,
        bssid: String?,
        cooldownSeconds: Int,
        createdAt: Long
    ): WatchlistEntryEntity = WatchlistEntryEntity().apply {
        this.uuid = UUID.randomUUID().toString()
        this.label = label
        this.ssid = ssid
        this.bssid = bssid
        this.matchType = WatchlistEntryEntity.MATCH_TYPE_EXACT
        this.enabled = true
        this.createdAt = createdAt
        this.cooldownSeconds = cooldownSeconds
        this.updatedAt = createdAt
    }
}
