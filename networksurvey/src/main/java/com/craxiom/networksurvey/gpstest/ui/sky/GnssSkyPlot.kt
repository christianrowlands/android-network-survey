package com.craxiom.networksurvey.gpstest.ui.sky

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import kotlin.math.min

private val GRID_STROKE = 1.dp
private val HORIZON_STROKE = 1.5.dp
private val NORTH_MARKER = 7.dp

/** The rings drawn inside the horizon, by elevation. */
private val ELEVATION_RINGS = floatArrayOf(30f, 60f)

/**
 * The sky above the phone: the horizon, rings at 30 and 60 degrees of elevation, a north marker,
 * and one mark per satellite (its constellation's shape, filled by C/N0, heavier when used in the
 * fix, hollow when not heard) with its number under it.
 *
 * @param heading Where the phone points, in degrees clockwise from north (true north when the
 * GNSS setting asks for it); read only while drawing, so a new heading redraws the plot without
 * recomposing it.
 */
@Composable
fun GnssSkyPlot(sky: SkyState, heading: () -> Float, modifier: Modifier = Modifier) {
    val colors = skyColors()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val northStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val north = stringResource(R.string.sky_north)
    // Laid out once per update, not on every heading redraw (the sensor runs at up to 100 Hz).
    val northLayout = remember(north, northStyle, textMeasurer) { textMeasurer.measure(north, northStyle) }
    val labels = remember(sky.satellites, labelStyle, textMeasurer) {
        sky.satellites.map { textMeasurer.measure(it.svid.toString(), labelStyle) }
    }
    val description = pluralStringResource(
        R.plurals.sky_plot_description, sky.summary.satellites, sky.summary.satellites, sky.summary.used,
    )
    val disk = MaterialTheme.colorScheme.surfaceContainerHigh
    val grid = MaterialTheme.colorScheme.outlineVariant
    val horizon = MaterialTheme.colorScheme.outline

    Canvas(modifier.semantics { contentDescription = description }) {
        val markRadius = SATELLITE_MARK_RADIUS.toPx()
        // Room outside the horizon for the north marker and for labels of satellites near it.
        val radius = (min(size.width, size.height) / 2 - markRadius * 2.5f).coerceAtLeast(0f)
        val rotation = heading()

        drawCircle(disk, radius, center)
        rotate(-rotation, center) {
            drawLine(grid, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), GRID_STROKE.toPx())
            drawLine(grid, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), GRID_STROKE.toPx())
            drawNorthMarker(radius, horizon)
        }
        ELEVATION_RINGS.forEach { elevation ->
            drawCircle(grid, radius * (1 - elevation / 90f), center, style = Stroke(GRID_STROKE.toPx()))
        }
        drawCircle(horizon, radius, center, style = Stroke(HORIZON_STROKE.toPx()))
        drawNorthLabel(northLayout, radius, rotation)

        sky.satellites.forEachIndexed { index, satellite ->
            val point = SkyProjection.project(satellite.elevationDegrees, satellite.azimuthDegrees, rotation)
                ?: return@forEachIndexed
            val at = Offset(center.x + point.x * radius, center.y + point.y * radius)
            val status = when {
                !satellite.inView -> MarkStatus.NOT_IN_VIEW
                satellite.usedInFix -> MarkStatus.USED_IN_FIX
                else -> MarkStatus.IN_VIEW
            }
            drawSatelliteMark(satellite.shape, at, markRadius, status, colors.cn0Color(satellite.cn0DbHz), colors)
            drawLabel(labels[index], Offset(at.x, at.y + markRadius * 1.3f))
        }
    }
}

/** A small triangle on the horizon, pointing out, drawn in the frame rotated to north. */
private fun DrawScope.drawNorthMarker(radius: Float, color: Color) {
    val marker = NORTH_MARKER.toPx()
    val top = center.y - radius
    val path = Path().apply {
        moveTo(center.x, top - marker)
        lineTo(center.x - marker / 2, top)
        lineTo(center.x + marker / 2, top)
        close()
    }
    drawPath(path, color)
}

/** The "N" just inside the horizon at north, kept upright as the plot turns. */
private fun DrawScope.drawNorthLabel(layout: TextLayoutResult, radius: Float, rotation: Float) {
    val inset = radius - layout.size.height
    val point = SkyProjection.project(0f, 360f, rotation) ?: return
    val at = Offset(center.x + point.x * inset, center.y + point.y * inset)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

/** A satellite number centered below its mark, starting at [top]. */
private fun DrawScope.drawLabel(layout: TextLayoutResult, top: Offset) {
    drawText(layout, topLeft = Offset(top.x - layout.size.width / 2f, top.y))
}
