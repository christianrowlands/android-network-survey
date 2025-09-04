package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.craxiom.networksurvey.ui.cellular.model.SERVING_CELL_LINE_LAYER_PREFIX
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Internal node that adds/removes the line layer and source, and can update its geometry.
 */
internal class LineStringNode(
    private val style: Style,
    private val layerId: String,
    private val sourceId: String,
    lineString: LineString,
    color: Color,
    width: Float,
    dashArray: List<Float>?,
    symbolLayerId: String? = null
) : MapNode {
    init {
        // Create source
        style.addSource(
            GeoJsonSource(
                sourceId, FeatureCollection.fromFeatures(
                    arrayOf(Feature.fromGeometry(lineString))
                )
            )
        )
        // Create layer
        val lineLayer = LineLayer(layerId, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(color.toArgb()),
                PropertyFactory.lineWidth(width)
            )
            dashArray?.let { dash ->
                setProperties(PropertyFactory.lineDasharray(dash.toTypedArray()))
            }
        }

        // Add layer below the symbol layer if specified, otherwise add normally
        if (symbolLayerId != null) {
            try {
                style.addLayerBelow(lineLayer, symbolLayerId)
            } catch (e: Exception) {
                // Fallback to normal addLayer if the symbol layer doesn't exist yet
                style.addLayer(lineLayer)
            }
        } else {
            style.addLayer(lineLayer)
        }
    }

    /**
     * Replace the GeoJSON of the source with new geometry.
     */
    fun updateGeometry(lineString: LineString) {
        (style.getSource(sourceId) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(lineString)))
        )
    }

    override fun onRemoved() {
        try {
            style.removeLayer(layerId)
        } catch (_: Exception) {
        }
        try {
            style.removeSource(sourceId)
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        try {
            style.removeLayer(layerId)
        } catch (_: Exception) {
        }
        try {
            style.removeSource(sourceId)
        } catch (_: Exception) {
        }
    }
}

/**
 * State holder for a line's geometry and styling.
 */
class LineStringState(
    initialPoints: List<LatLng>,
    initialColor: Color = Color.Blue,
    initialWidth: Float = 4f,
    initialDashArray: List<Float>? = null
) {
    var points by mutableStateOf(initialPoints)
    var color by mutableStateOf(initialColor)
    var width by mutableFloatStateOf(initialWidth)
    var dashArray by mutableStateOf(initialDashArray)
}

/**
 * Remember a LineStringState in Compose.
 */
@Composable
fun rememberLineStringState(
    points: List<LatLng> = emptyList(),
    color: Color = Color.Blue,
    width: Float = 4f,
    dashArray: List<Float>? = null
): LineStringState = remember(points, color, width, dashArray) {
    LineStringState(points, color, width, dashArray)
}

/**
 * Draws a line on the map, updating geometry when points change.
 */
@Composable
fun LineString(
    state: LineStringState
) {
    val mapApplier = currentComposer.applier as MapApplier
    val style = mapApplier.style
    val symbolManager = mapApplier.symbolManager

    // Need at least two points
    if (state.points.size < 2) return

    // Unique IDs per state instance
    val layerId = "${SERVING_CELL_LINE_LAYER_PREFIX}${state.hashCode()}"
    val sourceId = "line-source-${state.hashCode()}"

    // Build initial LineString
    val coords = state.points.map { Point.fromLngLat(it.longitude, it.latitude) }
    val lineString = LineString.fromLngLats(coords)

    // Get the symbol manager's layer ID to ensure lines are drawn below symbols
    val symbolLayerId = try {
        symbolManager.layerId
    } catch (e: Exception) {
        null
    }

    ComposeNode<LineStringNode, MapApplier>(
        factory = {
            LineStringNode(
                style = style,
                layerId = layerId,
                sourceId = sourceId,
                lineString = lineString,
                color = state.color,
                width = state.width,
                dashArray = state.dashArray,
                symbolLayerId = symbolLayerId
            )
        },
        update = {
            set(state.points) { newPoints ->
                val newCoords = newPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
                val newLine = LineString.fromLngLats(newCoords)
                this.updateGeometry(newLine)
            }
        }
    )
}
