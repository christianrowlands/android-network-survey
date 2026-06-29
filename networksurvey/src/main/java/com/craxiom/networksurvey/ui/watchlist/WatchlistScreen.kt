package com.craxiom.networksurvey.ui.watchlist

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog

/**
 * The Watchlist management screen: a header status, an info card, and a list of watched networks
 * with a per-row enable switch and delete action. Adding is done through a custom dialog; the app
 * bar links to the history log.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-read the notifications/MDM status when returning to the screen so a permission change made
    // in system settings is reflected.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshStatus() }

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WatchlistEntryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watchlist_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.watchlist_navigate_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.watchlist_history_action)
                        )
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.watchlist_add)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            WatchlistStatusHeader(
                enabled = uiState.watchlistEnabled,
                notificationsEnabled = uiState.notificationsEnabled,
                mdmControlled = uiState.mdmControlled,
                onToggle = { viewModel.setWatchlistEnabled(it) }
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.watchlist_info),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            if (uiState.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.watchlist_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showAddDialog = true }) {
                            Text(stringResource(R.string.watchlist_add_network))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = uiState.items, key = { it.entry.id }) { item ->
                        WatchlistRow(
                            item = item,
                            onToggleEnabled = { enabled ->
                                viewModel.setEntryEnabled(
                                    item.entry,
                                    enabled
                                )
                            },
                            onDelete = { pendingDelete = item.entry }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        WatchlistAddDialog(
            onAdd = { label, ssid, bssid -> viewModel.addEntry(label, ssid, bssid) },
            onDismiss = { showAddDialog = false }
        )
    }

    pendingDelete?.let { entry ->
        NsConfirmationDialog(
            title = stringResource(R.string.watchlist_delete_title),
            message = stringResource(R.string.watchlist_delete_message, entry.label ?: ""),
            confirmText = stringResource(R.string.watchlist_remove),
            onConfirm = { viewModel.deleteEntry(entry) },
            onDismiss = { pendingDelete = null },
            destructive = true
        )
    }
}

@Composable
private fun WatchlistStatusHeader(
    enabled: Boolean,
    notificationsEnabled: Boolean,
    mdmControlled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val (title, container) = when {
        !enabled -> stringResource(R.string.watchlist_status_disabled) to MaterialTheme.colorScheme.surfaceVariant
        !notificationsEnabled -> stringResource(R.string.watchlist_status_notifications_blocked) to MaterialTheme.colorScheme.errorContainer
        else -> stringResource(R.string.watchlist_status_active) to MaterialTheme.colorScheme.primaryContainer
    }
    // When enabled, the feature only fires during a survey, so the subtitle keeps that honest.
    // Under MDM control, the subtitle explains the toggle cannot be changed.
    val subtitle = when {
        mdmControlled -> stringResource(R.string.watchlist_status_mdm)
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
            Switch(checked = enabled, onCheckedChange = onToggle, enabled = !mdmControlled)
        }
    }
}

@Composable
private fun WatchlistRow(
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
