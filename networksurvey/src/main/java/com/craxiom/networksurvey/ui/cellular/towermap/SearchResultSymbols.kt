package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import com.craxiom.networksurvey.data.api.Tower
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

const val SEARCH_TOWER_LAYER_KEY = "search-tower-layer"
const val KEY_SEARCH_TOWER_ICON = "tower-search"

internal class SearchResultSymbolsNode(
    private val style: Style,
    private val sourceId: String,
    private val layerId: String,
    initialTower: Tower?,
    private val iconKey: String,
) : MapNode {
    private val source = GeoJsonSource(sourceId, FeatureCollection.fromFeatures(emptyArray()))
    private val layer = SymbolLayer(layerId, sourceId)

    init {
        style.addSource(source)

        style.addLayer(
            layer.withProperties(
                iconImage(iconKey),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )
        )

        // Populate with the initial tower if provided
        updateData(initialTower)
    }

    /**
     * Updates the search result tower on the map.
     */
    fun updateData(tower: Tower?) {
        val features = if (tower != null) {
            listOf(
                Feature.fromGeometry(Point.fromLngLat(tower.lon, tower.lat)).apply {
                    addStringProperty("radio", tower.radio)
                    addNumberProperty("mcc", tower.mcc)
                    addNumberProperty("mnc", tower.mnc)
                    addNumberProperty("area", tower.area)
                    addNumberProperty("cid", tower.cid)
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
            )
        } else {
            emptyList()
        }

        source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
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

    override fun onCleared() = onRemoved()
}

@Composable
fun SearchResultSymbols(
    searchedTower: Tower?,
    iconKey: String = KEY_SEARCH_TOWER_ICON,
) {
    val mapApplier = currentComposer.applier as MapApplier
    val style = mapApplier.style

    ComposeNode<SearchResultSymbolsNode, MapApplier>(
        factory = {
            SearchResultSymbolsNode(
                style = style,
                sourceId = "search-tower-source",
                layerId = SEARCH_TOWER_LAYER_KEY,
                initialTower = searchedTower,
                iconKey = iconKey,
            )
        },
        update = {
            // when searchedTower changes, update the display
            set(searchedTower) { newTower ->
                updateData(newTower)
            }
        }
    )
}