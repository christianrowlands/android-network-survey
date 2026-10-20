package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** The line's thickness. */
private val LINE_WIDTH = 1.5.dp

/** The radius of the dot on the newest point. */
private val END_DOT_RADIUS = 2.5.dp

/** The dashed median line's thickness and dash. */
private val BASELINE_WIDTH = 1.dp
private val BASELINE_DASH = 3.dp

/** Opacity of the track behind the line, which shows the whole window while it fills. */
private const val TRACK_ALPHA = 0.08f

/** Opacity of the dashed median line. */
private const val BASELINE_ALPHA = 0.6f

/**
 * A small trend line over a fixed time window that ends at [nowMs] on the right edge, so lines on
 * different rows share one time axis and a new one grows in from the right. Values outside
 * [min]..[max] are drawn at the edge. A pause longer than [gapMs] between two points breaks the
 * line. The newest point, when it is current, gets a dot in [endColor].
 *
 * Decorative: the caller says what the line shows in its own spoken text.
 *
 * @param baseline A value to draw as a dashed line (the window's median), or null for none.
 */
@Composable
fun NsSparkline(
    series: TrendSeries,
    nowMs: Long,
    windowMs: Long,
    gapMs: Long,
    min: Float,
    max: Float,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    endColor: Color = MaterialTheme.colorScheme.onSurface,
    baseline: Float? = null,
) {
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TRACK_ALPHA)
    val baselineColor = MaterialTheme.colorScheme.outline.copy(alpha = BASELINE_ALPHA)
    Spacer(
        modifier.drawWithCache {
            val line = LINE_WIDTH.toPx()
            val inset = END_DOT_RADIUS.toPx()
            val top = inset
            val bottom = size.height - inset
            val span = (max - min).takeIf { it > 0f } ?: 1f
            fun x(timeMs: Long): Float = size.width * (1f - (nowMs - timeMs).toFloat() / windowMs)
            fun y(value: Float): Float = bottom - (bottom - top) * ((value.coerceIn(min, max) - min) / span)

            val path = Path()
            var previous: TrendPoint? = null
            for (point in series.points) {
                if (nowMs - point.timeMs > windowMs) continue
                val joined = previous != null && point.timeMs - previous.timeMs <= gapMs
                if (joined) path.lineTo(x(point.timeMs), y(point.value)) else path.moveTo(x(point.timeMs), y(point.value))
                previous = point
            }
            val newest = series.points.lastOrNull()?.takeIf { nowMs - it.timeMs <= gapMs }
            val dash = BASELINE_DASH.toPx()
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))

            onDrawBehind {
                drawRoundRect(trackColor, cornerRadius = CornerRadius(inset))
                baseline?.let {
                    val baselineY = y(it)
                    drawLine(baselineColor, Offset(0f, baselineY), Offset(size.width, baselineY), BASELINE_WIDTH.toPx(), pathEffect = dashEffect)
                }
                drawPath(path, lineColor, style = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round))
                newest?.let { drawCircle(endColor, END_DOT_RADIUS.toPx(), Offset(x(it.timeMs), y(it.value))) }
            }
        }
    )
}
