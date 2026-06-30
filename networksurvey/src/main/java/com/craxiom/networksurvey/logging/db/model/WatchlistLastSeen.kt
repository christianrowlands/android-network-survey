package com.craxiom.networksurvey.logging.db.model

/**
 * Projection holding the most recent "Seen" time (and that sighting's signal) for a single watchlist
 * entry. Produced by a grouped query so the management screen can show a "seen Xm ago" subtitle and
 * the latest-known signal for every entry with a single query instead of one query per row.
 *
 * <p>{@code lastRssi} is the RSSI from the most recent hit: it is the bare column returned alongside
 * {@code MAX(timestamp)}, which SQLite guarantees comes from the same (max-timestamp) row. It is
 * nullable for safety even though a row only exists here when the entry has at least one hit.
 */
data class WatchlistLastSeen(
    val entryId: Long,
    val lastSeen: Long,
    val lastRssi: Int?,
)
