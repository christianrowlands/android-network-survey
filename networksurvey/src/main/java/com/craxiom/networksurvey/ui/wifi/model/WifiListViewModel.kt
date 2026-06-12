package com.craxiom.networksurvey.ui.wifi.model

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.craxiom.networksurvey.util.WifiBand
import com.craxiom.networksurvey.util.WifiUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

/**
 * View model for the redesigned Wi-Fi list. Owns the raw scan wrapper list, the user's
 * mode/sort/expansion state, and derives the display-ready item list for the Compose layer.
 *
 * Group-by-SSID mode is the default (handoff). Sort options (6) match the existing
 * [R.array.wifi_network_sort_options]:
 *  0: Signal (descending), 1: SSID, 2: BSSID, 3: Channel, 4: Frequency, 5: Security
 */
@HiltViewModel
class WifiListViewModel @Inject constructor(
    application: Application,
) : AndroidViewModel(application) {

    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(application)

    private val expandedGroupKeys = mutableSetOf<String>()

    // Per-BSSID wrapper map. The protobuf wrapper is kept out of the Compose state graph
    // (which would mark the whole display model unstable) composables navigate to Details
    // via [wrapperFor] when they need it.
    private val wrappersByBssid = mutableMapOf<String, WifiRecordWrapper>()

    // Per-BSSID display cache. Reused across scans to preserve reference identity for
    // unchanged APs so LazyColumn can skip row recomposition.
    private val displayCache = mutableMapOf<String, WifiAccessPointDisplay>()

    // Latest derived displays (ordered by current sort). The display filter reads this.
    private var latestSortedDisplays: List<WifiAccessPointDisplay> = emptyList()

    // Sorted displays that pass the current display filter. The grouping and interleave steps
    // read this so an active filter automatically drops empty groups and shrinks aggregates.
    private var latestFilteredDisplays: List<WifiAccessPointDisplay> = emptyList()

    // Latest derived group parents (ordered by current sort). Flipped `expanded` state is
    // applied by [toggleGroupExpanded] without a full rebuild.
    private var latestGroups: List<WifiDisplayItem.GroupParent> = emptyList()

    private val initialMode =
        if (preferences.getBoolean(NetworkSurveyConstants.PROPERTY_WIFI_GROUP_BY_SSID, true)) {
            WifiListMode.GROUPED
        } else {
            WifiListMode.FLAT
        }
    private val initialSortIndex =
        preferences.getInt(NetworkSurveyConstants.PROPERTY_WIFI_NETWORKS_SORT_ORDER, 0)

    private val _uiState = MutableStateFlow(
        WifiListUiState(
            mode = initialMode,
            sortIndex = initialSortIndex,
        )
    )
    val uiState: StateFlow<WifiListUiState> = _uiState.asStateFlow()

    /** Called by the Fragment on each scan callback. No-op while updates are paused. */
    fun onScanResults(wrappers: List<WifiRecordWrapper>) {
        if (_uiState.value.updatesPaused) return
        val current = _uiState.value
        rebuildDisplays(wrappers, current.sortIndex)
        _uiState.value = current.copy(
            scanNumber = current.scanNumber + 1,
            // Deduped total so "x of y" counts always match the rendered list.
            apCount = latestSortedDisplays.size,
            visibleApCount = latestFilteredDisplays.size,
            items = interleave(current.mode),
        )
    }

    /**
     * Look up the raw wrapper for a BSSID. Callers need this to build the Parcelable
     * [com.craxiom.networksurvey.model.WifiNetwork] when navigating to Details.
     */
    fun wrapperFor(bssid: String): WifiRecordWrapper? = wrappersByBssid[bssid]

    fun setScanStatus(statusId: Int) {
        _uiState.update { it.copy(scanStatusId = statusId) }
    }

    fun togglePaused(): Boolean {
        val next = !_uiState.value.updatesPaused
        _uiState.update { it.copy(updatesPaused = next) }
        return next
    }

    fun setMode(mode: WifiListMode) {
        if (_uiState.value.mode == mode) return
        preferences.edit {
            putBoolean(
                NetworkSurveyConstants.PROPERTY_WIFI_GROUP_BY_SSID,
                mode == WifiListMode.GROUPED,
            )
        }
        // Mode flip needs the sorted displays but not a fresh per-wrapper map.
        rebuildGroups()
        _uiState.update { it.copy(mode = mode, items = interleave(mode)) }
    }

    fun setSortIndex(index: Int) {
        if (_uiState.value.sortIndex == index) return
        preferences.edit {
            putInt(NetworkSurveyConstants.PROPERTY_WIFI_NETWORKS_SORT_ORDER, index)
        }
        // Sort change reorders existing cached displays; no per-wrapper mapping needed.
        latestSortedDisplays = latestSortedDisplays.sortedWith(apComparator(index))
        applyFilter()
        rebuildGroups(sortIndex = index)
        _uiState.update {
            it.copy(
                sortIndex = index,
                visibleApCount = latestFilteredDisplays.size,
                items = interleave(it.mode),
            )
        }
    }

    /** Updates the display filter's search query and re-derives the visible list. */
    fun setFilterQuery(query: String) {
        updateFilter { it.copy(searchQuery = query) }
    }

    /** Toggles a band in the display filter's band set and re-derives the visible list. */
    fun toggleFilterBand(band: WifiBand) {
        updateFilter { filter ->
            val bands = if (band in filter.selectedBands) {
                filter.selectedBands - band
            } else {
                filter.selectedBands + band
            }
            filter.copy(selectedBands = bands)
        }
    }

    /** Clears all display filter constraints, restoring the full list. */
    fun clearFilter() {
        updateFilter { WifiDisplayFilter() }
    }

    /**
     * Applies a transform to the current display filter and re-derives the filtered displays,
     * groups, and items. Works purely off cached displays, so it functions while scan updates
     * are paused or Wi-Fi is disabled.
     */
    private fun updateFilter(transform: (WifiDisplayFilter) -> WifiDisplayFilter) {
        val current = _uiState.value
        val newFilter = transform(current.filter)
        if (newFilter == current.filter) return
        applyFilter(newFilter)
        rebuildGroups()
        _uiState.value = current.copy(
            filter = newFilter,
            visibleApCount = latestFilteredDisplays.size,
            items = interleave(current.mode),
        )
    }

    /** Re-derives [latestFilteredDisplays] from [latestSortedDisplays] and the given filter. */
    private fun applyFilter(filter: WifiDisplayFilter = _uiState.value.filter) {
        latestFilteredDisplays = if (!filter.isActive) {
            latestSortedDisplays
        } else {
            latestSortedDisplays.filter(filter::matches)
        }
    }

    fun toggleGroupExpanded(groupKey: String) {
        if (!expandedGroupKeys.add(groupKey)) {
            expandedGroupKeys.remove(groupKey)
        }
        // Cheap path: flip `expanded` on cached groups without re-mapping or re-sorting.
        latestGroups = latestGroups.map { group ->
            val shouldExpand = expandedGroupKeys.contains(group.groupKey)
            if (group.expanded == shouldExpand) group else group.copy(expanded = shouldExpand)
        }
        val current = _uiState.value
        _uiState.value = current.copy(items = interleave(current.mode))
    }

    /**
     * Per-scan work: drop bad wrappers, update the BSSID→wrapper map, reuse cached display
     * entries whose scan data hasn't changed (preserving reference identity for Compose skip),
     * and sort into [latestSortedDisplays]. Follow-up [rebuildGroups] / [interleave] steps are
     * cheap because they work off these cached displays.
     */
    private fun rebuildDisplays(wrappers: List<WifiRecordWrapper>, sortIndex: Int) {
        // Drop empty-BSSID wrappers (scanner invariant violation; legacy adapter also couldn't
        // navigate to them) and de-duplicate by BSSID so LazyColumn keys never collide. Scanners
        // can emit the same BSSID across sub-scans or via MBSSID merges; a collision crashes the
        // list with IllegalStateException: Key was already used.
        val seen = HashSet<String>(wrappers.size)
        val dedupedWrappers = mutableListOf<WifiRecordWrapper>()
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
            dedupedWrappers += wrapper
        }
        if (emptyBssidDropped > 0) {
            Timber.w("Dropped %d Wi-Fi wrappers with empty BSSID", emptyBssidDropped)
        }
        if (duplicateBssidDropped > 0) {
            Timber.d("Dropped %d duplicate-BSSID Wi-Fi wrappers in scan", duplicateBssidDropped)
        }

        // Replace the wrapper map with the freshest wrappers for this scan.
        wrappersByBssid.clear()
        for (wrapper in dedupedWrappers) {
            wrappersByBssid[wrapper.wifiBeaconRecord.data.bssid] = wrapper
        }

        // Rebuild the display list, reusing cached entries whose value-equality matches.
        val displays = dedupedWrappers.map { wrapper ->
            val data = wrapper.wifiBeaconRecord.data
            val fresh = WifiAccessPointDisplay.fromWrapper(
                wrapper = wrapper,
                bandwidthLabel = WifiUtils.formatBandwidth(data.bandwidth),
                standardLabel = WifiUtils.formatStandard(data.standard),
                encryptionLabel = WifiBeaconMessageConstants.getEncryptionTypeString(data.encryptionType),
            )
            val cached = displayCache[fresh.bssid]
            if (cached == fresh) {
                cached // Same BSSID + same value fields -> keep reference identity.
            } else {
                displayCache[fresh.bssid] = fresh
                fresh
            }
        }

        // Trim cached entries for APs that disappeared this scan.
        displayCache.keys.retainAll(wrappersByBssid.keys)

        latestSortedDisplays = displays.sortedWith(apComparator(sortIndex))
        applyFilter()
        rebuildGroups(sortIndex)
    }

    /**
     * Rebuilds [latestGroups] from the already-sorted [latestFilteredDisplays]. Grouping runs
     * after the display filter so empty groups drop out and group aggregates only reflect
     * visible children.
     */
    private fun rebuildGroups(sortIndex: Int = _uiState.value.sortIndex) {
        latestGroups = buildGroupParents(latestFilteredDisplays, sortIndex, expandedGroupKeys)
    }

    /**
     * Interleaves cached filtered displays / groups into the final [WifiDisplayItem] list. Pure
     * function of cached state, no per-wrapper mapping, no re-sort.
     */
    private fun interleave(mode: WifiListMode): List<WifiDisplayItem> {
        if (latestFilteredDisplays.isEmpty()) return emptyList()
        return if (mode == WifiListMode.FLAT) {
            latestFilteredDisplays.map { WifiDisplayItem.Flat(it) }
        } else {
            val result = mutableListOf<WifiDisplayItem>()
            for (group in latestGroups) {
                result += group
                if (group.expanded) {
                    for (ap in group.aps) {
                        result += WifiDisplayItem.GroupChild(group.groupKey, ap)
                    }
                }
            }
            result
        }
    }

}

