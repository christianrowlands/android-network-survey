package com.craxiom.networksurvey.ui.watchlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How the history groups are ordered. */
enum class HistorySort { RECENT, STRONGEST, MOST_SEEN }

/** Whether the history is shown as a list of networks or a map of sightings. */
enum class HistoryViewMode { LIST, MAP }

/**
 * Per-network color palette for the history map pins and legend. The first watched network uses the
 * SSID accent so a single-network history reads on-brand; the rest cycle a fixed palette.
 */
private val WATCHLIST_NETWORK_COLORS = listOf(
    "#03A9F4", "#BA68C8", "#66BB6A", "#FFA726",
    "#EF5350", "#26C6DA", "#FFCA28", "#8D6E63",
)

internal fun watchlistNetworkColorHex(index: Int): String =
    WATCHLIST_NETWORK_COLORS[index % WATCHLIST_NETWORK_COLORS.size]

/**
 * ViewModel for the Watchlist history screen. Streams the "Seen" log grouped by watched network (only
 * networks that have actually been seen appear), and exposes search, sort, and a list/map view mode.
 * The map data (per-sighting points and a per-network legend) is derived from the located hits.
 */
class WatchlistHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val hitDao = SurveyDatabase.getInstance(application).watchlistHitDao()
    private val entryDao = SurveyDatabase.getInstance(application).watchlistDao()

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(HistorySort.RECENT)
    private val mode = MutableStateFlow(HistoryViewMode.LIST)

    private data class HistoryData(
        val groups: List<WatchlistHistoryGroup>,
        val mapPoints: List<WatchlistMapPoint>,
        val legend: List<WatchlistMapLegendEntry>,
        val totalSightings: Int,
    )

    private val historyData = combine(
        entryDao.observeAll(),
        hitDao.observeGroupSummaries(),
        hitDao.observeLocated()
    ) { entries, summaries, located ->
        val entryById = entries.associateBy { it.id }

        // Stable color per seen network: oldest-watched first so colors do not shuffle as data arrives.
        val colorByEntry = summaries
            .map { it.entryId }
            .sortedBy { id -> entryById[id]?.createdAt ?: Long.MAX_VALUE }
            .withIndex()
            .associate { (index, id) -> id to watchlistNetworkColorHex(index) }

        val groups = summaries.mapNotNull { summary ->
            val entry = entryById[summary.entryId] ?: return@mapNotNull null
            WatchlistHistoryGroup(
                entry = entry,
                sightings = summary.sightings,
                bestRssi = summary.bestRssi,
                lastSeen = summary.lastSeen,
                colorHex = colorByEntry[summary.entryId] ?: WATCHLIST_NETWORK_COLORS.first(),
            )
        }

        val mapPoints = located.map { hit ->
            WatchlistMapPoint(
                id = hit.entryId.toString(),
                latitude = hit.latitude!!,
                longitude = hit.longitude!!,
                colorHex = colorByEntry[hit.entryId] ?: WATCHLIST_NETWORK_COLORS.first(),
            )
        }
        val legend = located.groupBy { it.entryId }
            .mapNotNull { (entryId, locatedHits) ->
                val entry = entryById[entryId] ?: return@mapNotNull null
                WatchlistMapLegendEntry(
                    name = entry.label ?: entry.ssid ?: entry.bssid ?: "",
                    count = locatedHits.size,
                    colorHex = colorByEntry[entryId] ?: WATCHLIST_NETWORK_COLORS.first(),
                )
            }
            .sortedByDescending { it.count }

        HistoryData(groups, mapPoints, legend, summaries.sumOf { it.sightings })
    }

    val uiState = combine(historyData, query, sort, mode) { data, q, s, m ->
        val filtered = if (q.isBlank()) data.groups else data.groups.filter { it.matches(q) }
        val sorted = when (s) {
            HistorySort.RECENT -> filtered.sortedByDescending { it.lastSeen }
            HistorySort.STRONGEST -> filtered.sortedByDescending { it.bestRssi }
            HistorySort.MOST_SEEN -> filtered.sortedByDescending { it.sightings }
        }
        WatchlistHistoryUiState(
            groups = sorted,
            mapPoints = data.mapPoints,
            legend = data.legend,
            totalSightings = data.totalSightings,
            networkCount = data.groups.size,
            query = q,
            sort = s,
            mode = m,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WatchlistHistoryUiState()
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(value: HistorySort) {
        sort.value = value
    }

    fun setMode(value: HistoryViewMode) {
        mode.value = value
    }

    fun clearHistory() {
        viewModelScope.launch { hitDao.clearAll() }
    }
}

/**
 * A history group: one watched network that has been seen, with its sighting count, strongest signal,
 * most recent sighting time, and the color it is drawn with on the map.
 */
data class WatchlistHistoryGroup(
    val entry: WatchlistEntryEntity,
    val sightings: Int,
    val bestRssi: Int,
    val lastSeen: Long,
    val colorHex: String,
) {
    /** True when the network is watched by SSID (matched by name), false when watched by BSSID only. */
    val matchedByName: Boolean get() = !entry.ssid.isNullOrBlank()

    /** Case-insensitive match of the search query against the label, SSID, and BSSID. */
    fun matches(queryText: String): Boolean {
        val needle = queryText.trim().lowercase()
        return listOfNotNull(entry.label, entry.ssid, entry.bssid)
            .any { it.lowercase().contains(needle) }
    }
}

/** A legend row on the history map: a network's name, located-sighting count, and color. */
data class WatchlistMapLegendEntry(
    val name: String,
    val count: Int,
    val colorHex: String,
)

/**
 * UI state for the Watchlist history screen.
 */
data class WatchlistHistoryUiState(
    val groups: List<WatchlistHistoryGroup> = emptyList(),
    val mapPoints: List<WatchlistMapPoint> = emptyList(),
    val legend: List<WatchlistMapLegendEntry> = emptyList(),
    val totalSightings: Int = 0,
    val networkCount: Int = 0,
    val query: String = "",
    val sort: HistorySort = HistorySort.RECENT,
    val mode: HistoryViewMode = HistoryViewMode.LIST,
)
