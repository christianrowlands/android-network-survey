package com.craxiom.networksurvey.ui.newtowers

import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.towermap.KEY_TOWER_ICON
import com.craxiom.networksurvey.ui.cellular.towermap.TOWER_ICON_HALO_WIDTH
import com.craxiom.networksurvey.ui.cellular.towermap.towerIconHaloColor
import com.craxiom.networksurvey.ui.common.NsMapStyles
import com.craxiom.networksurvey.ui.common.mapColorHex
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.expressions.Expression.toColor
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleSortKey
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconColor
import org.maplibre.android.style.layers.PropertyFactory.iconHaloColor
import org.maplibre.android.style.layers.PropertyFactory.iconHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import timber.log.Timber

/**
 * The New towers map's MapLibre sources and layers, bottom to top: the dashed sighting-to-tower
 * lines, the tower glyphs (the Map tab's tower icon, tinted by provider), the sighting circles
 * (provider fill, white halo), the selected find's accent ring, and the technology labels.
 *
 * A sighting is a circle, the "where you were" shape of the Map tab's survey points, never a
 * tower glyph. Every call tolerates a style that is gone or still loading: it logs and skips.
 */
internal object NewTowerMapLayers {

    /** The layers a tap can hit; both carry [NEW_TOWER_PROP_ID]. */
    val TAPPABLE_LAYERS = arrayOf(SIGHTING_LAYER, TOWER_LAYER)

    /** The images the map registers: the tower icon, tinted per provider (an SDF image). */
    val SDF_IMAGES = mapOf(KEY_TOWER_ICON to R.drawable.ic_cell_tower_map_sdf)

    /** Adds the empty sources and the layers to a freshly loaded [style], once. */
    fun install(style: Style) = guarded("install") {
        if (style.getSource(SIGHTING_SOURCE) != null) return@guarded
        val empty = FeatureCollection.fromFeatures(emptyList())
        style.addSource(GeoJsonSource(LINE_SOURCE, empty))
        style.addSource(GeoJsonSource(TOWER_SOURCE, empty))
        style.addSource(GeoJsonSource(SIGHTING_SOURCE, empty))

        style.addLayer(LineLayer(LINE_LAYER, LINE_SOURCE).withProperties(lineWidth(LINE_WIDTH), lineDasharray(LINE_DASH)))
        style.addLayer(
            SymbolLayer(TOWER_LAYER, TOWER_SOURCE).withProperties(
                iconImage(KEY_TOWER_ICON),
                iconColor(toColor(get(NEW_TOWER_PROP_COLOR))),
                iconHaloWidth(TOWER_ICON_HALO_WIDTH),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
            )
        )
        style.addLayer(
            CircleLayer(SIGHTING_LAYER, SIGHTING_SOURCE).withProperties(
                circleColor(toColor(get(NEW_TOWER_PROP_COLOR))),
                circleRadius(whenSelected(SELECTED_RADIUS, RADIUS)),
                circleStrokeWidth(HALO_WIDTH),
                circleSortKey(whenSelected(1f, 0f)),
            )
        )
        style.addLayer(
            CircleLayer(RING_LAYER, SIGHTING_SOURCE).withProperties(
                circleRadius(SELECTED_RADIUS + HALO_WIDTH),
                circleOpacity(0f),
                circleStrokeWidth(RING_WIDTH),
            ).withFilter(eq(get(NEW_TOWER_PROP_SELECTED), literal(true)))
        )
        style.addLayer(
            SymbolLayer(LABEL_LAYER, SIGHTING_SOURCE).withProperties(
                textField(get(NEW_TOWER_PROP_TECH)),
                textFont(NsMapStyles.TEXT_FONT_BOLD),
                textSize(LABEL_TEXT_SIZE),
                textAnchor(Property.TEXT_ANCHOR_LEFT),
                textOffset(LABEL_OFFSET_EMS),
                textHaloWidth(LABEL_HALO_WIDTH),
            )
        )
    }

