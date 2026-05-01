package com.craxiom.networksurvey.ui.oui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R

/**
 * Standalone OUI Lookup tool: type a MAC address (or just the OUI prefix) and see the
 * matching manufacturer. Reachable from the nav drawer.
 *
 * The screen is a stateless renderer; all input formatting and lookup live in
 * [OuiLookupViewModel]. Per-state composables are split into [OuiLookupComponents] to keep this
 * file small and the per-phase routing easy to scan.
 */
@Composable
fun OuiLookupScreen(
    onOpenSettings: () -> Unit,
    viewModel: OuiLookupViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var fieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    // One-shot composition-entry sync. Two reachable scenarios:
    //   1. Process death: rememberSaveable restored fieldValue from the Bundle, but the
    //      HiltViewModel did not survive (no SavedStateHandle). Re-trigger the lookup so the
    //      screen does not get stuck showing typed text with the empty-state hint.
    //   2. Saver bundle dropped (rare, e.g. size limits) but the VM survived in the back stack:
    //      seed fieldValue from the persisted state.query so the field matches the visible phase.
    // Keyed on Unit so this only runs at composition entry; ongoing sync is intentionally avoided
    // to keep the screen as the source of truth for cursor/selection during typing.
    LaunchedEffect(Unit) {
        if (fieldValue.text.isNotEmpty() && state.query != fieldValue.text) {
            viewModel.onQueryChanged(fieldValue.text)
        } else if (fieldValue.text.isEmpty() && state.query.isNotEmpty()) {
            fieldValue = TextFieldValue(state.query, TextRange(state.query.length))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { newRaw ->
                val formatted = formatMacInput(newRaw)
                fieldValue = formatted
                viewModel.onQueryChanged(formatted.text)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.oui_lookup_search_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.oui_lookup_search_content_description),
                    tint = if (fieldValue.text.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            },
            trailingIcon = {
                if (fieldValue.text.isNotEmpty()) {
                    IconButton(onClick = {
                        fieldValue = TextFieldValue("")
                        viewModel.onClear()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.oui_lookup_clear_content_description),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() },
            ),
        )

        Spacer(Modifier.height(12.dp))

        when (val phase = state.phase) {
            OuiLookupUiState.Phase.Empty -> EmptyState()
            is OuiLookupUiState.Phase.Partial -> PartialCard(state.query, phase.remaining)
            OuiLookupUiState.Phase.Loading -> LoadingCard(state.query)
            is OuiLookupUiState.Phase.Resolved -> ResultCard(
                vendor = phase.vendor,
                query = state.query,
            )

            OuiLookupUiState.Phase.NotFound -> MessageCard(
                query = state.query,
                title = stringResource(R.string.oui_lookup_not_found_title),
                body = stringResource(R.string.oui_lookup_not_found_body),
            )

            OuiLookupUiState.Phase.Randomized -> MessageCard(
                query = state.query,
                title = stringResource(R.string.oui_lookup_randomized_title),
                body = stringResource(R.string.oui_lookup_randomized_body),
                pillLabel = stringResource(R.string.oui_lookup_randomized_pill),
            )

            OuiLookupUiState.Phase.PrivateVendor -> MessageCard(
                query = state.query,
                title = stringResource(R.string.oui_lookup_private_title),
                body = stringResource(R.string.oui_lookup_private_body),
            )

            OuiLookupUiState.Phase.SharedVendorBlock -> MessageCard(
                query = state.query,
                title = stringResource(R.string.oui_lookup_shared_block_title),
                body = stringResource(R.string.oui_lookup_shared_block_body),
            )

            OuiLookupUiState.Phase.Offline -> MessageCard(
                query = state.query,
                title = stringResource(R.string.oui_lookup_offline_title),
                body = stringResource(R.string.oui_lookup_offline_body),
                showMacDisplay = false,
            )

            OuiLookupUiState.Phase.DisabledByUser -> MessageCard(
                query = state.query,
                title = stringResource(R.string.oui_lookup_disabled_user_title),
                body = stringResource(R.string.oui_lookup_disabled_user_body),
                actionLabel = stringResource(R.string.oui_lookup_disabled_user_action),
                onAction = onOpenSettings,
                showMacDisplay = false,
            )

            OuiLookupUiState.Phase.DisabledByAdmin -> MessageCard(
                query = state.query,
                title = stringResource(R.string.oui_lookup_disabled_admin_title),
                body = stringResource(R.string.oui_lookup_disabled_admin_body),
                showMacDisplay = false,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = stringResource(R.string.oui_lookup_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.oui_lookup_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PartialCard(query: String, remaining: Int) {
    SurfaceCard {
        MacDisplay(query)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = pluralStringResource(
                    R.plurals.oui_lookup_partial_remaining,
                    remaining,
                    remaining,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingCard(query: String) {
    SurfaceCard {
        MacDisplay(query)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.oui_lookup_loading_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
