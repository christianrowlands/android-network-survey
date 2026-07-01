package com.craxiom.networksurvey.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.MapButton
import com.craxiom.networksurvey.ui.cellular.TopAppBarOverlay
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.theme.WifiTokens

/** SavedStateHandle key for handing the watched-network id to the sighting-details screen. */
const val WATCHLIST_ENTRY_ID_KEY = "watchlist_entry_id"

/** Visible height of the sheet's Peek detent (drag handle + summary header), before nav-bar inset. */
private val SHEET_PEEK_CONTENT = 116.dp

/**
 * The sighting-details screen for a single watched network: a full-screen map of every located
 * sighting with a draggable bottom sheet of the sightings on top. The sheet snaps between a peek
 * (its summary header only), a middle (~33%), and an open (~64%) stop; dragging its handle resizes it
 * while the list scrolls on its own. Tapping a list row selects (and recenters) its map pin; tapping a
 * pin selects, opens the sheet to the middle stop, and scrolls to its list row. The trash action clears
 * this network's history (the watched entry is kept), after which the screen pops back.
 */
@Composable
fun WatchlistHistoryDetailScreen(
    viewModel: WatchlistHistoryDetailViewModel,
    entryId: Long,
    onNavigateUp: () -> Unit
) {
    LaunchedEffect(entryId) { viewModel.start(entryId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val sheetState = remember { AnchoredDraggableState(initialValue = SheetDetent.Peek) }

    // Once this network's history is emptied (e.g. cleared here), there is nothing to show: go back.
    LaunchedEffect(uiState.loaded, uiState.sightings.isEmpty()) {
        if (uiState.loaded && uiState.sightings.isEmpty()) onNavigateUp()
    }

    // When a pin selects a sighting, open the sheet enough to reveal the list, then scroll to the row.
    LaunchedEffect(uiState.selectedHitId) {
        val id = uiState.selectedHitId ?: return@LaunchedEffect
        if (sheetState.currentValue == SheetDetent.Peek) {
            sheetState.animateTo(SheetDetent.Half)
        }
        val index = uiState.sightings.indexOfFirst { it.id == id }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    val matchedBy = if (uiState.matchedByName) {
        stringResource(R.string.watchlist_matched_by_name_short)
    } else {
        stringResource(R.string.watchlist_matched_by_ap_short)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val density = LocalDensity.current
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // Visible height at each detent; Full is also the sheet's total height.
        val peekVisible = SHEET_PEEK_CONTENT + navBarHeight
        val halfVisible = maxHeight * 0.33f
        val fullVisible = maxHeight * 0.64f

        val fullPx = with(density) { fullVisible.toPx() }
        val anchors = remember(peekVisible, halfVisible, fullVisible, density) {
            with(density) {
                DraggableAnchors {
                    SheetDetent.Full at 0f
                    SheetDetent.Half at (fullPx - halfVisible.toPx())
                    SheetDetent.Peek at (fullPx - peekVisible.toPx())
                }
            }
        }
        // Set anchors during composition (not a post-composition effect) so the sheet's first frame is
        // already positioned at Peek instead of flashing fully-open before snapping. updateAnchors is
        // idempotent and only re-keys when the measured anchors change (rotation / window resize).
        remember(anchors) { sheetState.updateAnchors(anchors); anchors }

        val mapBottomInset = when (sheetState.targetValue) {
            SheetDetent.Peek -> peekVisible
            SheetDetent.Half -> halfVisible
            SheetDetent.Full -> fullVisible
        }

        if (uiState.mapPoints.isEmpty()) {
            Text(
                text = stringResource(R.string.watchlist_history_map_no_locations),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        } else {
            WatchlistMap(
                points = uiState.mapPoints,
                selectedId = uiState.selectedHitId?.toString(),
                onPointClick = { id -> viewModel.selectHit(id.toLongOrNull()) },
                modifier = Modifier.fillMaxSize(),
                topInset = statusBarHeight + 56.dp,
                bottomInset = mapBottomInset,
                attributionEnabled = true
            )
        }

        // Scrim behind the floating controls so the title stays legible over the map.
        TopAppBarOverlay(statusBarHeight + 56.dp)

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = statusBarHeight + 4.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MapButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.watchlist_navigate_back),
                onClick = onNavigateUp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = WifiTokens.SsidAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.watchlist_detail_subtitle,
                        uiState.sightingCount,
                        matchedBy,
                        uiState.sightingCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MapButton(
                icon = Icons.Default.Delete,
                contentDescription = stringResource(R.string.watchlist_history_clear),
                onClick = { showClearDialog = true },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        }

        WatchlistDetailSheet(
            state = sheetState,
            sheetHeight = fullVisible,
            sightings = uiState.sightings,
            selectedId = uiState.selectedHitId,
            sort = uiState.sort,
            listState = listState,
            onSelect = { viewModel.selectHit(it) },
            onSortSelected = { viewModel.setSort(it) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showClearDialog) {
        NsConfirmationDialog(
            title = stringResource(R.string.watchlist_detail_clear_title),
            message = stringResource(R.string.watchlist_detail_clear_message, uiState.title),
            confirmText = stringResource(R.string.watchlist_history_clear),
            onConfirm = { viewModel.clearHistory() },
            onDismiss = { showClearDialog = false },
            destructive = true
        )
    }
}
