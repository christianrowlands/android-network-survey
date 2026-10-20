package com.craxiom.networksurvey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.theme.NsTheme

/*
 * Layouts that keep one line at the default font scale and give way at a large font scale or in a
 * long translation, so a word is never broken in the middle and an action never runs off the screen.
 */

/** When [NsBesideOrBelow] keeps its end part on the line of its main part. */
enum class BesideRule {
    /** Only when the whole main part fits on one line beside the end part (a heading and its count). */
    WHOLE_LINE,

    /**
     * When every word of the main part still fits beside the end part, and the end part takes at most
     * half the width (a row's text and its action).
     */
    WORDS,
}

/**
 * Lays out [main] and [end] on one line when [rule] allows it, [main] filling the rest of the line so
 * [end] sits at the right edge; otherwise [end] moves to its own line under [main], start-aligned.
 * [main] must emit exactly one layout node, and [end] one or none (wrap several in a Column or Row).
 *
 * @param centerEnd True to center both parts on the line (beside a text button); false to align
 *                  their bottoms (beside a count).
 */
@Composable
fun NsBesideOrBelow(
    modifier: Modifier = Modifier,
    rule: BesideRule = BesideRule.WORDS,
    gap: Dp = NsTheme.spacing.s,
    centerEnd: Boolean = true,
    main: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    Layout(contents = listOf(main, end), modifier = modifier) { (mainParts, endParts), constraints ->
        val mainMeasurable = mainParts.single()
        val endMeasurable = endParts.singleOrNull()
        if (endMeasurable == null) {
            val placeable = mainMeasurable.measure(constraints.copy(minWidth = constraints.maxWidth.takeIf { constraints.hasBoundedWidth } ?: 0))
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        } else {
            measureBesideOrBelow(mainMeasurable, endMeasurable, constraints, gap.roundToPx(), rule, centerEnd)
        }
    }
}

private fun MeasureScope.measureBesideOrBelow(
    main: Measurable,
    end: Measurable,
    constraints: Constraints,
    gap: Int,
    rule: BesideRule,
    centerEnd: Boolean,
): MeasureResult {
    val width = if (constraints.hasBoundedWidth) {
        constraints.maxWidth
    } else {
        main.maxIntrinsicWidth(Constraints.Infinity) + gap + end.maxIntrinsicWidth(Constraints.Infinity)
    }
    val endPlaceable = end.measure(Constraints(maxWidth = width))
    val beside = when (rule) {
        BesideRule.WHOLE_LINE -> main.maxIntrinsicWidth(Constraints.Infinity) + gap + endPlaceable.width <= width
        BesideRule.WORDS -> main.minIntrinsicWidth(Constraints.Infinity) + gap + endPlaceable.width <= width &&
            endPlaceable.width * 2 <= width
    }
    return if (beside) {
        val mainPlaceable = main.measure(Constraints.fixedWidth(width - gap - endPlaceable.width))
        val height = maxOf(mainPlaceable.height, endPlaceable.height)
        fun y(childHeight: Int) = if (centerEnd) (height - childHeight) / 2 else height - childHeight
        layout(width, height) {
            mainPlaceable.placeRelative(0, y(mainPlaceable.height))
            endPlaceable.placeRelative(width - endPlaceable.width, y(endPlaceable.height))
        }
    } else {
        val mainPlaceable = main.measure(Constraints.fixedWidth(width))
        layout(width, mainPlaceable.height + endPlaceable.height) {
            mainPlaceable.placeRelative(0, 0)
            endPlaceable.placeRelative(0, mainPlaceable.height)
        }
    }
}

/**
 * A line of actions (text buttons) that wraps onto more lines when they don't fit, instead of
 * squeezing or clipping them. The optional [end] (a confirming button such as OK) always comes last,
 * at the right edge: on the actions' last line when it fits there, otherwise on a line of its own.
 * At the default font scale it looks like a [androidx.compose.foundation.layout.Row] with a
 * weighted spacer before [end].
 *
 * @param gap The space between two items on a line.
 */
