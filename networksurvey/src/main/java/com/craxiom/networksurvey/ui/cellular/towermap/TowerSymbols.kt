package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng

/**
 * Node that manages a single source+layer for all tower symbols.
 */
internal class TowerSymbolsNode(
    private val style: Style,
    private val sourceId: String,
    private val layerId: String,
    points: List<LatLng>
) : MapNode {
    init {
        val features = points.map { latLng ->
            Feature.fromGeometry(Point.fromLngLat(latLng.longitude, latLng.latitude))
        }
        style.addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeatures(features.toTypedArray())))
        style.addLayer(SymbolLayer(layerId, sourceId).apply {
            withProperties(
                iconImage("tower"),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )
        })
    }

    /**
     * Update the GeoJSON source with new tower points.
     */
    fun updatePoints(newPoints: List<LatLng>) {
        val features = newPoints.map { latLng ->
            Feature.fromGeometry(Point.fromLngLat(latLng.longitude, latLng.latitude))
        }
        (style.getSource(sourceId) as? GeoJsonSource)
            ?.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
    }

    override fun onRemoved() {
        try { style.removeLayer(layerId) } catch (_: Exception) {}
        try { style.removeSource(sourceId) } catch (_: Exception) {}
    }

    override fun onCleared() {
        try { style.removeLayer(layerId) } catch (_: Exception) {}
        try { style.removeSource(sourceId) } catch (_: Exception) {}
    }
}

/**
 * A state holder for tower positions.
 */
class TowerSymbolsState(
    initialPoints: List<LatLng>
) {
    var points by mutableStateOf(initialPoints)
}

/**
 * Remember tower symbols state in Compose.
 */
@Composable
fun rememberTowerSymbolsState(
    points: List<LatLng>
): TowerSymbolsState = remember(points) {
    TowerSymbolsState(points)
}

/**
 * Composable that renders all towers via a single GeoJson source + SymbolLayer.
 */
@Composable
fun TowerSymbols(
    state: TowerSymbolsState
) {
    val mapApplier = currentComposer.applier as MapApplier
    val style = mapApplier.style

    // Unique IDs for this screen
    val sourceId = "tower-symbols-source"
    val layerId = "tower-symbols-layer"

    ComposeNode<TowerSymbolsNode, MapApplier>(
        factory = {
            TowerSymbolsNode(
                style = style,
                sourceId = sourceId,
                layerId = layerId,
                points = state.points
            )
        },
        update = {
            set(state.points) { newPoints ->
                this.updatePoints(newPoints)
            }
        }
    )
}
