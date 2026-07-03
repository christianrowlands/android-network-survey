package com.craxiom.networksurvey.ui.watchlist

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsSegmentedToggle
import com.craxiom.networksurvey.ui.common.SegmentedOption
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog

/** Visible height of the map sheet's Peek detent (drag handle + summary line), before nav-bar inset. */
private val MAP_SHEET_PEEK_CONTENT = 96.dp

/**
 * The Watchlist history screen: sightings grouped by watched network, with full-text search, a sort
 * control, and a List/Map view toggle. Tapping a network opens its sighting details. The map view
 * plots every located sighting colored per network, with a draggable network-key sheet: tapping a pin
 * highlights its network's row, tapping a row highlights the network's pins, and the selected row's
 * "View sightings" action opens that network's details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistHistoryScreen(
    viewModel: WatchlistHistoryViewModel,
    onNavigateUp: () -> Unit,
    onOpenDetail: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watchlist_history_action)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.watchlist_navigate_back)
                        )
                    }
                },
                actions = {
                    NsSegmentedToggle(
                        options = listOf(
                            SegmentedOption(
                                stringResource(R.string.watchlist_history_view_list),
                                Icons.AutoMirrored.Filled.List,
                                stringResource(R.string.watchlist_map_view_list)
                            ),
                            SegmentedOption(
                                stringResource(R.string.watchlist_history_view_map),
                                Icons.Default.Place,
                                stringResource(R.string.watchlist_map_view_map)
                            ),
                        ),
                        selectedIndex = if (uiState.mode == HistoryViewMode.MAP) 1 else 0,
                        onSelected = {
                            viewModel.setMode(if (it == 1) HistoryViewMode.MAP else HistoryViewMode.LIST)
                        },
                        fillWidth = false,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    if (uiState.totalSightings > 0) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.watchlist_history_clear)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        // The map tab skips the Scaffold's bottom inset so its sheet sits flush against the screen
        // bottom and owns the nav-bar inset itself; the list keeps the standard padding.
        val bottomPadding = if (uiState.mode == HistoryViewMode.MAP) {
            0.dp
        } else {
            paddingValues.calculateBottomPadding()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                    bottom = bottomPadding
                )
        ) {
            when (uiState.mode) {
                HistoryViewMode.LIST -> HistoryListContent(uiState, viewModel, onOpenDetail)
                HistoryViewMode.MAP -> HistoryMapContent(uiState, viewModel, onOpenDetail)
            }
        }
    }

    if (showClearDialog) {
        NsConfirmationDialog(
            title = stringResource(R.string.watchlist_history_clear_title),
            message = stringResource(R.string.watchlist_history_clear_message),
            confirmText = stringResource(R.string.watchlist_history_clear),
            onConfirm = { viewModel.clearHistory() },
            onDismiss = { showClearDialog = false },
            destructive = true
        )
    }
}

@Composable
private fun HistoryListContent(
    uiState: WatchlistHistoryUiState,
    viewModel: WatchlistHistoryViewModel,
    onOpenDetail: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text(stringResource(R.string.watchlist_history_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistorySortChip(sort = uiState.sort, onSortSelected = viewModel::setSort)
            Text(
                text = stringResource(
                    R.string.watchlist_history_count_summary,
                    uiState.totalSightings,
                    uiState.networkCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
        }
        if (uiState.groups.isEmpty()) {
            val message = if (uiState.query.isNotBlank()) {
                stringResource(R.string.watchlist_history_no_matches)
            } else {
                stringResource(R.string.watchlist_history_empty)
            }
            EmptyMessage(message)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                items(items = uiState.groups, key = { it.entry.id }) { group ->
                    WatchlistHistoryGroupRow(
                        group = group,
                        onClick = { onOpenDetail(group.entry.id) })
                }
            }
        }
    }
}

@Composable
private fun HistoryMapContent(
    uiState: WatchlistHistoryUiState,
    viewModel: WatchlistHistoryViewModel,
    onOpenDetail: (Long) -> Unit
) {
    if (uiState.mapPoints.isEmpty()) {
        EmptyMessage(stringResource(R.string.watchlist_history_map_no_locations))
        return
    }
    val listState = rememberLazyListState()
    val sheetState = remember { AnchoredDraggableState(initialValue = SheetDetent.Peek) }

    // When a pin (or row) selects a network, open the sheet enough to reveal the list, then scroll to
    // its row. Rows are ordered exactly like uiState.legend, so the index maps one-to-one.
    LaunchedEffect(uiState.selectedEntryId) {
        val id = uiState.selectedEntryId ?: return@LaunchedEffect
        if (sheetState.currentValue == SheetDetent.Peek) {
            sheetState.animateTo(SheetDetent.Half)
        }
        val index = uiState.legend.indexOfFirst { it.entryId == id }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val metrics = rememberMapSheetMetrics(
            state = sheetState,
            availableHeight = maxHeight,
            peekVisibleHeight = MAP_SHEET_PEEK_CONTENT + navBarHeight,
        )

        WatchlistMap(
            points = uiState.mapPoints,
            selectedId = uiState.selectedEntryId?.toString(),
            onPointClick = { id -> viewModel.selectEntry(id.toLongOrNull()) },
            modifier = Modifier.fillMaxSize(),
            bottomInset = metrics.mapBottomInset,
            attributionEnabled = true
        )

        WatchlistHistoryMapSheet(
            state = sheetState,
            sheetHeight = metrics.sheetHeight,
            legend = uiState.legend,
            selectedEntryId = uiState.selectedEntryId,
            listState = listState,
            // Tapping the selected row again clears the focus (the map has no empty-tap deselect).
            onSelect = { id ->
                viewModel.selectEntry(if (uiState.selectedEntryId == id) null else id)
            },
            onOpenDetail = onOpenDetail,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}
