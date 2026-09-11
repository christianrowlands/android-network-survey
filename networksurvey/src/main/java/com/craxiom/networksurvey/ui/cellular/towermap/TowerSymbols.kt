package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.rememberCoroutineScope
import com.craxiom.networksurvey.ui.cellular.model.LocationBadge
import com.craxiom.networksurvey.ui.cellular.model.TowerWrapper
import com.craxiom.networksurvey.util.PlmnColorMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconColor
import org.maplibre.android.style.layers.PropertyFactory.iconHaloColor
import org.maplibre.android.style.layers.PropertyFactory.iconHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import timber.log.Timber

const val TOWER_LAYER_KEY = "tower-layer"
const val KEY_SERVING_CELL_ICON = "tower-serving"
const val KEY_TOWER_ICON = "tower"

/** Feature property carrying the tower id used to resolve clicks and the serving cell icon. */
const val TOWER_ID_PROPERTY = "towerId"
private const val PLMN_PROPERTY = "plmn"
private const val COUNT_BADGE_PROPERTY = "countBadge"
private const val OPERATOR_BADGE_PROPERTY = "operatorBadge"
private const val TOWER_SOURCE_KEY = "tower-source"
private const val COUNT_BADGE_LAYER_KEY = "tower-count-badge-layer"
private const val OPERATOR_BADGE_LAYER_KEY = "operator-count-badge-layer"

/**
 * Renders every tower as one feature in a single GeoJSON source with three symbol layers on
 * top of it: the tower icon, the tower count badge, and the operator count badge. Features
 * carry only the properties the style needs; the full [TowerWrapper] is looked up by id when
 * a tower is tapped.
 *
 * The source is rebuilt only when the tower list changes. Serving cell highlighting, provider
 * colors and the halo are style expressions on the layer, so they update without touching
 * the feature data.
 */
