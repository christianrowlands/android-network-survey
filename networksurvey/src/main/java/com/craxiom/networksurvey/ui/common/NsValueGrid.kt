package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.theme.NsTheme

/** The narrowest a grid cell gets at font scale 1, unless the caller asks for another width. */
val VALUE_CELL_MIN_WIDTH = 80.dp

/**
 * Values laid out as the prototype's panels do: up to [maxColumns] to a row, fewer when the width or
 * the font scale needs it (never fewer than [minColumns]), the last row padded so its cells keep the
 * width of the cells above.
 *
 * A value is never broken across lines while the grid can avoid it: the columns are as even as the
 * values allow (a column whose widest value needs more than an even share gets what it needs, the
 * others share the rest), and when the values cannot all fit on one line at this many columns, the
 * grid drops a column. Only at [minColumns] does a value that is still too wide wrap. A cell's width
 * need is its widest content on one line ([NsValueCell] counts only its value, so a label wraps
 * instead of costing a column).
 */
@Composable
fun <T> NsValueGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    cellMinWidth: Dp = VALUE_CELL_MIN_WIDTH,
    minColumns: Int = 2,
    maxColumns: Int = 4,
    cell: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val columnGap = NsTheme.spacing.xs
    val rowGap = NsTheme.spacing.s
    Layout(
        content = {
            // One box per item, so a cell is one measurable whatever it emits; the box hands the
            // cell its fixed size, which also keeps its label level with its neighbors' labels.
            items.forEach { item -> Box(propagateMinConstraints = true) { cell(item, Modifier) } }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val gap = columnGap.roundToPx()
        val needs = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth else needs.sum() + gap * (maxColumns - 1)
        val startColumns = (available / (cellMinWidth * fontScale).toPx()).toInt().coerceIn(minColumns, maxColumns)
        val plan = planValueGridColumns(needs, available, gap, startColumns, minColumns)
        val columns = plan.widths.size
        val placeables = measurables.indices.chunked(columns).map { row ->
            // Every cell in a row takes the row's height, as a Row at IntrinsicSize.Min would.
            val height = row.maxOf { measurables[it].minIntrinsicHeight(plan.widths[it % columns]) }
            row.map { measurables[it].measure(Constraints.fixed(plan.widths[it % columns], height)) }
        }
        val rowGapPx = rowGap.roundToPx()
        val height = placeables.sumOf { row -> row.maxOf { it.height } } + rowGapPx * (placeables.size - 1).coerceAtLeast(0)
        val width = plan.widths.sum() + gap * (columns - 1)
        layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
            var y = 0
            placeables.forEach { row ->
                var x = 0
                row.forEachIndexed { column, placeable ->
                    placeable.placeRelative(x, y)
                    x += plan.widths[column] + gap
                }
                y += row.maxOf { it.height } + rowGapPx
            }
        }
    }
}

/** The column widths a [NsValueGrid] uses, in pixels; their count is the number of columns. */
internal data class ValueGridColumns(val widths: List<Int>)

/**
 * Plans a value grid's columns: starting at [startColumns], the first column count (down to
 * [minColumns]) at which every value fits on one line. Its columns are as even as possible: a
 * column whose widest value is wider than an even share keeps that width, the others share what is
 * left. When nothing fits even at [minColumns], the columns are even and the widest values wrap.
 *
 * @param needs     Each cell's width on one line, in item order.
 * @param available The grid's width.
 * @param gap       The space between two columns.
 */
internal fun planValueGridColumns(needs: List<Int>, available: Int, gap: Int, startColumns: Int, minColumns: Int): ValueGridColumns {
    for (columns in startColumns downTo minColumns.coerceAtMost(startColumns)) {
        val space = available - gap * (columns - 1)
        val columnNeeds = List(columns) { column -> needs.filterIndexed { index, _ -> index % columns == column }.maxOrNull() ?: 0 }
        if (columnNeeds.sum() <= space) return ValueGridColumns(evenWidths(columnNeeds, space))
    }
    val columns = minColumns.coerceAtMost(startColumns)
    return ValueGridColumns(evenWidths(List(columns) { 0 }, (available - gap * (columns - 1)).coerceAtLeast(0)))
}

/**
 * @return Widths summing to [space], as even as [needs] allow: a column that needs more than an
 * even share of what is left gets its need, the rest split the remainder (leftover pixels go to the
 * first of them). Since the needs fit in [space], at least one column always splits the remainder.
 */
private fun evenWidths(needs: List<Int>, space: Int): List<Int> {
    val widths = needs.toMutableList()
    var open = needs.indices.toList()
    var remaining = space
    while (true) {
        val share = remaining / open.size
        val wide = open.filter { needs[it] > share }
        if (wide.isEmpty()) break
        wide.forEach { remaining -= needs[it] }
        open = open - wide.toSet()
    }
    open.forEachIndexed { position, column ->
        widths[column] = remaining / open.size + if (position < remaining % open.size) 1 else 0
    }
    return widths
}

/**
 * One value over its uppercase label, centered; in a [NsValueGrid] the value stays on one line
 * whenever the grid can make room, and otherwise wraps rather than clips. The label and [detail] do
 * not ask the grid for width: the label wraps between words, the detail is cut. Given more height
 * than it needs (a taller neighbor in the same grid row), the value stays at the top and the label at
 * the bottom. TalkBack reads [spoken] as one item.
 *
 * @param detail        A short muted line under the value (a band's name), cut to one line.
 * @param labelTrailing Sits after the label (a chevron on a value that opens something).
 * @param labelColor    The label's color; [labelTrailing] usually matches it.
 */
@Composable
fun NsValueCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    spoken: String = "$label, $value",
    detail: String? = null,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelTrailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .semantics { contentDescription = spoken }
            .heightIn(min = NsTheme.spacing.touchTarget)
            .padding(vertical = NsTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = valueColor,
                textAlign = TextAlign.Center,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.then(NoGridWidthNeed),
                )
            }
        }
        Row(Modifier.clearAndSetSemantics {}.then(NoGridWidthNeed), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
            labelTrailing?.invoke(this)
        }
    }
}

/**
 * Reports no intrinsic width, so a [NsValueCell]'s label and detail never widen a [NsValueGrid]
 * column (only the value does); measuring and placing are unchanged.
 */
private object NoGridWidthNeed : LayoutModifier {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int = 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int = 0
}

/**
 * Copies on a long press, with [label] as TalkBack's name for the action; no tap action. The gesture
 * is keyed on [value], not on [copy], so an update that recomposes the screen mid-press (a new lambda,
 * the same value) does not cancel the press.
 */
fun Modifier.copyOnLongPress(label: String, value: String, copy: () -> Unit): Modifier =
    pointerInput(label, value) { detectTapGestures(onLongPress = { copy() }) }
        .semantics { onLongClick(label = label) { copy(); true } }
