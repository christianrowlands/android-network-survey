package com.craxiom.networksurvey.ui.watchlist

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import kotlin.math.roundToInt

/** The three heights a watchlist map sheet snaps between (least to most open). */
enum class SheetDetent { Peek, Half, Full }

/** Fraction of the available height a map sheet occupies at the Half detent. */
private const val HALF_HEIGHT_FRACTION = 0.33f

/** Fraction of the available height a map sheet occupies at the Full detent. */
private const val FULL_HEIGHT_FRACTION = 0.64f

/**
 * The measured geometry for an anchored map sheet.
 *
 * @property sheetHeight the sheet's total height; equals the Full detent's visible height.
 * @property mapBottomInset the map camera padding matching the sheet's current settle target, so the
 *   map frames its points in the band above the sheet.
 */
data class MapSheetMetrics(
    val sheetHeight: Dp,
    val mapBottomInset: Dp,
)

/**
 * Computes the [MapSheetMetrics] for a map screen's anchored bottom sheet and keeps [state]'s anchors in
 * sync with them. The sheet snaps between the caller-supplied Peek height, ~33% (Half), and ~64% (Full)
 * of [availableHeight].
 *
 * The anchors are set during composition (not in a post-composition effect) so the sheet's first frame
 * is already positioned at its initial detent instead of flashing fully-open before snapping. The
 * returned [MapSheetMetrics.mapBottomInset] follows [AnchoredDraggableState.targetValue] (the projected
 * settle target), so the map re-pads only when the settle target changes, not on every drag frame.
 *
 * @param availableHeight the height of the area the sheet is anchored in (the screen or map box).
 * @param peekVisibleHeight the visible height at the Peek detent, including any nav-bar inset.
 */
@Composable
fun rememberMapSheetMetrics(
    state: AnchoredDraggableState<SheetDetent>,
    availableHeight: Dp,
    peekVisibleHeight: Dp,
): MapSheetMetrics {
    val density = LocalDensity.current

    // Visible height at each detent; Full is also the sheet's total height.
    val halfVisible = availableHeight * HALF_HEIGHT_FRACTION
    val fullVisible = availableHeight * FULL_HEIGHT_FRACTION

    val fullPx = with(density) { fullVisible.toPx() }
    val anchors = remember(peekVisibleHeight, halfVisible, fullVisible, density) {
        with(density) {
            DraggableAnchors {
                SheetDetent.Full at 0f
                SheetDetent.Half at (fullPx - halfVisible.toPx())
                SheetDetent.Peek at (fullPx - peekVisibleHeight.toPx())
            }
        }
    }
    // updateAnchors is idempotent and only re-keys when the measured anchors change (rotation / window
    // resize), so calling it from composition is safe and avoids a first-frame flash.
    remember(anchors) { state.updateAnchors(anchors); anchors }

    val mapBottomInset = when (state.targetValue) {
        SheetDetent.Peek -> peekVisibleHeight
        SheetDetent.Half -> halfVisible
        SheetDetent.Full -> fullVisible
    }
    return MapSheetMetrics(sheetHeight = fullVisible, mapBottomInset = mapBottomInset)
}

/**
 * The shared shell for a draggable sheet anchored over a watchlist map: a fixed [sheetHeight]-tall
 * surface pushed down by [state]'s offset so only the current detent's band shows. The drag handle plus
 * [header] form the drag surface; [content] (typically a lazy list) scrolls independently so the user
 * can browse at the middle stop while still panning the exposed map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSheetSurface(
    state: AnchoredDraggableState<SheetDetent>,
    sheetHeight: Dp,
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
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
            // Header (handle + summary) is the drag surface; dragging it snaps between detents.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .anchoredDraggable(state, Orientation.Vertical)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    BottomSheetDefaults.DragHandle()
                }
                header()
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            content()
        }
    }
}

/**
 * The draggable network-key sheet for the history map, replacing the old unbounded legend card. At Peek
 * it shows a one-line summary; at Half/Full it reveals a scrollable list of the matched networks (color
 * dot, name, located-sighting count). Tapping a row selects that network (highlighting its map pins);
 * the selected row reveals a "View sightings" action that opens the network's sighting details.
 */
@Composable
fun WatchlistHistoryMapSheet(
    state: AnchoredDraggableState<SheetDetent>,
    sheetHeight: Dp,
    legend: List<WatchlistMapLegendEntry>,
    selectedEntryId: Long?,
    listState: LazyListState,
    onSelect: (Long) -> Unit,
    onOpenDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    MapSheetSurface(
        state = state,
        sheetHeight = sheetHeight,
        modifier = modifier,
        header = {
            val locatedSightings = legend.sumOf { it.count }
            Text(
                text = stringResource(
                    R.string.watchlist_history_map_summary,
                    pluralStringResource(
                        R.plurals.watchlist_networks_count,
                        legend.size,
                        legend.size
                    ),
                    pluralStringResource(
                        R.plurals.watchlist_located_sightings_count,
                        locatedSightings,
                        locatedSightings
                    )
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            items(items = legend, key = { it.entryId }) { entry ->
                WatchlistNetworkRow(
                    entry = entry,
                    selected = entry.entryId == selectedEntryId,
                    onClick = { onSelect(entry.entryId) },
                    onOpenDetail = { onOpenDetail(entry.entryId) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}