    /** Replaces what every layer draws with [markers], [selectedId] being the highlighted find. */
    fun setData(style: Style, markers: List<NewTowerMapMarker>, selectedId: Long?) = guarded("setData") {
        style.getSourceAs<GeoJsonSource>(SIGHTING_SOURCE)?.setGeoJson(newTowerSightingFeatures(markers, selectedId))
        style.getSourceAs<GeoJsonSource>(TOWER_SOURCE)?.setGeoJson(newTowerTowerFeatures(markers))
        style.getSourceAs<GeoJsonSource>(LINE_SOURCE)?.setGeoJson(newTowerLineFeatures(markers))
    }

    /**
     * Applies the theme: halos and labels for a dark or a light basemap, the accent ring, and the
     * dashed line color (the Map tab's serving cell line).
     */
    fun applyColors(style: Style, isDarkMap: Boolean, accentArgb: Int, lineArgb: Int) = guarded("applyColors") {
        style.getLayer(LINE_LAYER)?.setProperties(lineColor(mapColorHex(lineArgb)))
        style.getLayer(TOWER_LAYER)?.setProperties(iconHaloColor(towerIconHaloColor(isDarkMap)))
        style.getLayer(SIGHTING_LAYER)?.setProperties(circleStrokeColor(if (isDarkMap) DARK_MAP_HALO else LIGHT_MAP_HALO))
        style.getLayer(RING_LAYER)?.setProperties(circleStrokeColor(mapColorHex(accentArgb)))
        style.getLayer(LABEL_LAYER)?.setProperties(
            textColor(if (isDarkMap) DARK_MAP_LABEL else LIGHT_MAP_LABEL),
            textHaloColor(if (isDarkMap) DARK_MAP_LABEL_HALO else LIGHT_MAP_LABEL_HALO),
        )
    }

    private fun whenSelected(selected: Float, normal: Float) =
        switchCase(eq(get(NEW_TOWER_PROP_SELECTED), literal(true)), literal(selected), literal(normal))

    /** Runs [block], logging instead of crashing when the style was replaced or the map destroyed. */
    private inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Timber.w(e, "New towers map: %s skipped", what)
        }
    }
}

private const val LINE_SOURCE = "new-towers-line-source"
private const val TOWER_SOURCE = "new-towers-tower-source"
private const val SIGHTING_SOURCE = "new-towers-sighting-source"
private const val LINE_LAYER = "new-towers-line-layer"
private const val TOWER_LAYER = "new-towers-tower-layer"
private const val SIGHTING_LAYER = "new-towers-sighting-layer"
private const val RING_LAYER = "new-towers-ring-layer"
private const val LABEL_LAYER = "new-towers-label-layer"

/** Sighting circle radius; the selected one grows. MapLibre style units are density independent. */
private const val RADIUS = 9f
private const val SELECTED_RADIUS = 12f
private const val HALO_WIDTH = 3f
private const val RING_WIDTH = 3f

/** The same dashed line as the Map tab's serving cell line. */
private const val LINE_WIDTH = 3f
private val LINE_DASH = arrayOf(5f, 3f)

private const val LABEL_TEXT_SIZE = 11f
private const val LABEL_HALO_WIDTH = 1.5f

/** Clears the selected circle's ring (12 + 3 + 3 = 18 px) at an 11 px text size. */
private val LABEL_OFFSET_EMS = arrayOf(1.75f, 0f)

/** A pure white halo glares on the dark basemap, so it is softened a little there. */
private const val LIGHT_MAP_HALO = "#FFFFFF"
private const val DARK_MAP_HALO = "rgba(255,255,255,0.9)"
private const val LIGHT_MAP_LABEL = "#1B1F24"
private const val LIGHT_MAP_LABEL_HALO = "rgba(255,255,255,0.9)"
private const val DARK_MAP_LABEL = "#FFFFFF"
private const val DARK_MAP_LABEL_HALO = "rgba(0,0,0,0.75)"
