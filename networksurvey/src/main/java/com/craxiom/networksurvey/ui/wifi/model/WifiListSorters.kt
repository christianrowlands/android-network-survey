package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants
import com.craxiom.networksurvey.model.WifiRecordWrapper
import timber.log.Timber

/**
 * Sorting, grouping and de-duplication helpers for the Wi-Fi list, extracted from [WifiListViewModel] to keep
 * that class focused on state management.
 */

/**
 * Per-AP comparator mirroring the legacy WifiViewModel comparator behavior.
 * See [com.craxiom.networksurvey.R.array.wifi_network_sort_options] - index mapping must stay
 * in sync.
 */
internal fun apComparator(sortIndex: Int): Comparator<WifiAccessPointDisplay> {
    return when (sortIndex) {
        1 -> compareBy { it.ssid }
        2 -> compareBy { it.bssid }
        3 -> compareBy { it.channel ?: Int.MAX_VALUE }
        4 -> compareBy { it.frequencyMhz ?: Int.MAX_VALUE }
        5 -> compareBy { it.encryptionLabel }
        else -> compareByDescending { it.rssi ?: Int.MIN_VALUE }
    }
}

/**
 * Group ordering for each sort index. BSSID / Channel / Frequency have no natural group-level
 * key, so they fall back to strongest-signal ordering of groups (children still sort by the
 * chosen key via [apComparator]).
 */
internal fun groupComparator(sortIndex: Int): Comparator<WifiDisplayItem.GroupParent> {
    return when (sortIndex) {
        1 -> compareBy { it.displaySsid }
        5 -> compareBy(
            { it.aps.firstOrNull()?.encryptionLabel ?: "" },
            { it.displaySsid },
        )

        else -> compareByDescending { it.bestRssi ?: Int.MIN_VALUE }
    }
}

/** Stable group key: hidden SSIDs group per-BSSID, everything else groups by SSID. */
internal fun groupKeyFor(ap: WifiAccessPointDisplay): String {
    return if (ap.ssidIsHidden) "hidden:${ap.bssid}" else "ssid:${ap.ssid}"
}

/**
 * Builds the sorted [WifiDisplayItem.GroupParent] list from already-sorted displays.
 * Group-child order respects `sortIndex` except for SSID (1) and Security (5), where per-group
 * child order falls back to strongest-signal, sorting all members of a group by SSID/security
 * is a no-op anyway.
 */
internal fun buildGroupParents(
    displays: List<WifiAccessPointDisplay>,
    sortIndex: Int,
    expandedGroupKeys: Set<String>,
): List<WifiDisplayItem.GroupParent> {
    if (displays.isEmpty()) return emptyList()
    val childSortIndex = if (sortIndex == 1 || sortIndex == 5) 0 else sortIndex
    val childComparator = apComparator(childSortIndex)

    return displays.groupBy { groupKeyFor(it) }.map { (groupKey, aps) ->
        val firstAp = aps.first()
        val allHidden = firstAp.ssidIsHidden
        val displaySsid = if (allHidden) firstAp.bssid else firstAp.ssid
        val sortedAps = aps.sortedWith(childComparator)
        val bestRssi = sortedAps.mapNotNull { it.rssi }.maxOrNull()
        val uniqueBands = sortedAps.mapNotNull { it.band }.distinct().sorted()
        val distinctSecurity =
            sortedAps.map { it.encryptionLabel }.filter { it.isNotBlank() }.distinct()
        val encryptionLabel = distinctSecurity.joinToString(WifiBeaconMessageConstants.SECURITY_LABEL_SEPARATOR)
        val anyPasspoint = sortedAps.any { it.passpoint }
        val excludedCount = sortedAps.count { it.isExcluded }
        WifiDisplayItem.GroupParent(
            groupKey = groupKey,
            displaySsid = displaySsid,
            isHidden = allHidden,
            aps = sortedAps,
            bestRssi = bestRssi,
            bands = uniqueBands,
            encryptionLabel = encryptionLabel,
            anyPasspoint = anyPasspoint,
            expanded = expandedGroupKeys.contains(groupKey),
            excludedCount = excludedCount,
        )
    }.sortedWith(groupComparator(sortIndex))
}

/**
 * Drops wrappers with an empty BSSID (scanner invariant violation; they cannot open details) and
 * de-duplicates by BSSID so LazyColumn keys never collide. Scanners can emit the same BSSID across
 * sub-scans or via MBSSID merges; a collision crashes the list with IllegalStateException: Key was
 * already used.
 */
internal fun dedupeByBssid(wrappers: List<WifiRecordWrapper>): List<WifiRecordWrapper> {
    val seen = HashSet<String>(wrappers.size)
    val deduped = mutableListOf<WifiRecordWrapper>()
    var emptyBssidDropped = 0
    var duplicateBssidDropped = 0
    for (wrapper in wrappers) {
        val bssid = wrapper.wifiBeaconRecord.data.bssid
        if (bssid.isNullOrBlank()) {
            emptyBssidDropped++
            continue
        }
        if (!seen.add(bssid)) {
            duplicateBssidDropped++
            continue
        }
        deduped += wrapper
    }
    if (emptyBssidDropped > 0) {
        Timber.w("Dropped %d Wi-Fi wrappers with empty BSSID", emptyBssidDropped)
    }
    if (duplicateBssidDropped > 0) {
        Timber.d("Dropped %d duplicate-BSSID Wi-Fi wrappers in scan", duplicateBssidDropped)
    }
    return deduped
}
