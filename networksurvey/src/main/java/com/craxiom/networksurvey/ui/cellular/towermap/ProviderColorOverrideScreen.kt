package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.util.PlmnColorMapper

/**
 * Screen for managing provider color overrides. Shows a list of all overrides with
 * add, edit, and delete capabilities.
 */
@Composable
fun ProviderColorOverrideScreen(
    viewModel: ProviderColorOverrideViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<ProviderColorEntry?>(null) }

    ProviderColorOverrideContent(
        overrides = uiState.overrides,
        onNavigateUp = onNavigateUp,
        onAddClick = { showAddDialog = true },
        onClearAllClick = { showClearAllDialog = true },
        onRemoveClick = { entry -> viewModel.removeOverride(entry.mcc, entry.mnc) },
        onEditClick = { entry -> editEntry = entry }
    )

    if (showAddDialog) {
        AddOverrideDialog(
            isAtMaxCapacity = uiState.isAtMaxCapacity,
            onDismiss = { showAddDialog = false },
            onAdd = { mcc, mnc, paletteIndex ->
                viewModel.setOverride(mcc, mnc, paletteIndex)
                showAddDialog = false
            }
        )
    }

    if (showClearAllDialog) {
        ClearAllOverridesDialog(
            onDismiss = { showClearAllDialog = false },
            onConfirm = {
                viewModel.clearAll()
                showClearAllDialog = false
            }
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
    overrides: List<ProviderColorEntry>,
    onNavigateUp: () -> Unit,
    onAddClick: () -> Unit,
    onClearAllClick: () -> Unit,
    onRemoveClick: (ProviderColorEntry) -> Unit,
    onEditClick: (ProviderColorEntry) -> Unit
) {
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
                    if (overrides.isNotEmpty()) {
                        IconButton(onClick = onClearAllClick) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear_all)
                            )
                        }
                    }
                    IconButton(onClick = onAddClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.provider_color_add_title)
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
            // Info card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.provider_color_overrides_info),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            if (overrides.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.provider_color_empty_state),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
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
                    items(overrides, key = { "${it.mcc}-${it.mnc}" }) { entry ->
                        OverrideItem(
                            entry = entry,
                            onEditClick = { onEditClick(entry) },
                            onRemoveClick = { onRemoveClick(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverrideItem(
    entry: ProviderColorEntry,
    onEditClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val overrideColor = PlmnColorMapper.getColorByIndex(entry.paletteIndex)
    val overrideName = PlmnColorMapper.PALETTE_NAMES[entry.paletteIndex]
    val defaultName = PlmnColorMapper.PALETTE_NAMES[entry.defaultPaletteIndex]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(overrideColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.mcc_mnc_format, entry.mcc, entry.mnc),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$overrideName (${
                            stringResource(
                                R.string.provider_color_default_label,
                                defaultName
                            )
                        })",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRemoveClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Dialog for adding a new provider color override with MCC/MNC input fields
 * and a color grid.
 */
@Composable
private fun AddOverrideDialog(
    isAtMaxCapacity: Boolean,
    onDismiss: () -> Unit,
    onAdd: (mcc: String, mnc: String, paletteIndex: Int) -> Unit
) {
    var mccText by remember { mutableStateOf("") }
    var mncText by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var mccError by remember { mutableStateOf<String?>(null) }
    var mncError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.provider_color_add_title)) },
        text = {
            if (isAtMaxCapacity) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = stringResource(R.string.provider_color_max_reached),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = mccText,
                            onValueChange = {
                                mccText = it.filter { c -> c.isDigit() }.take(3)
                                mccError = null
                            },
                            label = { Text(stringResource(R.string.provider_color_mcc_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = mccError != null,
                            supportingText = mccError?.let { { Text(it) } },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = mncText,
                            onValueChange = {
                                mncText = it.filter { c -> c.isDigit() }.take(3)
                                mncError = null
                            },
                            label = { Text(stringResource(R.string.provider_color_mnc_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = mncError != null,
                            supportingText = mncError?.let { { Text(it) } },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Color grid
                    Text(
                        text = stringResource(R.string.provider_color_select_color),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    for (row in 0 until 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (col in 0 until 4) {
                                val index = row * 4 + col
                                AddDialogColorCell(
                                    index = index,
                                    isSelected = index == selectedIndex,
                                    onClick = { selectedIndex = index }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val mccInt = mccText.toIntOrNull()
                    val mncInt = mncText.toIntOrNull()
                    var hasError = false

                    if (mccInt == null || mccInt !in 100..999) {
                        mccError = "100\u2013999"
                        hasError = true
                    }
                    if (mncInt == null || mncInt !in 0..999) {
                        mncError = "0\u2013999"
                        hasError = true
                    }
                    if (selectedIndex < 0) {
                        hasError = true
                    }

                    if (!hasError && mccInt != null && mncInt != null) {
                        onAdd(mccText, mncText, selectedIndex)
                    }
                },
                enabled = !isAtMaxCapacity && mccText.isNotBlank() && mncText.isNotBlank() && selectedIndex >= 0
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun RowScope.AddDialogColorCell(
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = PlmnColorMapper.getColorByIndex(index)
    val name = PlmnColorMapper.PALETTE_NAMES[index]

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (isSelected) {
                        Modifier.background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            CircleShape
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(4.dp)
                .background(color, CircleShape)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun ClearAllOverridesDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_clear_overrides_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.confirm_clear_overrides_message))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.clear_all))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
