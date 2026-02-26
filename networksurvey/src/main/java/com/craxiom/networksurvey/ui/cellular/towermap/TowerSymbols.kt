package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import com.craxiom.networksurvey.ui.cellular.model.TowerWrapper
import com.craxiom.networksurvey.util.PlmnColorMapper
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconColor
import org.maplibre.android.style.layers.PropertyFactory.iconHaloColor
import org.maplibre.android.style.layers.PropertyFactory.iconHaloWidth
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
private const val PLMN_PROPERTY = "plmn"

internal class TowerSymbolsNode(
    private val style: Style,
    private val sourceId: String,
    private val layerId: String,
    initialTowers: List<TowerWrapper>,
    initialServingIds: Set<String>,
    private val normalIcon: String,
    private val servingIcon: String,
    private var isDarkMap: Boolean,
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
     * Rebuild both the GeoJSON source and the data-driven style expression
     * every time towers or servingIds change.
     */
    fun updateData(towers: List<TowerWrapper>, servingIds: Set<String>) {
        // 1) Collect unique PLMNs for the color match expression
        val uniquePlmns = mutableSetOf<String>()

        // 2) Rebuild the GeoJSON FeatureCollection
        val features = towers.map { towerWrapper ->
            val tower = towerWrapper.tower
            val plmn = "${tower.mcc}-${tower.mnc}"
            uniquePlmns.add(plmn)

            Feature.fromGeometry(Point.fromLngLat(tower.lon, tower.lat)).apply {
                addStringProperty(TOWER_ID_PROPERTY, towerWrapper.towerId)
                addStringProperty(PLMN_PROPERTY, plmn)
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
                tower.comments?.let { addStringProperty("comments", it) }
                addNumberProperty("lat", tower.lat)
                addNumberProperty("lon", tower.lon)
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))

        // 3) Build the PLMN -> color match expression
        val plmnColorPairs = uniquePlmns.flatMap { plmn ->
            val parts = plmn.split("-")
            val mcc = parts[0].toIntOrNull() ?: 0
            val mnc = parts[1].toIntOrNull() ?: 0
            listOf(literal(plmn), color(PlmnColorMapper.getColorArgb(mcc, mnc)))
        }

        // 4) Set all layer properties: icon selection, SDF color, and halo
        layer.setProperties(
            iconImage(
                match(
                    get(TOWER_ID_PROPERTY),
                    *servingIds.flatMap { id ->
                        listOf(
                            literal(id),
                            literal(servingIcon)
                        )
                    }.toTypedArray(),
                    literal(normalIcon)
                )
            ),
            iconColor(
                if (plmnColorPairs.isNotEmpty()) {
                    match(
                        get(PLMN_PROPERTY),
                        *plmnColorPairs.toTypedArray(),
                        color(android.graphics.Color.GRAY)
                    )
                } else {
                    color(android.graphics.Color.GRAY)
                }
            ),
            iconHaloColor(
                if (isDarkMap) "rgba(255,255,255,0.8)" else "rgba(0,0,0,0.3)"
            ),
            iconHaloWidth(1.5f),
        )
    }

    /**
     * Updates the halo color based on the current map theme without rebuilding all tower data.
     */
    fun updateDarkMap(dark: Boolean, towers: List<TowerWrapper>, servingIds: Set<String>) {
        isDarkMap = dark
        updateData(towers, servingIds)
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
 * Renders tower icons on the map as SDF symbols colored by provider (MCC/MNC).
 *
 * @param isDarkMap Whether the current map uses a dark tile source, affects halo color for contrast
 */
@Composable
fun TowerSymbols(
    towerWrapperList: List<TowerWrapper>,
    servingIds: Set<String>,
    isDarkMap: Boolean = true,
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
                isDarkMap = isDarkMap,
            )
        },
        update = {
            set(towerWrapperList) { newTowers ->
                updateData(newTowers, servingIds)
            }
            set(servingIds) { newServing ->
                updateData(towerWrapperList, newServing)
            }
            set(isDarkMap) { dark ->
                updateDarkMap(dark, towerWrapperList, servingIds)
            }
        }
    )
}
