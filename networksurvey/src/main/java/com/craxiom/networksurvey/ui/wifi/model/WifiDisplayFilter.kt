package com.craxiom.networksurvey.ui.wifi.model

import androidx.compose.runtime.Immutable
import com.craxiom.networksurvey.util.WifiBand

/**
 * Display-only filter for the Wi-Fi network list. This filter changes what is visible on
 * screen and never affects what is logged, streamed, or uploaded (that is the job of the
 * SSID Exclusion List).
 *
 * The filter is intentionally ephemeral: it is held in the activity-scoped
 * [WifiListViewModel] so it survives tab switches within a session but resets when the app
 * restarts. A persisted filter that silently hides networks across launches would look like
 * missing networks.
 */
@Immutable
data class WifiDisplayFilter(
    val searchQuery: String = "",
    val selectedBands: Set<WifiBand> = emptySet(),
) {
    /** True when the filter would hide anything, i.e. any constraint is set. */
    val isActive: Boolean get() = searchQuery.isNotBlank() || selectedBands.isNotEmpty()

    /**
     * Returns true if the given access point passes this filter.
     *
     * Text matching is a trimmed, case-insensitive substring match against the SSID or the
     * BSSID. BSSIDs are also compared with ":" and "-" separators stripped from both sides so
     * a query like "aabbcc" matches "AA:BB:CC:DD:EE:FF". Hidden SSIDs have an empty SSID and
     * can only match by BSSID, which mirrors how those rows are displayed.
     *
     * Band matching: an empty [selectedBands] set means all bands pass. When bands are
     * selected, an access point with an unmappable frequency (null band) fails the band
     * constraint by design.
     */
    fun matches(ap: WifiAccessPointDisplay): Boolean {
        if (selectedBands.isNotEmpty() && ap.band !in selectedBands) return false

        val query = searchQuery.trim()
        if (query.isEmpty()) return true

        if (ap.ssid.contains(query, ignoreCase = true)) return true
        if (ap.bssid.contains(query, ignoreCase = true)) return true

        val strippedQuery = stripSeparators(query)
        return strippedQuery.isNotEmpty() &&
                stripSeparators(ap.bssid).contains(strippedQuery, ignoreCase = true)
    }

    private fun stripSeparators(value: String): String {
        return value.replace(":", "").replace("-", "")
    }
}
