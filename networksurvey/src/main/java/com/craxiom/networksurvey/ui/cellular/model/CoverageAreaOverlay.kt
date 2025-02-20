package com.craxiom.networksurvey.ui.cellular.model

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.ui.graphics.toArgb
import com.craxiom.networksurvey.ui.theme.ColorServingCell
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

class CoverageAreaOverlay(
    private val center: GeoPoint,
    private val radius: Int // Radius in meters
) : Overlay() {

    private val paint: Paint = Paint().apply {
        color = ColorServingCell.toArgb()
        strokeWidth = 3f
        isAntiAlias = true
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        val projection: Projection = mapView.projection
        val screenPoint = Point()
        projection.toPixels(center, screenPoint)
        val projectedRadius = projection.metersToPixels(radius.toFloat())

        paint.alpha = 20
        paint.style = Paint.Style.FILL
        canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), projectedRadius, paint)

        paint.alpha = 150
        paint.style = Paint.Style.STROKE
        canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), projectedRadius, paint)
    }
}
