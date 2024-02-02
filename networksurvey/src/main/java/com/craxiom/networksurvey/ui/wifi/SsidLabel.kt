package com.craxiom.networksurvey.ui.wifi

import android.graphics.RectF
import com.patrykandpatrick.vico.core.chart.decoration.Decoration
import com.patrykandpatrick.vico.core.chart.draw.ChartDrawContext
import com.patrykandpatrick.vico.core.component.text.TextComponent
import com.patrykandpatrick.vico.core.component.text.textComponent

/**
 * Draws the SSID name just above the peak of the signal arc.
 */
data class SsidLabel(
    val ssid: String,
    val signalStrength: Int,
    val channel: Int,
    val labelComponent: TextComponent = textComponent(),
) : Decoration {
    override fun onDrawAboveChart(
        context: ChartDrawContext,
        bounds: RectF,
    ): Unit =
        with(context) {
            val yRange = chartValues.getYRange(null)

            val textHeight = labelComponent.getHeight(
                context = context,
                text = ssid,
                rotationDegrees = 0f,
            )

            val xRange = chartValues.maxX - chartValues.minX
            val xPixelPerChannel = chartBounds.width() / xRange
            val xPosition = chartBounds.left + (channel - chartValues.minX) * xPixelPerChannel

            val yPixelPerUnit = chartBounds.height() / yRange.length
            val yPosition =
                chartBounds.bottom - (signalStrength - yRange.minY) * yPixelPerUnit - (textHeight / 2)

            labelComponent.drawText(
                context = context,
                text = ssid,
                maxTextWidth = bounds.width().toInt(),
                textX = xPosition,
                textY = yPosition,
            )
        }
}
