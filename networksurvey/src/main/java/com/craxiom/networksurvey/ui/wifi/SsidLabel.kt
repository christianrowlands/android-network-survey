package com.craxiom.networksurvey.ui.wifi

import com.craxiom.networksurvey.ui.wifi.model.WIFI_SPECTRUM_MAX
import com.craxiom.networksurvey.ui.wifi.model.WIFI_SPECTRUM_MIN
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.common.component.TextComponent

/**
 * Draws the SSID name just above the peak of the signal arc.
 */
data class SsidLabel(
    val ssid: String,
    val signalStrength: Int,
    val channel: Int,
    val labelComponent: TextComponent,
    val minX: Float,
    val maxX: Float,
) : Decoration {
    override fun drawOverLayers(context: CartesianDrawingContext) {
        val bounds = context.layerBounds

        // Calculate x position based on channel mapping to chart coordinates
        val xRange = maxX - minX
        val xPixelPerUnit = bounds.width() / xRange
        val xPosition = bounds.left + ((channel - minX) * xPixelPerUnit)

        // Calculate y position based on signal strength mapping to chart coordinates
        val yRange = WIFI_SPECTRUM_MAX - WIFI_SPECTRUM_MIN
        val yPixelPerUnit = bounds.height() / yRange
        // Chart y-axis is inverted (top is max, bottom is min)
        val yPosition = bounds.bottom - ((signalStrength - WIFI_SPECTRUM_MIN) * yPixelPerUnit)

        // Draw label above the signal peak
        labelComponent.draw(
            context = context,
            text = ssid,
            x = xPosition,
            y = yPosition - 20f, // Offset above the peak
        )
    }
}