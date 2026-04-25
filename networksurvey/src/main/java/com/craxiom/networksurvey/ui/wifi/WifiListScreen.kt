package com.craxiom.networksurvey.ui.wifi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ScanStatusStrip(
            scanNumber = state.scanNumber,
            apCount = state.apCount,
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

    if (sortSheetOpen) {
        WifiSortSheet(
            selectedIndex = state.sortIndex,
            onSelected = { viewModel.setSortIndex(it) },
            onDismiss = { sortSheetOpen = false },
        )
    }
}
