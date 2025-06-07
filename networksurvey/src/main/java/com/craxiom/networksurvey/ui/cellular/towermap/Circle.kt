package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.craxiom.networksurvey.ui.cellular.model.SERVING_CELL_COVERAGE_FILL_LAYER_PREFIX
import com.craxiom.networksurvey.ui.cellular.model.SERVING_CELL_COVERAGE_OUTLINE_LAYER_PREFIX
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

/**
 * Internal node that manages adding/removing a filled circle + stroke layer.
 */
internal class CircleNode(
    private val style: Style,
    private val sourceId: String,
    private val fillLayerId: String,
    private val strokeLayerId: String,
    polygon: Polygon,
    fillColor: Color,
    strokeColor: Color,
    strokeWidth: Float,
    fillOpacity: Float,
    strokeOpacity: Float
) : MapNode {
    private var source: GeoJsonSource? = null
    private var fillLayer: FillLayer? = null
    private var strokeLayer: LineLayer? = null

    init {
        // Add source
        source = GeoJsonSource(
            sourceId, FeatureCollection.fromFeatures(
                arrayOf(Feature.fromGeometry(polygon))
            )
        )
        style.addSource(source!!)

        // Fill layer
        fillLayer = FillLayer(fillLayerId, sourceId).apply {
            setProperties(
                PropertyFactory.fillColor(fillColor.toArgb()),
                PropertyFactory.fillOpacity(fillOpacity)
            )
        }
        style.addLayer(fillLayer!!)

        // Stroke layer
        strokeLayer = LineLayer(strokeLayerId, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(strokeColor.toArgb()),
                PropertyFactory.lineWidth(strokeWidth),
                PropertyFactory.lineOpacity(strokeOpacity)
            )
        }
        style.addLayer(strokeLayer!!)
    }

    fun updateGeometry(polygon: Polygon) {
        source?.setGeoJson(
            FeatureCollection.fromFeatures(
                arrayOf(Feature.fromGeometry(polygon))
            )
        )
    }

    fun updateFillColor(color: Color, opacity: Float) {
        fillLayer?.setProperties(
            PropertyFactory.fillColor(color.toArgb()),
            PropertyFactory.fillOpacity(opacity)
        )
    }

    fun updateStroke(color: Color, width: Float, opacity: Float) {
        strokeLayer?.setProperties(
            PropertyFactory.lineColor(color.toArgb()),
            PropertyFactory.lineWidth(width),
            PropertyFactory.lineOpacity(opacity)
        )
    }

    override fun onRemoved() {
        try {
            style.removeLayer(strokeLayerId)
        } catch (_: Exception) {
        }
        try {
            style.removeLayer(fillLayerId)
        } catch (_: Exception) {
        }
        try {
            style.removeSource(sourceId)
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        try {
            style.removeLayer(strokeLayerId)
        } catch (_: Exception) {
        }
        try {
            style.removeLayer(fillLayerId)
        } catch (_: Exception) {
        }
        try {
            style.removeSource(sourceId)
        } catch (_: Exception) {
        }
    }
}

/**
 * State holder for a circle's center, radius, and styling.
 * Purely in-memory; not persisted across recompositions.
 */
class CircleState(
    initialCenter: LatLng,
    initialRadiusMeters: Int,
    initialFillColor: Color = Color.Transparent,
    initialStrokeColor: Color = Color.Blue.copy(alpha = 0.6f),
    initialStrokeWidth: Float = 3f
) {
    var center by mutableStateOf(initialCenter)
    var radiusMeters by mutableIntStateOf(initialRadiusMeters)
    var fillColor by mutableStateOf(initialFillColor)
    var strokeColor by mutableStateOf(initialStrokeColor)
    var strokeWidth by mutableFloatStateOf(initialStrokeWidth)
}

/**
 * Remember a CircleState for use in Compose. State resets if inputs change.
 */
@Composable
fun rememberCircleState(
    center: LatLng = LatLng(0.0, 0.0),
    radiusMeters: Int = 1000,
    fillColor: Color = Color.Blue.copy(alpha = 0.08f),
    strokeColor: Color = Color.Blue.copy(alpha = 0.6f),
    strokeWidth: Float = 3f
): CircleState = remember(center, radiusMeters, fillColor, strokeColor, strokeWidth) {
    CircleState(center, radiusMeters, fillColor, strokeColor, strokeWidth)
}

/**
 * Draws the circle on MapLibre via ComposeNode using CircleNode.
 */
@Composable
fun Circle(
    state: CircleState
) {
    val mapApplier = currentComposer.applier as MapApplier
    val style = mapApplier.style

    // Unique IDs per state instance
    val sourceId = "circle-source-${state.hashCode()}"
    val fillLayerId = "$SERVING_CELL_COVERAGE_FILL_LAYER_PREFIX${state.hashCode()}"
    val strokeLayerId = "$SERVING_CELL_COVERAGE_OUTLINE_LAYER_PREFIX${state.hashCode()}"

    // Build polygon approximation
    val polygon = createCirclePolygon(state.center, state.radiusMeters)

    ComposeNode<CircleNode, MapApplier>(
        factory = {
            CircleNode(
                style = style,
                sourceId = sourceId,
                fillLayerId = fillLayerId,
                strokeLayerId = strokeLayerId,
                polygon = polygon,
                fillColor = state.fillColor,
                strokeColor = state.strokeColor,
                strokeWidth = state.strokeWidth,
                fillOpacity = state.fillColor.alpha,
                strokeOpacity = state.strokeColor.alpha
            )
        },
        update = {
            // Update geometry when center or radius changes
            set(polygon) { updateGeometry(it) }

            // Update fill color and opacity  
            set(state.fillColor) { updateFillColor(it, state.fillColor.alpha) }

            // Update stroke color
            set(state.strokeColor) { updateStroke(it, state.strokeWidth, it.alpha) }

            // Update stroke width
            set(state.strokeWidth) { updateStroke(state.strokeColor, it, state.strokeColor.alpha) }
        }
    )
}

/**
 * Create a geojson Polygon approximating a circle.
 */
private fun createCirclePolygon(center: LatLng, radiusMeters: Int): Polygon {
    val points = mutableListOf<Point>()
    val segments = 64
    val radiusDeg = radiusMeters / 111320.0

    repeat(segments) { i ->
        val theta = 2 * PI * i / segments
        val lat = center.latitude + radiusDeg * cos(theta)
        val lon = center.longitude + radiusDeg * sin(theta) / cos(Math.toRadians(center.latitude))
        points += Point.fromLngLat(lon, lat)
    }
    points += points.first()
    return Polygon.fromLngLats(listOf(points))
}
