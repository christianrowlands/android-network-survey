package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import com.craxiom.networksurvey.ui.cellular.model.TowerWrapper
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

const val TOWER_LAYER_KEY = "tower-layer"
const val KEY_SERVING_CELL_ICON = "tower-serving"
const val KEY_TOWER_ICON = "tower"
private const val TOWER_ID_PROPERTY = "towerId"

internal class TowerSymbolsNode(
    private val style: Style,
    private val sourceId: String,
    private val layerId: String,
    initialTowers: List<TowerWrapper>,
    initialServingIds: Set<String>,
    private val normalIcon: String,
    private val servingIcon: String,
) : MapNode {
    private val source = GeoJsonSource(sourceId, FeatureCollection.fromFeatures(emptyArray()))
    private val layer = SymbolLayer(layerId, sourceId)

    init {
        style.addSource(source)

        style.addLayer(
            layer.withProperties(
                iconImage(normalIcon),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )
        )

        // Populate with the initial batch of data
        updateData(initialTowers, initialServingIds)
    }

    /**
     * Rebuild both the GeoJSON source and the data‐driven style expression
     * every time towers or servingIds change.
     */
    fun updateData(towers: List<TowerWrapper>, servingIds: Set<String>) {
        // 1) rebuild the GeoJSON FeatureCollection
        val features = towers.map { towerWrapper ->
            val tower = towerWrapper.tower
            Feature.fromGeometry(Point.fromLngLat(tower.lon, tower.lat)).apply {
                addStringProperty(TOWER_ID_PROPERTY, towerWrapper.towerId)
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
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))

        // 2) Recompute the “match” expressions so that any tower whose ID is in servingIds
        //    gets the “serving” icon, otherwise all others get the “normal” value.
        layer.setProperties(
            iconImage(
                match(
                    get(TOWER_ID_PROPERTY),
                    *servingIds.flatMap { id ->
                        listOf(
                            literal(id),          // match-value: a tower ID
                            literal(servingIcon)  // output-value: “tower-serving”
                        )
                    }.toTypedArray(),
                    literal(normalIcon)    // if no match => “tower”
                )
            ),
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

    override fun onCleared() = onRemoved()
}

@Composable
fun TowerSymbols(
    towerWrapperList: List<TowerWrapper>,
    servingIds: Set<String>,
    normalIcon: String = KEY_TOWER_ICON,
    servingIcon: String = KEY_SERVING_CELL_ICON,
) {
    val mapApplier = currentComposer.applier as MapApplier
    val style = mapApplier.style

    ComposeNode<TowerSymbolsNode, MapApplier>(
        factory = {
            TowerSymbolsNode(
                style = style,
                sourceId = "tower-source",
                layerId = TOWER_LAYER_KEY,
                initialTowers = towerWrapperList,
                initialServingIds = servingIds,
                normalIcon = normalIcon,
                servingIcon = servingIcon,
            )
        },
        update = {
            // when `towers` changes, call updateData(newTowers, current servingIds)
            set(towerWrapperList) { newTowers ->
                updateData(newTowers, servingIds)
            }
            // when `servingIds` changes, call updateData(current towers, newServingIds)
            set(servingIds) { newServing ->
                updateData(towerWrapperList, newServing)
            }
        }
    )
}
