package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import kotlin.math.roundToInt

/**
 * Node for managing a distance text symbol on the map.
 */
internal class DistanceSymbolNode(
    private val symbolManager: SymbolManager,
    startPoint: LatLng,
    endPoint: LatLng,
    distanceMeters: Double,
    textColor: String
) : MapNode {
    // Calculate midpoint
    private val midpoint = LatLng(
        (startPoint.latitude + endPoint.latitude) / 2,
        (startPoint.longitude + endPoint.longitude) / 2
    )

    private val symbol: Symbol = symbolManager.create(
        SymbolOptions().apply {
            withLatLng(midpoint)
            withTextField(formatDistance(distanceMeters))
            withTextFont(arrayOf("Open Sans Bold", "Arial Unicode MS Bold"))
            withTextSize(18f)
            withTextColor(textColor)
            withTextHaloColor("#000000")
            withTextHaloWidth(5f)
            withTextAnchor("center")
            withTextOffset(arrayOf(0f, 0f))
        }
    )

    fun updatePosition(startPoint: LatLng, endPoint: LatLng) {
        val newMidpoint = LatLng(
            (startPoint.latitude + endPoint.latitude) / 2,
            (startPoint.longitude + endPoint.longitude) / 2
        )
        symbol.latLng = newMidpoint
        symbolManager.update(symbol)
    }

    fun updateDistance(distanceMeters: Double) {
        symbol.textField = formatDistance(distanceMeters)
        symbolManager.update(symbol)
    }

    override fun onRemoved() {
        symbolManager.delete(symbol)
    }

    override fun onCleared() {
        symbolManager.delete(symbol)
    }

    companion object {
        /**
         * Formats distance for display.
         */
        fun formatDistance(meters: Double): String {
            return when {
                meters < 1000 -> "${meters.roundToInt()}m"
                else -> {
                    val km = meters / 1000.0
                    if (km < 10) {
                        String.format("%.1fkm", km)
                    } else {
                        "${km.roundToInt()}km"
                    }
                }
            }
        }
    }
}

/**
 * Converts a Compose Color to a hex string format for MapLibre.
 */
private fun colorToHexString(color: Color): String {
    val argb = color.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

/**
 * Displays a distance text symbol at the midpoint of a serving cell line.
 *
 * @param startPoint The starting point (user location)
 * @param endPoint The ending point (tower location)
 * @param distanceMeters The distance between points in meters
 */
@Composable
fun DistanceSymbol(
    startPoint: LatLng,
    endPoint: LatLng,
    distanceMeters: Double
) {
    if (distanceMeters <= 0) return

    val mapApplier = currentComposer.applier as? MapApplier ?: return
    val symbolManager = mapApplier.symbolManager
    val map = mapApplier.map

    // Get the primary color from the theme
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColorHex = colorToHexString(primaryColor)

    // Check if line is long enough on screen to display label
    val screenDistanceVisible = remember(startPoint, endPoint, map.cameraPosition) {
        try {
            val startScreen = map.projection.toScreenLocation(startPoint)
            val endScreen = map.projection.toScreenLocation(endPoint)

            val dx = endScreen.x - startScreen.x
            val dy = endScreen.y - startScreen.y
            val distance = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())

            // Only show if line is at least 100 pixels
            distance >= 100
        } catch (e: Exception) {
            false
        }
    }

    if (!screenDistanceVisible) return

    ComposeNode<DistanceSymbolNode, MapApplier>(
        factory = {
            DistanceSymbolNode(
                symbolManager = symbolManager,
                startPoint = startPoint,
                endPoint = endPoint,
                distanceMeters = distanceMeters,
                textColor = textColorHex
            )
        },
        update = {
            update(Pair(startPoint, endPoint)) { (start, end) ->
                this.updatePosition(start, end)
            }
            update(distanceMeters) {
                this.updateDistance(it)
            }
        }
    )
}