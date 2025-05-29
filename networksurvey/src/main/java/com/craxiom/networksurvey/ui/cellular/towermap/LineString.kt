package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

internal class LineStringNode(
    val style: Style,
    val lineString: LineString,
    val layerId: String,
    val sourceId: String,
    color: Color,
    width: Float,
    dashArray: List<Float>?
) : MapNode {
    
    init {
        val feature = Feature.fromGeometry(lineString)
        val featureCollection = FeatureCollection.fromFeature(feature)
        val source = GeoJsonSource(sourceId, featureCollection)
        style.addSource(source)
        
        val lineLayer = LineLayer(layerId, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(color.toArgb()),
                PropertyFactory.lineWidth(width)
            )
            dashArray?.let { dash ->
                setProperties(PropertyFactory.lineDasharray(dash.toTypedArray()))
            }
        }
        style.addLayer(lineLayer)
    }

    override fun onRemoved() {
        // Safely remove layer and source; may occur during style reload
        try {
            style.removeLayer(layerId)
        } catch (_: Exception) {
            // ignore
        }
        try {
            style.removeSource(sourceId)
        } catch (_: Exception) {
            // ignore
        }
    }

    override fun onCleared() {
        // Safely remove layer and source
        try {
            style.removeLayer(layerId)
        } catch (_: Exception) {
            // ignore
        }
        try {
            style.removeSource(sourceId)
        } catch (_: Exception) {
            // ignore
        }
    }
}

/**
 * A state object that can be hoisted to control and observe the line state.
 */
class LineStringState(
    points: List<LatLng>,
    color: Color = Color.Blue,
    width: Float = 4f,
    dashArray: List<Float>? = null
) {
    /**
     * Current points of the line.
     */
    var points: List<LatLng> by mutableStateOf(points)
    
    /**
     * Color of the line.
     */
    var color: Color by mutableStateOf(color)
    
    /**
     * Width of the line.
     */
    var width: Float by mutableFloatStateOf(width)
    
    /**
     * Dash array for dashed lines.
     */
    var dashArray: List<Float>? by mutableStateOf(dashArray)

    companion object {
        /**
         * The default saver implementation for [LineStringState].
         */
        val Saver: Saver<LineStringState, List<Any>> = Saver(
            save = { listOf(it.points, it.color, it.width, it.dashArray ?: emptyList<Float>()) },
            restore = { 
                @Suppress("UNCHECKED_CAST")
                LineStringState(
                    it[0] as List<LatLng>,
                    it[1] as Color,
                    it[2] as Float,
                    (it[3] as List<Float>).takeIf { dash -> dash.isNotEmpty() }
                )
            }
        )
    }
}

@Composable
fun rememberLineStringState(
    key: String? = null,
    points: List<LatLng> = emptyList(),
    color: Color = Color.Blue,
    width: Float = 4f,
    dashArray: List<Float>? = null
): LineStringState = rememberSaveable(key = key, saver = LineStringState.Saver) {
    LineStringState(points, color, width, dashArray)
}

/**
 * A composable for a line on the map.
 *
 * @param state the [LineStringState] to be used to control or observe the line state
 */
@Composable
fun LineString(
    state: LineStringState = rememberLineStringState()
) {
    val mapApplier = currentComposer.applier as MapApplier
    val style = mapApplier.style
    
    if (state.points.size < 2) return // Need at least 2 points for a line
    
    val layerId = "line-layer-${state.hashCode()}"
    val sourceId = "line-source-${state.hashCode()}"
    
    val coordinates = state.points.map { 
        Point.fromLngLat(it.longitude, it.latitude)
    }
    val lineString = LineString.fromLngLats(coordinates)
    
    ComposeNode<LineStringNode, MapApplier>(
        factory = {
            LineStringNode(
                style = style,
                lineString = lineString,
                layerId = layerId,
                sourceId = sourceId,
                color = state.color,
                width = state.width,
                dashArray = state.dashArray
            )
        },
        update = {
            // For now, we recreate the line when properties change
            // A more sophisticated implementation would update the existing source/layer
        }
    )
}