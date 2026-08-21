package com.craxiom.networksurvey.services.watchlist

import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity

/**
 * Pure matching logic for deciding whether a Wi-Fi network satisfies a watchlist entry's match
 * rule. Shared by [WatchlistDetectionManager] (scan-time detection) and the Wi-Fi Details screen
 * (the "On watchlist" status) so the two can never drift apart.
 *
 * Rules: values are trimmed and compared case-insensitively. When an entry has both an SSID and a
 * BSSID, both must match. When it has only one, that field alone decides. An empty candidate SSID
 * (a hidden network) never satisfies an SSID rule. Trimming the candidate BSSID is a deliberate
 * hardening over the original inline detection code, which compared it untrimmed (scan-result
 * BSSIDs never carry whitespace, so this is unobservable in practice).
 */
object WatchlistMatcher {
    /**
     * Returns true when the network identified by [ssid]/[bssid] satisfies the match rule of
     * [entry]. Null and blank identifier values are treated as absent.
     */
    fun matches(entry: WatchlistEntryEntity, ssid: String?, bssid: String?): Boolean {
        val entrySsid = entry.ssid?.trim()?.takeIf { it.isNotEmpty() }
        val entryBssid = entry.bssid?.trim()?.takeIf { it.isNotEmpty() }
        if (entrySsid == null && entryBssid == null) return false

        val candidateSsid = ssid?.trim().orEmpty()
        val candidateBssid = bssid?.trim().orEmpty()

        val ssidMatches = entrySsid != null && candidateSsid.isNotEmpty() &&
                candidateSsid.equals(entrySsid, ignoreCase = true)
        val bssidMatches = entryBssid != null && candidateBssid.isNotEmpty() &&
                candidateBssid.equals(entryBssid, ignoreCase = true)

        return when {
            entrySsid != null && entryBssid != null -> ssidMatches && bssidMatches
            entrySsid != null -> ssidMatches
            else -> bssidMatches
        }
    }
}