internal class TowerSymbolsNode(
    private val style: Style,
    private val scope: CoroutineScope,
    initialTowers: List<TowerWrapper>,
    initialBadges: Map<String, LocationBadge>,
    initialServingIds: Set<String>,
    private val normalIcon: String,
    private val servingIcon: String,
    private var isDarkMap: Boolean,
) : MapNode {
    private val source = GeoJsonSource(TOWER_SOURCE_KEY, FeatureCollection.fromFeatures(emptyArray()))
    private val towerLayer = SymbolLayer(TOWER_LAYER_KEY, TOWER_SOURCE_KEY)
    private val countBadgeLayer = SymbolLayer(COUNT_BADGE_LAYER_KEY, TOWER_SOURCE_KEY)
    private val operatorBadgeLayer = SymbolLayer(OPERATOR_BADGE_LAYER_KEY, TOWER_SOURCE_KEY)
    private var buildJob: Job? = null
    private var servingIds: Set<String> = initialServingIds
    private var plmns: Set<String> = emptySet()

    init {
        BadgeImages.register(style)
        style.addSource(source)
        style.addLayer(
            towerLayer.withProperties(
                iconImage(normalIcon),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )
        )
        style.addLayer(
            countBadgeLayer.withProperties(
                iconImage(get(COUNT_BADGE_PROPERTY)),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconOffset(arrayOf(12f, -12f))
            ).withFilter(has(COUNT_BADGE_PROPERTY))
        )
        style.addLayer(
            operatorBadgeLayer.withProperties(
                iconImage(get(OPERATOR_BADGE_PROPERTY)),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconOffset(arrayOf(12f, 12f))
            ).withFilter(has(OPERATOR_BADGE_PROPERTY))
        )

        updateSource(initialTowers, initialBadges)
        updateLayerProperties()
    }

    /**
     * Rebuilds the feature collection off the main thread and hands it to MapLibre on the main
     * thread. A newer update cancels an in flight build so the map only ever receives the
     * latest list.
     */
    fun updateSource(towers: List<TowerWrapper>, badges: Map<String, LocationBadge>) {
        buildJob?.cancel()
        buildJob = scope.launch(Dispatchers.Default) {
            val startMs = System.currentTimeMillis()
            val uniquePlmns = HashSet<String>()
            val features = ArrayList<Feature>(towers.size)
            for (wrapper in towers) {
                val tower = wrapper.tower
                val plmn = "${tower.mcc}-${tower.mnc}"
                uniquePlmns.add(plmn)
                val feature = Feature.fromGeometry(Point.fromLngLat(tower.lon, tower.lat))
                feature.addStringProperty(TOWER_ID_PROPERTY, wrapper.towerId)
                feature.addStringProperty(PLMN_PROPERTY, plmn)
                badges[wrapper.towerId]?.let { badge ->
                    feature.addStringProperty(COUNT_BADGE_PROPERTY, BadgeImages.countKey(badge.towerCount))
                    if (badge.operatorCount >= 2) {
                        feature.addStringProperty(
                            OPERATOR_BADGE_PROPERTY,
                            BadgeImages.operatorKey(badge.operatorCount)
                        )
                    }
                }
                features.add(feature)
            }
            val collection = FeatureCollection.fromFeatures(features)
            val buildMs = System.currentTimeMillis() - startMs

            withContext(Dispatchers.Main.immediate) {
                val setStartMs = System.currentTimeMillis()
                source.setGeoJson(collection)
                plmns = uniquePlmns
                updateLayerProperties()
                Timber.d(
                    "Tower source updated: ${features.size} features, build ${buildMs} ms, setGeoJson ${System.currentTimeMillis() - setStartMs} ms"
                )
            }
        }
    }

    fun updateServingIds(newServingIds: Set<String>) {
        servingIds = newServingIds
        updateLayerProperties()
    }

    fun updateDarkMap(dark: Boolean) {
        isDarkMap = dark
        updateLayerProperties()
    }

    /** Re-applies the data driven icon, color and halo expressions without rebuilding data. */
    fun updateLayerProperties() {
        val plmnColorPairs = plmns.flatMap { plmn ->
            val parts = plmn.split("-")
            listOf(literal(plmn), color(PlmnColorMapper.getColorArgb(parts[0], parts[1])))
        }

        towerLayer.setProperties(
            iconImage(
                match(
                    get(TOWER_ID_PROPERTY),
                    *servingIds.flatMap { id -> listOf(literal(id), literal(servingIcon)) }.toTypedArray(),
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
            iconHaloColor(if (isDarkMap) "rgba(255,255,255,0.8)" else "rgba(0,0,0,0.3)"),
            iconHaloWidth(5f),
        )
    }

    override fun onRemoved() {
        buildJob?.cancel()
        listOf(OPERATOR_BADGE_LAYER_KEY, COUNT_BADGE_LAYER_KEY, TOWER_LAYER_KEY).forEach { layerId ->
            try {
                style.removeLayer(layerId)
            } catch (_: Exception) {
            }
        }
        try {
            style.removeSource(TOWER_SOURCE_KEY)
        } catch (_: Exception) {
        }
    }

    override fun onCleared() = onRemoved()
}

/**
 * Renders tower icons on the map as SDF symbols colored by provider (MCC/MNC), with count
 * badges where several towers share one location.
 *
 * @param badges per tower badge counts, keyed by tower id, from the ViewModel
 * @param isDarkMap Whether the current map uses a dark tile source, affects halo color for contrast
 */
@Composable
fun TowerSymbols(
    towerWrapperList: List<TowerWrapper>,
    badges: Map<String, LocationBadge>,
    servingIds: Set<String>,
    isDarkMap: Boolean = true,
    colorVersion: Int = 0,
    normalIcon: String = KEY_TOWER_ICON,
    servingIcon: String = KEY_SERVING_CELL_ICON,
) {
    val mapApplier = currentComposer.applier as MapApplier
    val style = mapApplier.style
    val scope = rememberCoroutineScope()

    ComposeNode<TowerSymbolsNode, MapApplier>(
        factory = {
            TowerSymbolsNode(
                style = style,
                scope = scope,
                initialTowers = towerWrapperList,
                initialBadges = badges,
                initialServingIds = servingIds,
                normalIcon = normalIcon,
                servingIcon = servingIcon,
                isDarkMap = isDarkMap,
            )
        },
        update = {
            set(towerWrapperList) { newTowers -> updateSource(newTowers, badges) }
            set(servingIds) { newServing -> updateServingIds(newServing) }
            set(isDarkMap) { dark -> updateDarkMap(dark) }
            set(colorVersion) { _ -> updateLayerProperties() }
        }
    )
}
