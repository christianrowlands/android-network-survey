package com.craxiom.networksurvey.ui.wifi

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer

/**
 * The line interpolator for the spectrum chart. This interpolator rounds out the curve to make
 * each wifi channel look more like one large curve.
 */
class SpectrumInterpolator : LineCartesianLayer.Interpolator {
    override fun interpolate(
        context: CartesianDrawingContext,
        path: Path,
        points: List<Offset>,
        visibleIndexRange: IntRange,
    ) {
        if (visibleIndexRange.isEmpty()) return
        val first = points[visibleIndexRange.first]
        path.moveTo(first.x, first.y)
        for (index in visibleIndexRange.first + 1..visibleIndexRange.last) {
            val previous = points[index - 1]
            val current = points[index]
            connect(path, previous.x, previous.y, current.x, current.y)
        }
    }

    private fun connect(path: Path, x1: Float, y1: Float, x2: Float, y2: Float) {
        // for all the WIFI_CHART_MIN points, just draw a straight line
        if (y1 == y2) {
            path.lineTo(x2, y2)
            return
        }

        if (y1 > y2) {
            // Left side
            val controlPoint1X = x1 + (x2 - x1) * 0.05f
            val controlPoint1Y = y1 + (y2 - y1) * 0.5f
            val controlPoint2X = x1 + (x2 - x1) * 0.2f
            val controlPoint2Y = y1 + (y2 - y1) * 1f

            path.cubicTo(controlPoint1X, controlPoint1Y, controlPoint2X, controlPoint2Y, x2, y2)
        } else {
            // Right side
            val controlPoint1X = x1 + (x2 - x1) * 0.8f
            val controlPoint1Y = y1 + (y2 - y1) * 0f
            val controlPoint2X = x1 + (x2 - x1) * 1f
            val controlPoint2Y = y1 + (y2 - y1) * 0.5f

            path.cubicTo(controlPoint1X, controlPoint1Y, controlPoint2X, controlPoint2Y, x2, y2)
        }
    }
}
