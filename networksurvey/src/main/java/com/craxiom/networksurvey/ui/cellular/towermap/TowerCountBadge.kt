package com.craxiom.networksurvey.ui.cellular.towermap

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import com.craxiom.networksurvey.ui.cellular.model.TowerWrapper
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val COUNT_BADGE_LAYER_KEY = "tower-count-badge-layer"
private const val COUNT_BADGE_SOURCE_KEY = "tower-count-badge-source"

/**
 * A MapNode that renders a count badge on top of tower icons when 2+ towers share the same
 * exact coordinates. This helps users identify multi-tower locations on the map.
 */
internal class TowerCountBadgeNode(
    private val style: Style,
    private val sourceId: String,
    private val layerId: String,
    initialTowers: List<TowerWrapper>,
) : MapNode {
    private val source = GeoJsonSource(sourceId, FeatureCollection.fromFeatures(emptyArray()))
    private val layer = SymbolLayer(layerId, sourceId)

    init {
        style.addSource(source)

        style.addLayer(
            layer.withProperties(
                textField(get("count")),
                textSize(11f),
                textColor(Color.WHITE),
                textHaloColor(Color.parseColor("#CC333333")),
                textHaloWidth(1.5f),
                textOffset(arrayOf(0.8f, -0.8f)),
                textAllowOverlap(true),
                textFont(arrayOf("Open Sans Bold"))
            )
        )

        updateData(initialTowers)
    }

    /**
     * Groups towers by exact (lat, lon), filters to locations with 2+ towers,
     * and creates one text feature per group.
     */
    fun updateData(towers: List<TowerWrapper>) {
        val grouped = towers.groupBy { Pair(it.tower.lat, it.tower.lon) }
        val features = grouped
            .filter { it.value.size >= 2 }
            .map { (coords, group) ->
                Feature.fromGeometry(Point.fromLngLat(coords.second, coords.first)).apply {
                    addStringProperty("count", group.size.toString())
                }
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

/**
 * Composable that renders count badges on the map for locations where multiple towers share
 * exact coordinates. Only displays badges for locations with 2 or more towers.
 */
@Composable
fun TowerCountBadge(
    towerWrapperList: List<TowerWrapper>,
) {
    val mapApplier = currentComposer.applier as MapApplier
    val style = mapApplier.style

    ComposeNode<TowerCountBadgeNode, MapApplier>(
        factory = {
            TowerCountBadgeNode(
                style = style,
                sourceId = COUNT_BADGE_SOURCE_KEY,
                layerId = COUNT_BADGE_LAYER_KEY,
                initialTowers = towerWrapperList,
            )
        },
        update = {
            set(towerWrapperList) { newTowers ->
                updateData(newTowers)
            }
        }
    )
}