@Composable
fun NsActionRow(
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    end: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    Layout(contents = listOf(actions, end ?: {}), modifier = modifier) { (actionParts, endParts), constraints ->
        val gapPx = gap.roundToPx()
        val actionPlaceables = actionParts.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val endPlaceables = endParts.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            (actionPlaceables + endPlaceables).sumOf { it.width } + gapPx * (actionPlaceables.size + endPlaceables.size)
        }

        val lines = packLines(actionPlaceables, width, gapPx)
        val hasEnd = endPlaceables.isNotEmpty()
        val endWidth = lineWidth(endPlaceables, gapPx)
        val endHeight = endPlaceables.maxOfOrNull { it.height } ?: 0
        val endOnLastLine = hasEnd && lines.isNotEmpty() && lineWidth(lines.last(), gapPx) + gapPx + endWidth <= width
        val lineHeights = lines.mapIndexed { index, line ->
            val height = line.maxOf { it.height }
            if (endOnLastLine && index == lines.lastIndex) maxOf(height, endHeight) else height
        }
        val endOwnLine = hasEnd && !endOnLastLine
        val height = lineHeights.sum() + if (endOwnLine) endHeight else 0
        val layoutWidth = if (hasEnd) width else (lines.maxOfOrNull { lineWidth(it, gapPx) } ?: 0)

        layout(layoutWidth.coerceIn(constraints.minWidth, constraints.maxWidth), height) {
            var y = 0
            lines.forEachIndexed { index, line ->
                placeLine(line, 0, y, lineHeights[index], gapPx)
                y += lineHeights[index]
            }
            when {
                endOnLastLine -> placeLine(endPlaceables, width - endWidth, y - lineHeights.last(), lineHeights.last(), gapPx)
                endOwnLine -> placeLine(endPlaceables, width - endWidth, y, endHeight, gapPx)
            }
        }
    }
}

/** Splits [placeables] into lines no wider than [width], in order. */
private fun packLines(placeables: List<Placeable>, width: Int, gap: Int): List<List<Placeable>> {
    val lines = mutableListOf<MutableList<Placeable>>()
    placeables.forEach { placeable ->
        val line = lines.lastOrNull()
        if (line == null || lineWidth(line, gap) + gap + placeable.width > width) {
            lines += mutableListOf(placeable)
        } else {
            line += placeable
        }
    }
    return lines
}

private fun lineWidth(line: List<Placeable>, gap: Int): Int =
    if (line.isEmpty()) 0 else line.sumOf { it.width } + gap * (line.size - 1)

/** Places [line] from [x], each item centered in a line [lineHeight] tall. */
private fun Placeable.PlacementScope.placeLine(line: List<Placeable>, x: Int, top: Int, lineHeight: Int, gap: Int) {
    var left = x
    line.forEach { placeable ->
        placeable.placeRelative(left, top + (lineHeight - placeable.height) / 2)
        left += placeable.width + gap
    }
}

/**
 * A grid of equal-width cells, up to [maxColumns] per row, that uses fewer columns when a cell
 * cannot fit its longest word (its minimum intrinsic width) in a column: at a large font scale the
 * record type tiles go to one column rather than break "Bluetooth" in the middle. The cells of a row
 * share the height of the tallest.
 *
 * @param gap    The space between two columns.
 * @param rowGap The space between two rows.
 */
@Composable
fun NsAdaptiveGrid(
    maxColumns: Int,
    modifier: Modifier = Modifier,
    gap: Dp = NsTheme.spacing.s,
    rowGap: Dp = gap,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val rowGapPx = rowGap.roundToPx()
        val widest = measurables.maxOfOrNull { it.minIntrinsicWidth(Constraints.Infinity) } ?: 0
        // Unbounded (an intrinsic query from a parent): as wide as every column at its widest cell
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else widest * maxColumns + gapPx * (maxColumns - 1)
        val columns = adaptiveColumns(maxColumns, width, gapPx, widest)
        val columnWidth = columnWidth(width, columns, gapPx)

        val rows = measurables.chunked(columns).map { row ->
            val rowHeight = row.maxOf { it.minIntrinsicHeight(columnWidth) }
            row.map { it.measure(Constraints(minWidth = columnWidth, maxWidth = columnWidth, minHeight = rowHeight)) }
        }
        val rowHeights = rows.map { row -> row.maxOf { it.height } }
        val height = rowHeights.sum() + rowGapPx * (rows.size - 1).coerceAtLeast(0)

        layout(width, height) {
            var y = 0
            rows.forEachIndexed { index, row ->
                row.forEachIndexed { column, placeable -> placeable.placeRelative(column * (columnWidth + gapPx), y) }
                y += rowHeights[index] + rowGapPx
            }
        }
    }
}

/**
 * @return The most columns, up to [maxColumns], whose width in [width] (with [gap] between them)
 * still holds [widest]; at least one.
 */
private fun adaptiveColumns(maxColumns: Int, width: Int, gap: Int, widest: Int): Int =
    (maxColumns downTo 2).firstOrNull { columnWidth(width, it, gap) >= widest } ?: 1

private fun columnWidth(width: Int, columns: Int, gap: Int): Int = (width - gap * (columns - 1)) / columns
