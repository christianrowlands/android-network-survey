package com.craxiom.networksurvey.ui.wifi

import android.graphics.Path
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer

/**
 * The point connector for the spectrum chart. This point connector rounds out the curve to make
 * each wifi channel look more like one large curve.
 */
class SpectrumPointConnector : LineCartesianLayer.PointConnector {
    override fun connect(
        context: CartesianDrawingContext,
        path: Path,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ) {
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