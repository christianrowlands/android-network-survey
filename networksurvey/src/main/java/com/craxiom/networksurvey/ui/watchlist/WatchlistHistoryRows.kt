package com.craxiom.networksurvey.ui.watchlist

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.util.toWifiSignalCategory

/**
 * Row composables for the Watchlist history screen: a per-network group card, the sort chip, and the
 * map legend. Extracted to keep [WatchlistHistoryScreen] focused.
 */

/**
 * A history group card: the watched network's name, a "N sightings - matched by X - when" subtitle,
 * its strongest signal, and a chevron. Tapping opens the sighting-details screen for that network.
 */
@Composable
internal fun WatchlistHistoryGroupRow(
    group: WatchlistHistoryGroup,
    onClick: () -> Unit
) {
    val matchedBy = if (group.matchedByName) {
        stringResource(R.string.watchlist_matched_by_name_short)
    } else {
        stringResource(R.string.watchlist_matched_by_ap_short)
    }
    val relative = DateUtils.getRelativeTimeSpanString(
        group.lastSeen,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
    val subtitle = stringResource(
        R.string.watchlist_history_group_subtitle,
        stringResource(R.string.watchlist_history_group_sightings, group.sightings),
        matchedBy,
        relative
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.entry.label ?: group.entry.ssid ?: group.entry.bssid ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = WifiTokens.SsidAccent
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = group.bestRssi.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = group.bestRssi.toWifiSignalCategory().color
                )
                Text(
                    text = stringResource(R.string.watchlist_history_best_dbm),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.watchlist_history_open_detail),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A compact chip that shows the current sort and opens a menu to change it. */
@Composable
internal fun HistorySortChip(
    sort: HistorySort,
    onSortSelected: (HistorySort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val labelRes = when (sort) {
        HistorySort.RECENT -> R.string.watchlist_history_sort_recent
        HistorySort.STRONGEST -> R.string.watchlist_history_sort_strongest
        HistorySort.MOST_SEEN -> R.string.watchlist_history_sort_most_seen
    }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(WifiTokens.SsidSoft)
                .clickable(
                    onClickLabel = stringResource(R.string.watchlist_history_sort_action),
                    role = Role.Button,
                    onClick = { expanded = true }
                )
                .padding(start = 13.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = WifiTokens.SsidAccent
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = WifiTokens.SsidAccent,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                HistorySort.RECENT to R.string.watchlist_history_sort_recent,
                HistorySort.STRONGEST to R.string.watchlist_history_sort_strongest,
                HistorySort.MOST_SEEN to R.string.watchlist_history_sort_most_seen,
            ).forEach { (value, res) ->
                DropdownMenuItem(
                    text = { Text(stringResource(res)) },
                    onClick = {
                        expanded = false
                        onSortSelected(value)
                    }
                )
            }
        }
    }
}

/** A single legend row on the history map: a color dot, the network name, and its sighting count. */
@Composable
internal fun WatchlistMapLegendRow(entry: WatchlistMapLegendEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(runCatching { Color(android.graphics.Color.parseColor(entry.colorHex)) }
                    .getOrDefault(WifiTokens.SsidAccent))
        )
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.watchlist_history_group_sightings, entry.count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
