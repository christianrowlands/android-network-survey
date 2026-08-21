package com.craxiom.networksurvey.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import com.craxiom.networksurvey.ui.common.NsInfoCard
import com.craxiom.networksurvey.ui.common.NsSectionLabel
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog

/**
 * The Watchlist management screen: a header status, an info card, and a list of watched networks
 * with a per-row enable switch and delete action. Adding is done through a custom dialog; the app
 * bar links to the history log and offers a "Clear all" action. When opened from an import deep link,
 * a confirmation prompt previews the networks to add.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-read the notifications/MDM status when returning to the screen so a permission change made in
    // system settings is reflected. (A pending import deep link is consumed in the ViewModel's init.)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStatus()
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
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
                            painter = painterResource(id = R.drawable.ic_history),
                            contentDescription = stringResource(R.string.watchlist_history_action)
                        )
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.watchlist_add)
                        )
                    }
                    if (uiState.items.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.watchlist_more_options)
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.watchlist_clear_all)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showClearAllConfirm = true
                                    }
                                )
                            }
                        }
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
                onToggle = { viewModel.setWatchlistEnabled(it) }
            )

            NsInfoCard(text = stringResource(R.string.watchlist_info))

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
                    item(key = "watched-header") {
                        NsSectionLabel(
                            text = stringResource(
                                R.string.watchlist_watched_count,
                                uiState.items.size
                            ),
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }
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
        WatchlistAddSheet(
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

    if (showClearAllConfirm) {
        NsConfirmationDialog(
            title = stringResource(R.string.watchlist_clear_all_title),
            message = stringResource(R.string.watchlist_clear_all_message),
            confirmText = stringResource(R.string.watchlist_clear_all),
            onConfirm = { viewModel.clearAll() },
            onDismiss = { showClearAllConfirm = false },
            destructive = true
        )
    }

    uiState.pendingImport?.let { pending ->
        WatchlistImportDialog(
            pending = pending,
            onConfirm = { viewModel.confirmPendingImport() },
            onDismiss = { viewModel.dismissPendingImport() }
        )
    }
}
