package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class CircleNode(
    val style: Style,
    val polygon: Polygon,
    val fillLayerId: String,
    val strokeLayerId: String,
    val sourceId: String,
    fillColor: Color,
    strokeColor: Color,
    strokeWidth: Float,
    fillOpacity: Float,
    strokeOpacity: Float
) : MapNode {
    
    init {
        val feature = Feature.fromGeometry(polygon)
        val featureCollection = FeatureCollection.fromFeature(feature)
        val source = GeoJsonSource(sourceId, featureCollection)
        style.addSource(source)
        
        // Add fill layer
        val fillLayer = FillLayer(fillLayerId, sourceId).apply {
            setProperties(
                PropertyFactory.fillColor(fillColor.toArgb()),
                PropertyFactory.fillOpacity(fillOpacity)
            )
        }
        style.addLayer(fillLayer)
        
        // Add stroke layer
        val strokeLayer = LineLayer(strokeLayerId, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(strokeColor.toArgb()),
                PropertyFactory.lineWidth(strokeWidth),
                PropertyFactory.lineOpacity(strokeOpacity)
            )
        }
        style.addLayer(strokeLayer)
    }

    override fun onRemoved() {
        style.removeLayer(fillLayerId)
        style.removeLayer(strokeLayerId)
        style.removeSource(sourceId)
    }

    override fun onCleared() {
        style.removeLayer(fillLayerId)
        style.removeLayer(strokeLayerId)
        style.removeSource(sourceId)
    }
}

/**
 * A state object that can be hoisted to control and observe the circle state.
 */
class CircleState(
    center: LatLng,
    radiusMeters: Int,
    fillColor: Color = Color.Blue.copy(alpha = 0.08f),
    strokeColor: Color = Color.Blue.copy(alpha = 0.6f),
    strokeWidth: Float = 3f
) {
    /**
     * Center of the circle.
     */
    var center: LatLng by mutableStateOf(center)
    
    /**
     * Radius in meters.
     */
    var radiusMeters: Int by mutableIntStateOf(radiusMeters)
    
    /**
     * Fill color of the circle.
     */
    var fillColor: Color by mutableStateOf(fillColor)
    
    /**
     * Stroke color of the circle.
     */
    var strokeColor: Color by mutableStateOf(strokeColor)
    
    /**
     * Stroke width.
     */
    var strokeWidth: Float by mutableFloatStateOf(strokeWidth)

    companion object {
        /**
         * The default saver implementation for [CircleState].
         */
        val Saver: Saver<CircleState, List<Any>> = Saver(
            save = { listOf(it.center, it.radiusMeters, it.fillColor, it.strokeColor, it.strokeWidth) },
            restore = { 
                CircleState(
                    it[0] as LatLng,
                    it[1] as Int,
                    it[2] as Color,
                    it[3] as Color,
                    it[4] as Float
                )
            }
        )
    }
}

@Composable
fun rememberCircleState(
    key: String? = null,
    center: LatLng = LatLng(0.0, 0.0),
    radiusMeters: Int = 1000,
    fillColor: Color = Color.Blue.copy(alpha = 0.08f),
    strokeColor: Color = Color.Blue.copy(alpha = 0.6f),
    strokeWidth: Float = 3f
): CircleState = rememberSaveable(key = key, saver = CircleState.Saver) {
    CircleState(center, radiusMeters, fillColor, strokeColor, strokeWidth)
}

/**
 * A composable for a circle on the map.
 *
 * @param state the [CircleState] to be used to control or observe the circle state
 */
@Composable
fun Circle(
    state: CircleState = rememberCircleState()
) {
    val mapApplier = currentComposer.applier as MapApplier
    val style = mapApplier.style
    
    val fillLayerId = "circle-fill-layer-${state.hashCode()}"
    val strokeLayerId = "circle-stroke-layer-${state.hashCode()}"
    val sourceId = "circle-source-${state.hashCode()}"
    
    // Create a circle polygon from center and radius
    val polygon = createCirclePolygon(state.center, state.radiusMeters)
    
    ComposeNode<CircleNode, MapApplier>(
        factory = {
            CircleNode(
                style = style,
                polygon = polygon,
                fillLayerId = fillLayerId,
                strokeLayerId = strokeLayerId,
                sourceId = sourceId,
                fillColor = state.fillColor,
                strokeColor = state.strokeColor,
                strokeWidth = state.strokeWidth,
                fillOpacity = state.fillColor.alpha,
                strokeOpacity = state.strokeColor.alpha
            )
        },
        update = {
            // For now, we recreate the circle when properties change
            // A more sophisticated implementation would update the existing source/layer
        }
    )
}

/**
 * Create a polygon that approximates a circle.
 */
private fun createCirclePolygon(center: LatLng, radiusMeters: Int): Polygon {
    val points = mutableListOf<Point>()
    val numPoints = 64 // Number of points to approximate the circle
    
    // Convert radius from meters to degrees (rough approximation)
    val radiusInDegrees = radiusMeters / 111320.0 // 1 degree ≈ 111,320 meters at equator
    
    for (i in 0 until numPoints) {
        val angle = 2 * PI * i / numPoints
        val lat = center.latitude + radiusInDegrees * cos(angle)
        val lng = center.longitude + radiusInDegrees * sin(angle) / cos(Math.toRadians(center.latitude))
        points.add(Point.fromLngLat(lng, lat))
    }
    
    // Close the polygon by adding the first point again
    points.add(points[0])
    
    return Polygon.fromLngLats(listOf(points))
}