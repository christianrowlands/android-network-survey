package com.craxiom.networksurvey.ui.cellular.towermap

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
 * Screen for managing provider color overrides: a capacity hero card, an info card, and the list
 * of overrides. Adding is done through a bottom sheet, tapping a row opens the color picker to
 * edit, and removal (single or clear all) is confirmed before it is applied.
 */
@Composable
fun ProviderColorOverrideScreen(
    viewModel: ProviderColorOverrideViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-read the overrides when returning to this screen so changes made from the tower details
    // color picker while this screen was on the back stack are reflected.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProviderColorEntry?>(null) }
    var editEntry by remember { mutableStateOf<ProviderColorEntry?>(null) }

    ProviderColorOverrideContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onAddClick = { showAddSheet = true },
        onClearAllClick = { showClearAllConfirm = true },
        onRemoveClick = { entry -> pendingDelete = entry },
        onEditClick = { entry -> editEntry = entry }
    )

    if (showAddSheet) {
        ProviderColorAddSheet(
            onAdd = { mcc, mnc, paletteIndex -> viewModel.setOverride(mcc, mnc, paletteIndex) },
            onDismiss = { showAddSheet = false }
        )
    }

    pendingDelete?.let { entry ->
        NsConfirmationDialog(
            title = stringResource(R.string.provider_color_remove_title),
            message = stringResource(R.string.provider_color_remove_message, entry.mcc, entry.mnc),
            confirmText = stringResource(R.string.remove),
            onConfirm = { viewModel.removeOverride(entry.mcc, entry.mnc) },
            onDismiss = { pendingDelete = null },
            destructive = true
        )
    }

    if (showClearAllConfirm) {
        NsConfirmationDialog(
            title = stringResource(R.string.confirm_clear_overrides_title),
            message = stringResource(R.string.confirm_clear_overrides_message),
            confirmText = stringResource(R.string.clear_all),
            onConfirm = { viewModel.clearAll() },
            onDismiss = { showClearAllConfirm = false },
            destructive = true
        )
    }

    editEntry?.let { entry ->
        ProviderColorPickerDialog(
            mcc = entry.mcc,
            mnc = entry.mnc,
            currentPaletteIndex = entry.paletteIndex,
            hasOverride = true,
            onColorSelected = { index ->
                viewModel.setOverride(entry.mcc, entry.mnc, index)
                editEntry = null
            },
            onResetToDefault = {
                viewModel.removeOverride(entry.mcc, entry.mnc)
                editEntry = null
            },
            onDismiss = { editEntry = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderColorOverrideContent(
    uiState: ProviderColorOverrideUiState,
    onNavigateUp: () -> Unit,
    onAddClick: () -> Unit,
    onClearAllClick: () -> Unit,
    onRemoveClick: (ProviderColorEntry) -> Unit,
    onEditClick: (ProviderColorEntry) -> Unit
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.provider_color_overrides_title)) },
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
                            contentDescription = stringResource(R.string.provider_color_add_title)
                        )
                    }
                    if (uiState.overrides.isNotEmpty()) {
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
            ProviderColorHero(count = uiState.overrides.size, max = uiState.maxCapacity)

            NsInfoCard(text = stringResource(R.string.provider_color_overrides_info))

            if (uiState.overrides.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.provider_color_empty_state),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onAddClick) {
                            Text(stringResource(R.string.provider_color_add_title))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Int key so the header can never collide with the String item keys below.
                    item(key = 0) {
                        NsSectionLabel(
                            text = stringResource(
                                R.string.provider_color_section_count,
                                uiState.overrides.size
                            ),
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }
                    items(uiState.overrides, key = { "${it.mcc}-${it.mnc}" }) { entry ->
                        ProviderColorOverrideRow(
                            entry = entry,
                            onClick = { onEditClick(entry) },
                            onDelete = { onRemoveClick(entry) }
                        )
                    }
                }
            }
        }
    }
}
