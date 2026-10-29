package com.craxiom.networksurvey.gpstest.ui.sky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.theme.NsPalette

/** The radius of a satellite mark on the plot and in the legend. */
val SATELLITE_MARK_RADIUS = 5.5.dp

/** How a satellite is marked: its C/N0 fill, and a heavier outline when it is used in the fix. */
enum class MarkStatus { NOT_IN_VIEW, IN_VIEW, USED_IN_FIX }

/**
 * The colors of the sky marks in the current theme.
 *
 * @property scale The colors at [SkyProjection.CN0_STOPS], weakest first.
 */
@Immutable
data class SkyColors(val scale: List<Color>, val outline: Color, val hollow: Color) {

    /** @return The fill for [cn0DbHz], blended between the stops as GPSTest did. */
    fun cn0Color(cn0DbHz: Float): Color {
        val stop = SkyProjection.cn0Stop(cn0DbHz)
        val from = scale[stop.index]
        return if (stop.index == scale.lastIndex) from else lerp(from, scale[stop.index + 1], stop.fraction)
    }
}

/** @return The sky colors: gray, red, yellow and green on the theme's signal ramp. */
@Composable
fun skyColors(): SkyColors = SkyColors(
    scale = listOf(
        MaterialTheme.colorScheme.outline,
        NsPalette.Signal.VeryWeak,
        NsPalette.Signal.Fair,
        NsPalette.Signal.Strong,
    ),
    outline = MaterialTheme.colorScheme.onSurface,
    hollow = MaterialTheme.colorScheme.onSurfaceVariant,
)

private val THIN_OUTLINE = 1.dp
private val USED_OUTLINE = 2.5.dp
private val HOLLOW_OUTLINE = 1.5.dp

/**
 * Draws one satellite mark centered on [center]: filled with [fill] and outlined, more heavily when
 * used in the fix, or only outlined when the satellite is not heard. [SatelliteShape.NONE] draws
 * nothing.
 */
fun DrawScope.drawSatelliteMark(
    shape: SatelliteShape,
    center: Offset,
    radius: Float,
    status: MarkStatus,
    fill: Color,
    colors: SkyColors,
) {
    val path = markPath(shape, center, radius) ?: return
    val (strokeColor, strokeWidth) = when (status) {
        MarkStatus.NOT_IN_VIEW -> colors.hollow to HOLLOW_OUTLINE.toPx()
        MarkStatus.IN_VIEW -> colors.outline to THIN_OUTLINE.toPx()
        MarkStatus.USED_IN_FIX -> colors.outline to USED_OUTLINE.toPx()
    }
    if (status != MarkStatus.NOT_IN_VIEW) drawPath(path, fill)
    drawPath(path, strokeColor, style = Stroke(width = strokeWidth))
}

/** The outline of a mark, from the old sky view's proportions. */
private fun markPath(shape: SatelliteShape, c: Offset, r: Float): Path? {
    val path = Path()
    when (shape) {
        SatelliteShape.CIRCLE -> path.addOval(Rect(c, r))
        SatelliteShape.OVAL -> path.addOval(rectAround(c, Size(r * 3f, r * 2f)))
        SatelliteShape.SQUARE -> path.addRect(Rect(c, r))
        SatelliteShape.TRIANGLE -> path.polygon(c, r, 0f to -1f, -1f to 1f, 1f to 1f)
        SatelliteShape.DIAMOND -> path.polygon(c, r, 0f to -1f, -1.5f to 0f, 0f to 1f, 1.5f to 0f)
        SatelliteShape.PENTAGON -> path.polygon(c, r, 0f to -1f, -1f to -1f / 3, -2f / 3 to 1f, 2f / 3 to 1f, 1f to -1f / 3)
        SatelliteShape.HEXAGON -> path.polygon(c, r, -0.6f to -1f, -1.4f to 0f, -0.6f to 1f, 0.6f to 1f, 1.4f to 0f, 0.6f to -1f)
        SatelliteShape.NONE -> return null
    }
    return path
}

private fun rectAround(center: Offset, size: Size) = Rect(center - Offset(size.width / 2, size.height / 2), size)

/** Adds a closed polygon whose corners are given in units of [r] around [c]. */
private fun Path.polygon(c: Offset, r: Float, vararg corners: Pair<Float, Float>) {
    corners.forEachIndexed { i, (x, y) ->
        if (i == 0) moveTo(c.x + x * r, c.y + y * r) else lineTo(c.x + x * r, c.y + y * r)
    }
    close()
}

/** A satellite mark on its own, for the legend: decorative, TalkBack reads the row's text. */
@Composable
fun SatelliteMarkIcon(
    shape: SatelliteShape,
    status: MarkStatus,
    fill: Color,
    modifier: Modifier = Modifier,
    size: Dp = MARK_ICON_SIZE,
) {
    val colors = skyColors()
    Canvas(modifier.size(size)) {
        drawSatelliteMark(shape, center, SATELLITE_MARK_RADIUS.toPx(), status, fill, colors)
    }
}

private val MARK_ICON_SIZE = 24.dp
