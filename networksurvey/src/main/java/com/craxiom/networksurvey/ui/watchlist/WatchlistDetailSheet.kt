package com.craxiom.networksurvey.ui.watchlist

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity
import kotlin.math.roundToInt

/** The three heights the sighting-details sheet snaps between (least to most open). */
enum class SheetDetent { Peek, Half, Full }

/**
 * The draggable sightings sheet for the sighting-details screen. It overlays the map and is moved
 * between [SheetDetent]s by dragging its header/handle (via [state]); the sighting list scrolls
 * independently so the user can browse at the middle stop while still panning the exposed map.
 *
 * The sheet is a fixed [sheetHeight]-tall surface pushed down by [state]'s offset so only the current
 * detent's worth of it shows. Anchors are owned by the caller (which knows the screen height).
 *
 * @param sheetHeight the full (most-open) height of the sheet; equals the Full detent's visible height.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .offset {
                val y = state.offset
                IntOffset(0, if (y.isNaN()) 0 else y.roundToInt())
            },
        color = BottomSheetDefaults.ContainerColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header (handle + count/sort) is the drag surface; dragging it snaps between detents.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .anchoredDraggable(state, Orientation.Vertical)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    BottomSheetDefaults.DragHandle()
                }
                SightingSheetHeader(
                    sightingCount = sightings.size,
                    sort = sort,
                    onSortSelected = onSortSelected
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
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
}
