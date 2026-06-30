package com.craxiom.networksurvey.ui.watchlist

import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import com.craxiom.networksurvey.model.WatchlistImportSet

/**
 * Pure planning logic for a watchlist import. Given the current watchlist and a parsed import set, it
 * normalizes each incoming entry the same way [com.craxiom.networksurvey.ui.watchlist.WatchlistViewModel]
 * does for a manual add, dedupes against the existing entries AND within the batch (case-insensitive
 * SSID + BSSID), resolves a display label, and reports the new/duplicate counts the import dialog
 * shows. Kept free of Android and database dependencies so it is trivially unit-testable and reusable.
 */
object WatchlistImportPlanner {

    /** Build the import plan: which rows are new, which are duplicates, and the resulting counts. */
    fun plan(existing: List<WatchlistEntryEntity>, set: WatchlistImportSet): WatchlistImportPlan {
        val seenKeys = HashSet<String>()
        existing.forEach {
            seenKeys.add(
                dedupKey(
                    normalizeSsid(it.ssid),
                    normalizeBssid(it.bssid)
                )
            )
        }

        val previewRows = ArrayList<WatchlistImportPreviewRow>(set.entries.size)
        for (entry in set.entries) {
            val ssid = normalizeSsid(entry.ssid)
            val bssid = normalizeBssid(entry.bssid)
            val key = dedupKey(ssid, bssid)
            val isDuplicate = !seenKeys.add(key)
            previewRows.add(
                WatchlistImportPreviewRow(
                    label = resolveLabel(entry.label, ssid, bssid),
                    ssid = ssid,
                    bssid = bssid,
                    isDuplicate = isDuplicate
                )
            )
        }

        val addedCount = previewRows.count { !it.isDuplicate }
        return WatchlistImportPlan(
            previewRows = previewRows,
            addedCount = addedCount,
            duplicateCount = previewRows.size - addedCount
        )
    }

    /** Trim an SSID and treat blank as absent; case is preserved (SSIDs are case-sensitive). */
    fun normalizeSsid(ssid: String?): String? = ssid?.trim()?.takeIf { it.isNotEmpty() }

    /** Trim and lower-case a BSSID and treat blank as absent. */
    fun normalizeBssid(bssid: String?): String? =
        bssid?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    /** Resolve the display label: an explicit label, else the SSID, else the BSSID, else empty. */
    fun resolveLabel(label: String?, normalizedSsid: String?, normalizedBssid: String?): String =
        label?.trim()?.takeIf { it.isNotEmpty() } ?: normalizedSsid ?: normalizedBssid ?: ""

    /**
     * Build the case-insensitive uniqueness key for an (SSID, BSSID) pair. Two entries are duplicates
     * only when both fields match (a null field matches a null field), the same rule the manual-add
     * path uses. The SSID is length-prefixed so no SSID/BSSID content can be mistaken for a separator.
     */
    fun dedupKey(normalizedSsid: String?, normalizedBssid: String?): String {
        val ssidKey = normalizedSsid?.lowercase().orEmpty()
        val bssidKey = normalizedBssid.orEmpty()
        return ssidKey.length.toString() + ":" + ssidKey + ":" + bssidKey
    }
}

/** A single row in the import preview, plus whether it duplicates something already saved. */
data class WatchlistImportPreviewRow(
    val label: String,
    val ssid: String?,
    val bssid: String?,
    val isDuplicate: Boolean
)

/** The computed result of planning an import. */
data class WatchlistImportPlan(
    val previewRows: List<WatchlistImportPreviewRow>,
    val addedCount: Int,
    val duplicateCount: Int
) {
    /** The new (non-duplicate) rows, in incoming order, that should be inserted. */
    val toAdd: List<WatchlistImportPreviewRow> get() = previewRows.filterNot { it.isDuplicate }
}
