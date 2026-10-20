package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * A section heading with an optional count or state on the right ("3 of 6 on"), or an [action]
 * (a text button such as "Manage") in its place.
 *
 * The right-hand part stays on the title's line only when the whole title fits beside it on one
 * line. Otherwise it moves to its own line under the title, start-aligned, so a long count, a
 * translation or a large font never squeezes the title into a narrow column.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    topPadding: Dp = NsTheme.spacing.l,
    action: (@Composable () -> Unit)? = null,
) {
    val end: (@Composable () -> Unit)? = when {
        trailing != null || action != null -> {
            {
                // One measurable for whatever the right-hand part emits
                Box {
                    if (trailing != null) {
                        Text(
                            text = trailing,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    action?.invoke()
                }
            }
        }

        else -> null
    }
    HeaderLine(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = if (action == null) NsTheme.spacing.s else 0.dp),
        centerEnd = action != null,
        gap = NsTheme.spacing.s,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        },
        end = end,
    )
}

/**
 * Lays out [title] and the optional [end] on one line when the title's one-line width, [gap] and
 * the end's width fit; otherwise stacks the end under the title. The title fills the rest of the
 * line, so the end sits at the right edge.
 */
@Composable
private fun HeaderLine(
    modifier: Modifier,
    centerEnd: Boolean,
    gap: Dp,
    title: @Composable () -> Unit,
    end: (@Composable () -> Unit)?,
) {
    Layout(contents = listOf(title, end ?: {}), modifier = modifier) { (titleParts, endParts), constraints ->
        val titleMeasurable = titleParts.single()
        val endMeasurable = endParts.singleOrNull()
        if (endMeasurable == null) {
            val placeable = titleMeasurable.measure(constraints.copy(minWidth = constraints.maxWidth.takeIf { constraints.hasBoundedWidth } ?: 0))
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            measureWithEnd(titleMeasurable, endMeasurable, constraints, gap.roundToPx(), centerEnd)
        }
    }
}

private fun MeasureScope.measureWithEnd(
    title: Measurable,
    end: Measurable,
    constraints: Constraints,
    gap: Int,
    centerEnd: Boolean,
): MeasureResult {
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else title.maxIntrinsicWidth(Constraints.Infinity) + gap + end.maxIntrinsicWidth(Constraints.Infinity)
    val loose = Constraints(maxWidth = width)
    val endPlaceable = end.measure(loose)
    val oneLine = title.maxIntrinsicWidth(Constraints.Infinity) + gap + endPlaceable.width <= width
    return if (oneLine) {
        val titlePlaceable = title.measure(Constraints.fixedWidth(width - gap - endPlaceable.width))
        val height = maxOf(titlePlaceable.height, endPlaceable.height)
        // Both sit on the bottom beside a count, both centered beside a taller text button
        fun y(childHeight: Int) = if (centerEnd) (height - childHeight) / 2 else height - childHeight
        layout(width, height) {
            titlePlaceable.place(0, y(titlePlaceable.height))
            endPlaceable.place(width - endPlaceable.width, y(endPlaceable.height))
        }
    } else {
        val titlePlaceable = title.measure(Constraints(minWidth = width, maxWidth = width))
        layout(width, titlePlaceable.height + endPlaceable.height) {
            titlePlaceable.place(0, 0)
            endPlaceable.place(0, titlePlaceable.height)
        }
    }
}
