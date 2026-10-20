package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.theme.NsTheme

/** The narrowest a grid cell gets at font scale 1, unless the caller asks for another width. */
val VALUE_CELL_MIN_WIDTH = 80.dp

/**
 * Values laid out as the prototype's panels do: up to [maxColumns] to a row, fewer when the width or
 * the font scale needs it (never fewer than [minColumns]), the last row padded so its cells keep the
 * same width as the rows above.
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
    BoxWithConstraints(modifier) {
        val fontScale = LocalDensity.current.fontScale
        val columns = (maxWidth / (cellMinWidth * fontScale)).toInt().coerceIn(minColumns, maxColumns)
        Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
            items.chunked(columns).forEach { row ->
                // Cells take the row's height, so a cell with an extra line keeps its label level
                // with its neighbors' labels.
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs)) {
                    row.forEach { item -> cell(item, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * One value over its uppercase label, centered; the value wraps rather than clips. Given more height
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
                )
            }
        }
        Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
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
 * Copies on a long press, with [label] as TalkBack's name for the action; no tap action. The gesture
 * is keyed on [value], not on [copy], so an update that recomposes the screen mid-press (a new lambda,
 * the same value) does not cancel the press.
 */
fun Modifier.copyOnLongPress(label: String, value: String, copy: () -> Unit): Modifier =
    pointerInput(label, value) { detectTapGestures(onLongPress = { copy() }) }
        .semantics { onLongClick(label = label) { copy(); true } }
