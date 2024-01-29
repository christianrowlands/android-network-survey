package com.craxiom.networksurvey.ui.wifi

import android.graphics.Path
import android.graphics.RectF
import com.patrykandpatrick.vico.core.chart.dimensions.HorizontalDimensions
import com.patrykandpatrick.vico.core.chart.layer.LineCartesianLayer

/**
 * The point connector for the spectrum chart. This point connector rounds out the curve to make
 * each wifi channel look more like one large curve.
 */
class SpectrumPointConnector : LineCartesianLayer.LineSpec.PointConnector {
    override fun connect(
        path: Path,
        prevX: Float,
        prevY: Float,
        x: Float,
        y: Float,
        horizontalDimensions: HorizontalDimensions,
        bounds: RectF,
    ) {
        // for all the WIFI_CHART_MIN points, just draw a straight line
        if (prevY == y) {
            path.lineTo(x, y)
            return
        }

        if (prevY > y) {
            // Left side
            val controlPoint1X = prevX + (x - prevX) * 0.05f
            val controlPoint1Y = prevY + (y - prevY) * 0.5f
            val controlPoint2X = prevX + (x - prevX) * 0.2f
            val controlPoint2Y = prevY + (y - prevY) * 1f

            path.cubicTo(controlPoint1X, controlPoint1Y, controlPoint2X, controlPoint2Y, x, y)
        } else {
            // Right side
            val controlPoint1X = prevX + (x - prevX) * 0.8f
            val controlPoint1Y = prevY + (y - prevY) * 0f
            val controlPoint2X = prevX + (x - prevX) * 1f
            val controlPoint2Y = prevY + (y - prevY) * 0.5f

            path.cubicTo(controlPoint1X, controlPoint1Y, controlPoint2X, controlPoint2Y, x, y)
        }
    }
}
