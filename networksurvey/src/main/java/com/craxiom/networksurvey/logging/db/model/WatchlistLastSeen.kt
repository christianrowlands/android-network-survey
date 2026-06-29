package com.craxiom.networksurvey.logging.db.model

/**
 * Projection holding the most recent "Seen" timestamp for a single watchlist entry. Produced by a
 * grouped query so the management screen can show a "Seen Xm ago" subtitle for every entry with a
 * single query instead of one query per row.
 */
data class WatchlistLastSeen(
    val entryId: Long,
    val lastSeen: Long,
)
