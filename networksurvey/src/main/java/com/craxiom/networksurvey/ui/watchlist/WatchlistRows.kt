package com.craxiom.networksurvey.ui.watchlist

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R

/**
 * Row composables for the Watchlist management screen, extracted to keep [WatchlistScreen] focused: the
 * status header (feature on/off, notifications, MDM) and a single watched-network row with its
 * per-entry enable switch and delete action.
 */
@Composable
internal fun WatchlistStatusHeader(
    enabled: Boolean,
    notificationsEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val (title, container) = when {
        !enabled -> stringResource(R.string.watchlist_status_disabled) to MaterialTheme.colorScheme.surfaceVariant
        !notificationsEnabled -> stringResource(R.string.watchlist_status_notifications_blocked) to MaterialTheme.colorScheme.errorContainer
        else -> stringResource(R.string.watchlist_status_active) to MaterialTheme.colorScheme.primaryContainer
    }
    // When enabled, the feature only fires during a survey, so the subtitle keeps that honest.
    val subtitle = when {
        enabled && notificationsEnabled -> stringResource(R.string.watchlist_status_active_subtitle)
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
internal fun WatchlistRow(
    item: WatchlistRowItem,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val entry = item.entry
    val identifier = listOfNotNull(
        entry.ssid?.takeIf { it.isNotBlank() },
        entry.bssid?.takeIf { it.isNotBlank() }
    ).joinToString("  -  ")

    val seenText = item.lastSeen?.let {
        stringResource(
            R.string.watchlist_seen_relative,
            DateUtils.getRelativeTimeSpanString(
                it,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            ).toString()
        )
    } ?: stringResource(R.string.watchlist_never_seen)

    val enableForDescription = stringResource(R.string.watchlist_enable_for, entry.label ?: "")

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label ?: identifier,
                    style = MaterialTheme.typography.titleMedium
                )
                if (identifier.isNotBlank()) {
                    Text(
                        text = identifier,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = seenText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = entry.enabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.semantics { contentDescription = enableForDescription }
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.watchlist_remove),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
