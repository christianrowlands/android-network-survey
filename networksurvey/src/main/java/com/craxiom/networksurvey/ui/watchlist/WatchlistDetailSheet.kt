package com.craxiom.networksurvey.ui.watchlist

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity

/**
 * The draggable sightings sheet for the sighting-details screen, built on the shared [MapSheetSurface]
 * shell. Its header shows the sighting count and sort chip; the sighting list scrolls independently.
 * Anchors are owned by the caller (which knows the screen height) via [rememberMapSheetMetrics].
 *
 * @param sheetHeight the full (most-open) height of the sheet; equals the Full detent's visible height.
 */
@Composable
fun WatchlistDetailSheet(
    state: AnchoredDraggableState<SheetDetent>,
    sheetHeight: Dp,
    sightings: List<WatchlistHitEntity>,
    selectedId: Long?,
    sort: DetailSort,
    listState: LazyListState,
    onSelect: (Long) -> Unit,
    onSortSelected: (DetailSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    MapSheetSurface(
        state = state,
        sheetHeight = sheetHeight,
        modifier = modifier,
        header = {
            SightingSheetHeader(
                sightingCount = sightings.size,
                sort = sort,
                onSortSelected = onSortSelected
            )
        }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            items(items = sightings, key = { it.id }) { hit ->
                SightingRow(
                    hit = hit,
                    selected = hit.id == selectedId,
                    onClick = { onSelect(hit.id) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}
