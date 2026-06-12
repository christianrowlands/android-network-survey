package com.craxiom.networksurvey.ui.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.wifi.components.ScanStatusStrip
import com.craxiom.networksurvey.ui.wifi.components.WifiGroupChildRow
import com.craxiom.networksurvey.ui.wifi.components.WifiGroupParentRow
import com.craxiom.networksurvey.ui.wifi.components.WifiModeTabs
import com.craxiom.networksurvey.ui.wifi.components.WifiRowA
import com.craxiom.networksurvey.ui.wifi.components.WifiSortSheet
import com.craxiom.networksurvey.ui.wifi.model.WifiDisplayItem
import com.craxiom.networksurvey.ui.wifi.model.WifiListViewModel

/**
 * Top-level Compose screen for the redesigned Wi-Fi list. Reads state from [WifiListViewModel]
 * and dispatches taps (mode change, sort, pause, expand/collapse, navigate) back to it or out
 * to the hosting Fragment via [onNetworkSelected].
 */
@Composable
fun WifiListScreen(
    viewModel: WifiListViewModel,
    onNetworkSelected: (WifiNetwork) -> Unit,
    onTogglePause: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var sortSheetOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScanStatusStrip(
            scanNumber = state.scanNumber,
            apCount = state.apCount,
            visibleApCount = state.visibleApCount,
            filterActive = state.filter.isActive,
            scanStatusId = state.scanStatusId,
            isPaused = state.updatesPaused,
            sortIndex = state.sortIndex,
            onSortClick = { sortSheetOpen = true },
            onTogglePause = onTogglePause,
        )
        WifiModeTabs(
            mode = state.mode,
            onModeChanged = { viewModel.setMode(it) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Centralised nav dispatcher. Looks up the raw wrapper on demand so the display model
        // can stay @Immutable / Compose-stable. Empty-BSSID guard is belt-and-suspenders — the
        // VM already drops empty-BSSID wrappers before they reach a row.
        val openDetails = { bssid: String ->
            if (bssid.isNotBlank()) {
                val wrapper = viewModel.wrapperFor(bssid)
                if (wrapper != null) {
                    onNetworkSelected(WifiNetwork.from(wrapper))
                }
            }
        }

        // Only blame the filter when there were networks to hide; a genuinely empty scan
        // (e.g. Wi-Fi off) falls through to the normal empty list.
        if (state.items.isEmpty() && state.filter.isActive && state.apCount > 0) {
            FilteredEmptyState(onClearFilter = { viewModel.clearFilter() })
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = state.items, key = { it.key }) { item ->
                    when (item) {
                        is WifiDisplayItem.Flat -> {
                            WifiRowA(
                                ap = item.ap,
                                onClick = { openDetails(item.ap.bssid) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        is WifiDisplayItem.GroupParent -> {
                            WifiGroupParentRow(
                                group = item,
                                onToggle = { viewModel.toggleGroupExpanded(item.groupKey) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        is WifiDisplayItem.GroupChild -> {
                            WifiGroupChildRow(
                                ap = item.ap,
                                onClick = { openDetails(item.ap.bssid) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    if (sortSheetOpen) {
        WifiSortSheet(
            selectedIndex = state.sortIndex,
            onSelected = { viewModel.setSortIndex(it) },
            onDismiss = { sortSheetOpen = false },
        )
    }
}

/**
 * Shown in place of the list when the display filter hides every network, with a one-tap way
 * out so a forgotten filter never reads as an empty scan.
 */
@Composable
private fun FilteredEmptyState(onClearFilter: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.wifi_filter_empty_state),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClearFilter) {
            Text(text = stringResource(R.string.wifi_filter_clear))
        }
    }
}
