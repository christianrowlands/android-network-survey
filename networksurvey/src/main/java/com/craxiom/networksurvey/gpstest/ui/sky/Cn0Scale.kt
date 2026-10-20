package com.craxiom.networksurvey.gpstest.ui.sky

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R

private val BAR_HEIGHT = 10.dp
private val MARKER_SIZE = 9.dp
private val MARKER_GAP = 2.dp
private val MARKER_STROKE = 1.5.dp

/** How long a marker takes to reach a new average; C/N0 updates every second. */
private const val MARKER_ANIMATION_MS = 300

/** The ticks under the bar: each value in dB-Hz and its label. */
private val TICKS = listOf(
    10f to R.string.sky_legend_cn0_low,
    18f to R.string.sky_legend_cn0_low_middle,
    27f to R.string.sky_legend_cn0_middle,
    36f to R.string.sky_legend_cn0_middle_high,
    45f to R.string.sky_legend_cn0_high,
)

/**
 * The C/N0 color scale from 10 to 45 dB-Hz with its ticks, and optionally the in-view average
 * (a hollow marker above the bar) and the used-in-fix average (a filled marker below it). A marker
 * slides to a new average; one with no average is not drawn. Decorative: the numbers are written
 * out next to it.
 */
@Composable
fun Cn0Scale(modifier: Modifier = Modifier, inViewAverage: Float? = null, usedAverage: Float? = null) {
    val colors = skyColors()
    val ink = MaterialTheme.colorScheme.onSurface
    val inView = inViewAverage?.let { animatedFraction(it) }
    val used = usedAverage?.let { animatedFraction(it) }
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(MARKER_SIZE + MARKER_GAP + BAR_HEIGHT + MARKER_GAP + MARKER_SIZE),
        ) {
            val markerSize = MARKER_SIZE.toPx()
            val barTop = markerSize + MARKER_GAP.toPx()
            val barHeight = BAR_HEIGHT.toPx()
            // Inset by half a marker, so a marker at either end stays inside the canvas.
            val start = markerSize / 2
            val length = size.width - markerSize
            // The stops sit at their place on the 10 to 45 bar, so the gradient is GPSTest's scale.
            val stops = SkyProjection.CN0_STOPS.mapIndexed { i, cn0 -> SkyProjection.meterFraction(cn0) to colors.scale[i] }
            drawRoundRect(
                brush = Brush.horizontalGradient(*stops.toTypedArray(), startX = start, endX = start + length),
                topLeft = Offset(start, barTop),
                size = Size(length, barHeight),
                cornerRadius = CornerRadius(barHeight / 2),
            )
            inView?.let { drawMarker(start + it * length, barTop, pointsDown = true, filled = false, ink) }
            used?.let { drawMarker(start + it * length, barTop + barHeight, pointsDown = false, filled = true, ink) }
        }
        Ticks()
    }
}

@Composable
private fun animatedFraction(cn0DbHz: Float): Float {
    val fraction by animateFloatAsState(
        targetValue = SkyProjection.meterFraction(cn0DbHz),
        animationSpec = tween(MARKER_ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "cn0Marker",
    )
    return fraction
}

/** A triangle whose tip touches the bar edge at [edgeY]. */
private fun DrawScope.drawMarker(x: Float, edgeY: Float, pointsDown: Boolean, filled: Boolean, color: Color) {
    val size = MARKER_SIZE.toPx()
    val gap = MARKER_GAP.toPx()
    val tipY = if (pointsDown) edgeY - gap else edgeY + gap
    val baseY = if (pointsDown) tipY - size else tipY + size
    val path = Path().apply {
        moveTo(x, tipY)
        lineTo(x - size / 2, baseY)
        lineTo(x + size / 2, baseY)
        close()
    }
    if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(MARKER_STROKE.toPx()))
}

/** The tick labels, each centered on its value along the bar and kept inside it. */
@Composable
private fun Ticks() {
    val labels = TICKS.map { (_, label) -> stringResource(label) }
    Layout(
        content = {
            labels.forEach {
                Text(text = it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val start = MARKER_SIZE.toPx() / 2
        val length = width - MARKER_SIZE.toPx()
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { i, placeable ->
                val fraction = SkyProjection.meterFraction(TICKS[i].first)
                val x = (start + fraction * length - placeable.width / 2f).toInt()
                    .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}

/** Draws a marker glyph for the averages' text: hollow for in view, filled for used in the fix. */
@Composable
fun Cn0MarkerGlyph(filled: Boolean, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.size(MARKER_SIZE + MARKER_GAP * 2)) {
        val tipEdge = if (filled) 0f else size.height
        drawMarker(size.width / 2, tipEdge, pointsDown = !filled, filled = filled, color = ink)
    }
}
