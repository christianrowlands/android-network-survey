package com.craxiom.networksurvey.ui.watchlist

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsHeroStatusCard
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.ui.wifi.components.SignalMeterWide
import com.craxiom.networksurvey.util.toWifiSignalCategory

/**
 * Row composables for the Watchlist management screen, extracted to keep [WatchlistScreen] focused:
 * the alerts status card (feature on/off, notifications state) and a signal-rich watched-network row
 * with its per-entry enable switch, an overflow menu, and the latest sighting's signal.
 */

/**
 * The "Alerts on" card: a calm surface card with a leading bell badge, a state-driven title/subtitle,
 * and the feature toggle. Preserves the three states from the original header: feature disabled,
 * notifications blocked (error-tinted so it stands out), and active.
 */
@Composable
internal fun WatchlistStatusHeader(
    enabled: Boolean,
    notificationsEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val blocked = enabled && !notificationsEnabled
    val title = when {
        !enabled -> stringResource(R.string.watchlist_status_disabled)
        !notificationsEnabled -> stringResource(R.string.watchlist_status_notifications_blocked)
        else -> stringResource(R.string.watchlist_status_active)
    }

    NsHeroStatusCard(
        title = title,
        subtitle = stringResource(R.string.watchlist_alerts_subtitle),
        icon = rememberVectorPainter(Icons.Default.Notifications),
        isError = blocked,
        trailing = { Switch(checked = enabled, onCheckedChange = onToggle) }
    )
}

/**
 * A single watched-network row: the display name (in the SSID accent), a subtitle of
 * SSID / seen-count / relative time, the latest sighting's signal (dBm + meter), an enable switch,
 * and an overflow menu whose only action is Remove. The signal is muted when the network has not
 * been seen yet.
 */
@Composable
internal fun WatchlistRow(
    item: WatchlistRowItem,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val entry = item.entry
    val ssid = entry.ssid?.takeIf { it.isNotBlank() }
    val bssid = entry.bssid?.takeIf { it.isNotBlank() }
    val name = entry.label?.takeIf { it.isNotBlank() } ?: ssid ?: bssid ?: ""
    val subtitle = buildList {
        when {
            ssid != null -> add(stringResource(R.string.watchlist_row_ssid_quoted, ssid))
            bssid != null -> add(bssid)
        }
        if (item.sightingsCount > 0 && item.lastSeen != null) {
            add(stringResource(R.string.watchlist_row_seen_times, item.sightingsCount))
            add(
                DateUtils.getRelativeTimeSpanString(
                    item.lastSeen,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()
            )
        } else {
            add(stringResource(R.string.watchlist_row_not_seen))
        }
    }.joinToString("  ·  ")

    val enableForDescription = stringResource(R.string.watchlist_enable_for, entry.label ?: "")
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = WifiTokens.SsidAccent
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = entry.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.semantics { contentDescription = enableForDescription }
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.watchlist_more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.watchlist_remove),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            WatchlistSignalRow(rssi = item.lastRssi)
        }
    }
}

/**
 * The signal line of a watched-network row: a monospace dBm value colored by signal bucket plus a
 * flexible meter. Shown muted (a dash, empty meter) when [rssi] is null. The trailing inset keeps the
 * meter from running to the card edge (the row column hugs the overflow icon with a small end pad).
 */
@Composable
private fun WatchlistSignalRow(rssi: Int?) {
    Row(
        modifier = Modifier.padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color =
            rssi?.toWifiSignalCategory()?.color ?: MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            text = rssi?.let { stringResource(R.string.watchlist_signal_dbm, it) }
                ?: stringResource(R.string.watchlist_signal_unknown),
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(end = 12.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            SignalMeterWide(rssi = rssi)
        }
    }
}
