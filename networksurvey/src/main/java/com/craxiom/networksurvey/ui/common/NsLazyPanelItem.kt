package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * One item of a lazy list drawn as part of a single rounded panel, the lazy counterpart of
 * [NsRowGroup] (whose one Surface cannot span lazy items): the first item rounds the top corners,
 * the last the bottom ones, and an inset rule separates each item from the next. The panel keeps
 * the screen gutter and the content width cap. Rows bring their own horizontal padding, as they do
 * inside [NsRowGroup].
 *
 * @param index The item's position in the list.
 * @param count How many items the list has.
 * @param divider False to leave out the rule between items (a dense table's rows).
 * @param continuesAbove True when something above (a sticky header) draws the panel's top, so the
 * first item neither rounds its top corners nor adds space above it.
 */
@Composable
fun NsLazyPanelItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    continuesAbove: Boolean = false,
    content: @Composable () -> Unit,
) {
    val first = index == 0 && !continuesAbove
    val last = index == count - 1
    Column(
        modifier
            .nsContentWidth()
            .padding(horizontal = NsTheme.spacing.screenGutter)
            .padding(top = if (first) NsTheme.spacing.s else 0.dp, bottom = if (last) NsTheme.spacing.l else 0.dp)
            .clip(panelPart(first, last))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        content()
        if (divider && !last) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = NsTheme.spacing.panelPaddingHorizontal),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/** The panel shape with only the corners this item owns. */
@Composable
internal fun panelPart(first: Boolean, last: Boolean): Shape {
    val panel = NsTheme.shapes.panel
    val square = CornerSize(0.dp)
    return when {
        first && last -> panel
        first -> panel.copy(bottomStart = square, bottomEnd = square)
        last -> panel.copy(topStart = square, topEnd = square)
        else -> RectangleShape
    }
}
