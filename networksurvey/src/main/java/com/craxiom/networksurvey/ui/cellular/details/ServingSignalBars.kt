package com.craxiom.networksurvey.ui.cellular.details

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.details.model.CellField
import com.craxiom.networksurvey.ui.common.copyOnLongPress
import com.craxiom.networksurvey.ui.common.signalFillColor
import com.craxiom.networksurvey.ui.common.signalTextColor
import com.craxiom.networksurvey.ui.theme.NsTheme

/** A bar's height on the serving card, and on the compact NR secondary card. */
private val BAR_HEIGHT = 14.dp
private val COMPACT_BAR_HEIGHT = 8.dp

/** A bar row's minimum height. */
private val ROW_MIN_HEIGHT = 32.dp

/** Below this width a bar moves to its own line under its label and value. */
private val NARROWEST_BAR = 56.dp

/** The no-value line's thickness. */
private val NO_VALUE_LINE = 2.dp

/** The fill's outline in light theme, where the yellow fill alone fades into white. */
private val LIGHT_FILL_STROKE = 1.dp

/** The track: a neutral base, then the bucket's hue over it, so it shows in both themes. */
private const val NEUTRAL_TRACK_ALPHA = 0.20f
private const val HUE_TRACK_ALPHA = 0.25f

/** How long a bar takes to follow a new value. */
private const val FILL_ANIMATION_MS = 300

/** A surface brighter than this is a light theme. */
private const val LIGHT_SURFACE_LUMINANCE = 0.5f

/** The widest value a bar shows, which sets the value column's width. */
private const val WIDEST_VALUE = "-140"

/**
 * The serving cell's signal values as bars, one row per value: the label, a bar filled to the
 * value, and the value. Length, bar color and text color come from one scale ([CellField.fraction]
 * and [CellField.bucket]), so they never disagree. A value the phone did not report draws a thin
 * line and "--" instead of an empty bar, so it never reads as a very weak signal.
 *
 * @param compact Thinner bars, for the NR secondary card beside the serving card.
 */
@Composable
fun ServingSignalBars(
    fields: List<CellField>,
    onCopy: (label: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val labels = fields.map { stringResource(fieldLabel(it.kind)).uppercase() }
    val texts = fields.map { fieldText(it) }
    val labelStyle = MaterialTheme.typography.labelMedium
    val valueStyle = MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widestValue = stringResource(R.string.dbm_value_label, WIDEST_VALUE)
    val labelWidth = with(density) { labels.maxOfOrNull { measurer.measure(it, labelStyle).size.width }?.toDp() ?: 0.dp }
    val valueWidth = with(density) { (texts + widestValue).maxOf { measurer.measure(it, valueStyle).size.width }.toDp() }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = NsTheme.spacing.m
        val stacked = maxWidth - labelWidth - valueWidth - gap * 2 < NARROWEST_BAR
        Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs)) {
            fields.forEachIndexed { index, field ->
                BarRow(
                    field = field,
                    label = labels[index],
                    text = texts[index],
                    labelWidth = labelWidth,
                    valueWidth = valueWidth,
                    stacked = stacked,
                    barHeight = if (compact) COMPACT_BAR_HEIGHT else BAR_HEIGHT,
                    onCopy = onCopy,
                )
            }
        }
    }
}

@Composable
private fun BarRow(
    field: CellField,
    label: String,
    text: String,
    labelWidth: Dp,
    valueWidth: Dp,
    stacked: Boolean,
    barHeight: Dp,
    onCopy: (String, String) -> Unit,
) {
    val plainLabel = stringResource(fieldLabel(field.kind))
    val spoken = fieldSpoken(plainLabel, field)
    val copyLabel = stringResource(R.string.cellular_copy_action)
    val gestures = if (field.value != null) Modifier.copyOnLongPress(copyLabel, text) { onCopy(plainLabel, text) } else Modifier
    val rowModifier = Modifier
        .fillMaxWidth()
        .semantics { contentDescription = spoken }
        .then(gestures)
    val labelText: @Composable () -> Unit = {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(labelWidth),
        )
    }
    val valueText: @Composable () -> Unit = {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (field.value != null) signalTextColor(field.bucket) else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(valueWidth),
        )
    }

    // The row speaks as one item; its parts say nothing on their own.
    Box(rowModifier) {
        if (stacked) {
            Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    labelText()
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) { valueText() }
                }
                SignalBar(field, barHeight, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.clearAndSetSemantics {}.heightIn(min = ROW_MIN_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
            ) {
                labelText()
                SignalBar(field, barHeight, Modifier.weight(1f))
                valueText()
            }
        }
    }
}

/**
 * One bar: the track and a pill-shaped fill at least as wide as it is tall, so a very weak signal
 * still shows a dot; or a thin centered line when there is no value. The fill starts at its value
 * (no replay from empty when the card comes back) and animates when the value changes.
 */
@Composable
private fun SignalBar(field: CellField, height: Dp, modifier: Modifier) {
    val fraction = field.fraction?.takeIf { field.value != null }
    val fill = signalFillColor(field.bucket)
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val panel = MaterialTheme.colorScheme.surfaceContainerLow
    val track = fill.copy(alpha = HUE_TRACK_ALPHA).compositeOver(neutral.copy(alpha = NEUTRAL_TRACK_ALPHA).compositeOver(panel))
    val stroke = if (MaterialTheme.colorScheme.surface.luminance() > LIGHT_SURFACE_LUMINANCE) signalTextColor(field.bucket) else null
    val noValueLine = MaterialTheme.colorScheme.outlineVariant

    val animated = remember { Animatable(fraction ?: 0f) }
    LaunchedEffect(fraction) {
        if (fraction != null) animated.animateTo(fraction, tween(FILL_ANIMATION_MS))
    }

    Canvas(modifier.height(height)) {
        if (fraction == null) {
            val line = NO_VALUE_LINE.toPx()
            drawRoundRect(noValueLine, Offset(0f, (size.height - line) / 2), Size(size.width, line), CornerRadius(line / 2))
        } else {
            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
            drawFill(animated.value, fill, stroke)
        }
    }
}

private fun DrawScope.drawFill(fraction: Float, fill: Color, stroke: Color?) {
    val radius = CornerRadius(size.height / 2)
    val width = (size.width * fraction).coerceIn(size.height, size.width)
    drawRoundRect(fill, size = Size(width, size.height), cornerRadius = radius)
    if (stroke != null) {
        val strokeWidth = LIGHT_FILL_STROKE.toPx()
        val inset = strokeWidth / 2
        drawRoundRect(
            color = stroke,
            topLeft = Offset(inset, inset),
            size = Size(width - strokeWidth, size.height - strokeWidth),
            cornerRadius = CornerRadius(radius.x - inset),
            style = Stroke(strokeWidth),
        )
    }
}
