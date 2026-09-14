package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointColorMode
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointFeatures
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointsController
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointsData
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleSortKey
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

const val SURVEYED_POINTS_LAYER_KEY = "surveyed-points-layer"
private const val SURVEYED_POINTS_SOURCE_KEY = "surveyed-points-source"

private const val POINT_RADIUS_PX = 4.5f
private const val COARSE_RADIUS_PX = SurveyedPointsController.TARGET_SPACING_PX * 0.6f
private const val COARSE_OPACITY = 0.6f
private const val DARK_MAP_STROKE = "rgba(255,255,255,0.35)"
private const val LIGHT_MAP_STROKE = "rgba(0,0,0,0.25)"

/**
 * Draws surveyed places as one circle layer beneath the tower icons. The hue comes from the
 * selected color mode's expression (see [SurveyedPointStyles]); recent and pending points sort
 * above older and sent ones so the live walk always draws on top.
 */
internal class SurveyedPointsNode(
    private val style: Style,
    initialData: SurveyedPointsData?,
    private var mode: SurveyedPointColorMode,
    private var isDarkMap: Boolean,
) : MapNode {
    private val source = GeoJsonSource(
        SURVEYED_POINTS_SOURCE_KEY,
        FeatureCollection.fromFeatures(emptyList<Feature>())
    )
    private val layer = CircleLayer(SURVEYED_POINTS_LAYER_KEY, SURVEYED_POINTS_SOURCE_KEY)
    private var coarse = false
    private var plmns: Set<String> = emptySet()

    init {
        style.addSource(source)
        if (style.getLayer(TOWER_LAYER_KEY) != null) {
            style.addLayerBelow(layer, TOWER_LAYER_KEY)
        } else {
            style.addLayer(layer)
        }
        update(initialData)
    }

    fun update(data: SurveyedPointsData?) {
        source.setGeoJson(data?.features ?: FeatureCollection.fromFeatures(emptyList<Feature>()))
        coarse = data?.coarse ?: false
        plmns = data?.plmns ?: emptySet()
        updateStyle()
    }

    fun updateMode(newMode: SurveyedPointColorMode) {
        mode = newMode
        updateStyle()
    }

    fun updateDarkMap(dark: Boolean) {
        isDarkMap = dark
        updateStyle()
    }

    /** Re-applies the color expression; also called when provider color overrides change. */
    fun updateStyle() {
        layer.setProperties(
            circleColor(SurveyedPointStyles.circleColor(mode, plmns)),
            circleRadius(if (coarse) COARSE_RADIUS_PX else POINT_RADIUS_PX),
            circleOpacity(if (coarse) COARSE_OPACITY else 1f),
            circleStrokeWidth(if (coarse) 0f else 1f),
            circleStrokeColor(if (isDarkMap) DARK_MAP_STROKE else LIGHT_MAP_STROKE),
            circleSortKey(get(SurveyedPointFeatures.PROP_SORT)),
        )
    }

    override fun onRemoved() {
        try {
            style.removeLayer(SURVEYED_POINTS_LAYER_KEY)
        } catch (_: Exception) {
        }
        try {
            style.removeSource(SURVEYED_POINTS_SOURCE_KEY)
        } catch (_: Exception) {
        }
    }

    override fun onCleared() = onRemoved()
}

/**
 * The "My surveyed places" map layer. Compose it before [TowerSymbols] so it sits beneath the
 * tower icons.
 *
 * @param colorVersion bumped when provider color overrides change so the provider hues refresh
 */
@Composable
fun SurveyedPoints(
    data: SurveyedPointsData?,
    mode: SurveyedPointColorMode,
    isDarkMap: Boolean,
    colorVersion: Int = 0,
) {
    val mapApplier = currentComposer.applier as MapApplier
    val style = mapApplier.style

    ComposeNode<SurveyedPointsNode, MapApplier>(
        factory = {
            SurveyedPointsNode(
                style = style,
                initialData = data,
                mode = mode,
                isDarkMap = isDarkMap
            )
        },
        update = {
            set(data) { newData -> update(newData) }
            set(mode) { newMode -> updateMode(newMode) }
            set(isDarkMap) { dark -> updateDarkMap(dark) }
            set(colorVersion) { _ -> updateStyle() }
        }
    )
}
