package com.craxiom.networksurvey.logging.db.model

/**
 * Aggregate projection over a single watchlist entry's "Seen" history: how many times it was seen, the
 * strongest signal, and the most recent sighting time. Produced by one grouped query so the screens
 * can show "seen N times", "best dBm", and "Xm ago" without loading every hit.
 *
 * <p>All columns are aggregates ({@code COUNT}, {@code MAX}), so this projection carries no
 * bare-column ambiguity and the count and timestamp always come from the same emission (no flash of a
 * count without a matching time).
 *
 * <p>A row exists only for entries that have at least one hit, so the history screen iterates these
 * summaries (rather than every watched entry) to avoid listing never-seen networks. {@code bestRssi}
 * is {@code MAX(rssi)}: because RSSI is negative dBm, the maximum is the strongest signal.
 */
data class WatchlistGroupSummary(
    val entryId: Long,
    val sightings: Int,
    val bestRssi: Int,
    val lastSeen: Long,
)
