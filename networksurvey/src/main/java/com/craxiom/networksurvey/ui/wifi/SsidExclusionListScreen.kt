package com.craxiom.networksurvey.ui.wifi

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsInfoCard
import com.craxiom.networksurvey.ui.common.NsSectionLabel
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog

/**
 * Screen for managing the SSID exclusion list: a capacity hero card, an info card, and the list of
 * excluded SSIDs. Adding is done through a bottom sheet, and removal (single or clear all) is
 * confirmed before it is applied.
 */
@Composable
fun SsidExclusionListScreen(
    viewModel: SsidExclusionListViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-read the list when returning to this screen so exclusions toggled from the Wi-Fi Details
    // screen while this screen was on the back stack are reflected.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    SsidExclusionListContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onAddClick = { showAddSheet = true },
        onClearAllClick = { showClearAllConfirm = true },
        onRemoveClick = { ssid -> pendingDelete = ssid }
    )

    if (showAddSheet) {
        SsidExclusionAddSheet(
            onAdd = { ssid -> viewModel.addSsid(ssid) },
            onDismiss = { showAddSheet = false }
        )
    }

    pendingDelete?.let { ssid ->
        NsConfirmationDialog(
            title = stringResource(R.string.ssid_exclusion_remove_title),
            message = stringResource(R.string.ssid_exclusion_remove_message, ssid),
            confirmText = stringResource(R.string.remove),
            onConfirm = { viewModel.removeSsid(ssid) },
            onDismiss = { pendingDelete = null },
            destructive = true
        )
    }

    if (showClearAllConfirm) {
        NsConfirmationDialog(
            title = stringResource(R.string.confirm_clear_exclusion_list_title),
            message = stringResource(R.string.confirm_clear_exclusion_list_message),
            confirmText = stringResource(R.string.clear_all),
            onConfirm = { viewModel.clearAll() },
            onDismiss = { showClearAllConfirm = false },
            destructive = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SsidExclusionListContent(
    uiState: SsidExclusionListUiState,
    onNavigateUp: () -> Unit,
    onAddClick: () -> Unit,
    onClearAllClick: () -> Unit,
    onRemoveClick: (String) -> Unit
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ssid_exclusion_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                actions = {
                    // At capacity the add action is disabled; the error-tinted hero card explains why.
                    IconButton(onClick = onAddClick, enabled = !uiState.isAtMaxCapacity) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_ssid_manually)
                        )
                    }
                    if (uiState.excludedSsids.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more_options)
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.clear_all)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onClearAllClick()
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
            SsidExclusionHero(count = uiState.excludedSsids.size, max = uiState.maxCapacity)

            NsInfoCard(text = stringResource(R.string.wifi_exclusion_info))

            if (uiState.excludedSsids.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ssid_exclusion_list_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onAddClick) {
                            Text(stringResource(R.string.add_ssid_manually))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // The header key is an Int on purpose: the item keys below are arbitrary
                    // user-controlled SSID strings, and an Int can never collide with them.
                    item(key = 0) {
                        NsSectionLabel(
                            text = stringResource(
                                R.string.ssid_exclusion_section_count,
                                uiState.excludedSsids.size
                            ),
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }
                    items(items = uiState.excludedSsids, key = { it }) { ssid ->
                        SsidExclusionRow(
                            ssid = ssid,
                            onDelete = { onRemoveClick(ssid) }
                        )
                    }
                }
            }
        }
    }
}
