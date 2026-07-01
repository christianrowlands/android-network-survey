package com.craxiom.networksurvey.ui.watchlist

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.ui.wifi.components.SignalMeter
import com.craxiom.networksurvey.util.toWifiSignalCategory

/**
 * Row and header composables for the Watchlist sighting-details bottom sheet: the peek header
 * (count + sort chip), the sort chip itself, and a single sighting row. Extracted to keep
 * [WatchlistHistoryDetailScreen] focused on layout.
 */

/**
 * The sheet's peek header: the sighting count on the left and the sort chip on the right. This is the
 * only content visible while the sheet is collapsed.
 */
@Composable
internal fun SightingSheetHeader(
    sightingCount: Int,
    sort: DetailSort,
    onSortSelected: (DetailSort) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.watchlist_sightings_count,
                sightingCount,
                sightingCount
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        DetailSortChip(sort = sort, onSortSelected = onSortSelected)
    }
}

/** A compact chip that shows the current sighting sort and opens a menu to change it. */
@Composable
internal fun DetailSortChip(
    sort: DetailSort,
    onSortSelected: (DetailSort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val labelRes = when (sort) {
        DetailSort.STRONGEST -> R.string.watchlist_detail_sort_strongest
        DetailSort.NEWEST -> R.string.watchlist_detail_sort_newest
    }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(WifiTokens.SsidSoft)
                .clickable(
                    onClickLabel = stringResource(R.string.watchlist_detail_sort_action),
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
                DetailSort.STRONGEST to R.string.watchlist_detail_sort_strongest,
                DetailSort.NEWEST to R.string.watchlist_detail_sort_newest,
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

/**
 * A single sighting row: signal (dBm + meter), timestamp, and a location/BSSID line. The selected row
 * gets a soft accent background plus a leading accent bar so it reads as selected alongside its map pin.
 */
@Composable
internal fun SightingRow(
    hit: WatchlistHitEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val whenText = DateUtils.formatDateTime(
        context,
        hit.timestamp,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME
    )
    val background = if (selected) WifiTokens.SsidSoft else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(background)
            .height(IntrinsicSize.Min)
    ) {
        // Leading accent bar mirrors the selected map pin; transparent (but present) when unselected so
        // the row content stays aligned regardless of selection.
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (selected) WifiTokens.SsidAccent else Color.Transparent)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.watchlist_signal_dbm, hit.rssi),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.SemiBold,
                    color = hit.rssi.toWifiSignalCategory().color
                )
                SignalMeter(rssi = hit.rssi, width = 80.dp)
                Text(
                    text = whenText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
            val latitude = hit.latitude
            val longitude = hit.longitude
            val locationText = if (latitude != null && longitude != null) {
                stringResource(
                    R.string.watchlist_detail_location_line,
                    stringResource(
                        R.string.watchlist_detail_latlng,
                        "%.5f".format(latitude),
                        "%.5f".format(longitude)
                    ),
                    hit.bssid ?: ""
                )
            } else {
                hit.bssid ?: ""
            }
            Text(
                text = locationText,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
