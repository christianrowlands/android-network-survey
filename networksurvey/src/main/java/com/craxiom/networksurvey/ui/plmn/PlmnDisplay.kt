package com.craxiom.networksurvey.ui.plmn

import androidx.compose.runtime.Immutable
import com.craxiom.networksurvey.data.api.PlmnRecord
import com.craxiom.networksurvey.data.plmn.displayName

/**
 * A grouping of [PlmnRecord]s that share the same MCC+MNC.
 *
 * The result list is rendered as flat rows when [records] has size 1, or as an expand/collapse
 * parent + indented child rows when shared (size > 1).
 *
 * Marked [Immutable] so Compose can skip recomposition of child rows that take a `PlmnGroup` /
 * `PlmnRecord` parameter unchanged across recompositions.
 */
@Immutable
data class PlmnGroup(
    val plmn: String,
    val mcc: String,
    val mnc: String,
    val country: String?,
    val iso: String?,
    val region: String?,
    val records: List<PlmnRecord>
)

/**
 * Groups records by `plmn` while preserving the order of first appearance. Reused by both the
 * Search and Resolve flows since each can return multiple operators for a shared PLMN.
 */
fun groupByPlmn(records: List<PlmnRecord>): List<PlmnGroup> {
    val map = linkedMapOf<String, MutableList<PlmnRecord>>()
    for (record in records) {
        map.getOrPut(record.plmn) { mutableListOf() }.add(record)
    }
    return map.map { (plmn, group) ->
        val first = group.first()
        PlmnGroup(
            plmn = plmn,
            mcc = first.mcc,
            mnc = first.mnc,
            country = first.country,
            iso = first.iso,
            region = first.region,
            records = group
        )
    }
}

/**
 * Sorts groups according to the user-selected key. All comparisons are stable (LinkedHashMap +
 * `sortedWith`) so equal-key entries keep their relative order from the backend.
 */
fun sortGroups(groups: List<PlmnGroup>, key: PlmnSortKey): List<PlmnGroup> = when (key) {
    PlmnSortKey.MccMnc -> groups.sortedWith(
        compareBy(
            { it.mcc.toIntOrNull() ?: Int.MAX_VALUE },
            { it.mnc.toIntOrNull() ?: Int.MAX_VALUE }
        )
    )

    PlmnSortKey.Country -> {
        val countryComparator: Comparator<PlmnGroup> =
            compareBy(nullsLast<String>(), { group: PlmnGroup -> group.country })
        groups.sortedWith(
            countryComparator.thenBy { it.records.first().displayName().lowercase() }
        )
    }

    PlmnSortKey.Provider -> groups.sortedWith(
        compareBy { it.records.first().displayName().lowercase() }
    )
}

/**
 * Converts a 2-letter ISO country code into its emoji flag using regional indicator code points.
 * Returns the globe glyph for null/blank input, multi-segment ISO strings (e.g. "BQ/CW/SX"),
 * the dataset's "XX" placeholder, or anything that isn't exactly two A-Z letters.
 */
fun isoToFlag(iso: String?): String {
    val trimmed = iso?.trim().orEmpty()
    if (trimmed.length != 2) return GLOBE
    if (trimmed.equals(PLACEHOLDER_ISO, ignoreCase = true)) return GLOBE
    val upper = trimmed.uppercase()
    if (!upper.all { it in 'A'..'Z' }) return GLOBE
    val first = 0x1F1E6 + (upper[0].code - 'A'.code)
    val second = 0x1F1E6 + (upper[1].code - 'A'.code)
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

private const val GLOBE = "🌐"
private const val PLACEHOLDER_ISO = "XX"
