package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.craxiom.networksurvey.ui.cellular.Tower
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Node that manages a single source+layer for all tower symbols.
 */
internal class TowerSymbolsNode(
    private val style: Style,
    private val sourceId: String,
    private val layerId: String,
    towers: List<Tower>
) : MapNode {
    init {
        val features = towers.map { tower ->
            Feature.fromGeometry(Point.fromLngLat(tower.lon, tower.lat)).apply {
                addStringProperty("towerId", tower.cid.toString())
                addStringProperty("radio", tower.radio)
                addNumberProperty("mcc", tower.mcc)
                addNumberProperty("mnc", tower.mnc)
                addNumberProperty("area", tower.area)
                addNumberProperty("unit", tower.unit)
                addNumberProperty("range", tower.range)
                addNumberProperty("samples", tower.samples)
                addNumberProperty("averageSignal", tower.averageSignal)
                addNumberProperty("changeable", tower.changeable)
                addNumberProperty("createdAt", tower.createdAt)
                addNumberProperty("updatedAt", tower.updatedAt)
                addStringProperty("source", tower.source)
                addNumberProperty("lat", tower.lat)
                addNumberProperty("lon", tower.lon)
            }
        }
        style.addSource(
            GeoJsonSource(
                sourceId,
                FeatureCollection.fromFeatures(features.toTypedArray())
            )
        )
        style.addLayer(SymbolLayer(layerId, sourceId).apply {
            withProperties(
                iconImage("tower"),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )
        })
    }

    /**
     * Update the GeoJSON source with new towers.
     */
    fun updateTowers(newTowers: List<Tower>) {
        val newFeatures = newTowers.map { tower ->
            Feature.fromGeometry(Point.fromLngLat(tower.lon, tower.lat)).apply {
                addStringProperty("towerId", tower.cid.toString())
                addStringProperty("radio", tower.radio)
                addNumberProperty("mcc", tower.mcc)
                addNumberProperty("mnc", tower.mnc)
                addNumberProperty("area", tower.area)
                addNumberProperty("unit", tower.unit)
                addNumberProperty("range", tower.range)
                addNumberProperty("samples", tower.samples)
                addNumberProperty("averageSignal", tower.averageSignal)
                addNumberProperty("changeable", tower.changeable)
                addNumberProperty("createdAt", tower.createdAt)
                addNumberProperty("updatedAt", tower.updatedAt)
                addStringProperty("source", tower.source)
                addNumberProperty("lat", tower.lat)
                addNumberProperty("lon", tower.lon)
            }
        }
        (style.getSource(sourceId) as? GeoJsonSource)
            ?.setGeoJson(FeatureCollection.fromFeatures(newFeatures.toTypedArray()))
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
 * A state holder for towers.
 */
class TowerSymbolsState(
    initialTowers: List<Tower>
) {
    var towers by mutableStateOf(initialTowers)
}

/**
 * Remember tower symbols state in Compose.
 */
@Composable
fun rememberTowerSymbolsState(
    towers: List<Tower>
): TowerSymbolsState = remember(towers) {
    TowerSymbolsState(towers)
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
                towers = state.towers
            )
        },
        update = {
            set(state.towers) { newTowers ->
                this.updateTowers(newTowers)
            }
        }
    )
}
