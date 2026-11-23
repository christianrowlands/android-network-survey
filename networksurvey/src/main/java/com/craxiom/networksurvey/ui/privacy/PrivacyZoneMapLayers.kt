package com.craxiom.networksurvey.ui.privacy

import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.ui.common.NsMapStyles
import com.craxiom.networksurvey.ui.common.mapColorHex
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.geometryType
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import timber.log.Timber

/** A zone as the map draws it; [editing] marks the circle the editor is placing. */
data class PrivacyZoneShape(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val editing: Boolean = false,
) {
    companion object {
        /** The shape of a saved [zone]. */
        fun of(zone: PrivacyZone) = PrivacyZoneShape(zone.id, zone.name, zone.latitude, zone.longitude, zone.radiusMeters)
    }
}

/**
 * The MapLibre source and layers that draw privacy zones, shared by the Map tab and the zone
 * editor so a zone looks the same in both: a neutral fill with a dashed edge and the zone's name at
 * street zoom. The circle the editor is placing uses the accent color and a solid edge.
 *
 * Every call tolerates a style that is gone or still loading: it logs and skips.
 */
object PrivacyZoneMapLayers {

    /** The fill layer, which a tap on a zone hits; its features carry [PROP_ID]. */
    const val FILL_LAYER = "privacy-zones-fill"

    /** The zone id on every feature. */
    const val PROP_ID = "zoneId"

    private const val SOURCE = "privacy-zones-source"
    private const val LINE_LAYER = "privacy-zones-line"
    private const val LABEL_LAYER = "privacy-zones-label"
    private const val PROP_NAME = "name"
    private const val PROP_EDITING = "editing"
    private const val POINT_TYPE = "Point"

    private const val ZONE_FILL_OPACITY = 0.14f
    private const val EDITING_FILL_OPACITY = 0.18f
    private const val LINE_WIDTH = 2f
    private const val EDITING_LINE_WIDTH = 2.5f
    private val LINE_DASH = arrayOf(3f, 2f)
    private const val LABEL_SIZE = 12f
    private const val LABEL_HALO_WIDTH = 1.5f

    /** Labels show from street zoom in, where a name no longer covers the neighborhood. */
    private const val LABEL_MIN_ZOOM = 13f

    /** Neutral zone colors that read on both light and dark basemaps. */
    private const val ZONE_COLOR_LIGHT_MAP = 0xFF4A5563.toInt()
    private const val ZONE_COLOR_DARK_MAP = 0xFFC9D1DB.toInt()
    private const val HALO_LIGHT_MAP = 0xFFFFFFFF.toInt()
    private const val HALO_DARK_MAP = 0xFF101418.toInt()

    /**
     * Adds the empty source and the layers to [style] once, below [belowLayerId] when the style has
     * it (so layers added earlier, like towers and survey points, stay on top), otherwise on top.
     */
    fun install(style: Style, belowLayerId: String? = null) = guarded("install") {
        if (style.getSource(SOURCE) != null) return@guarded
        style.addSource(GeoJsonSource(SOURCE, FeatureCollection.fromFeatures(emptyList())))
        val below = belowLayerId?.takeIf { style.getLayer(it) != null }
        addLayer(style, FillLayer(FILL_LAYER, SOURCE), below)
        addLayer(style, LineLayer(LINE_LAYER, SOURCE).withProperties(lineDasharray(LINE_DASH)), below)
        addLayer(
            style,
            SymbolLayer(LABEL_LAYER, SOURCE).withProperties(
                textField(get(PROP_NAME)),
                textFont(NsMapStyles.TEXT_FONT_BOLD),
                textSize(LABEL_SIZE),
                textHaloWidth(LABEL_HALO_WIDTH),
            ).apply {
                minZoom = LABEL_MIN_ZOOM
                // Only the center points carry a label; a polygon would be labeled along its edge
                setFilter(eq(geometryType(), literal(POINT_TYPE)))
            },
            below,
        )
    }

    /** Draws [zones] (replacing whatever was drawn). */
    fun setData(style: Style, zones: List<PrivacyZoneShape>) = guarded("setData") {
        style.getSourceAs<GeoJsonSource>(SOURCE)?.setGeoJson(features(zones))
    }

    /** Colors the layers for a light or dark basemap; [accentArgb] colors the circle being edited. */
    fun applyColors(style: Style, isDarkMap: Boolean, accentArgb: Int) = guarded("applyColors") {
        val zoneHex = mapColorHex(if (isDarkMap) ZONE_COLOR_DARK_MAP else ZONE_COLOR_LIGHT_MAP)
        val accentHex = mapColorHex(accentArgb)
        val color = switchCase(get(PROP_EDITING), literal(accentHex), literal(zoneHex))
        style.getLayer(FILL_LAYER)?.setProperties(
            fillColor(color),
            fillOpacity(switchCase(get(PROP_EDITING), literal(EDITING_FILL_OPACITY), literal(ZONE_FILL_OPACITY))),
        )
        style.getLayer(LINE_LAYER)?.setProperties(
            lineColor(color),
            lineWidth(switchCase(get(PROP_EDITING), literal(EDITING_LINE_WIDTH), literal(LINE_WIDTH))),
        )
        style.getLayer(LABEL_LAYER)?.setProperties(
            textColor(zoneHex),
            textHaloColor(mapColorHex(if (isDarkMap) HALO_DARK_MAP else HALO_LIGHT_MAP)),
        )
    }

    /** Takes the layers and the source off [style]. */
    fun remove(style: Style) = guarded("remove") {
        listOf(LABEL_LAYER, LINE_LAYER, FILL_LAYER).forEach { if (style.getLayer(it) != null) style.removeLayer(it) }
        if (style.getSource(SOURCE) != null) style.removeSource(SOURCE)
    }

    /**
     * One polygon per zone (the fill and the dashed edge) plus one point at its center (the label).
     * The circle being edited comes last, so it draws over the saved zones.
     */
    fun features(zones: List<PrivacyZoneShape>): FeatureCollection {
        val features = zones.sortedBy { it.editing }.flatMap { zone ->
            val ring = PrivacyZoneGeometry.ring(zone.latitude, zone.longitude, zone.radiusMeters)
                .map { Point.fromLngLat(it.longitude, it.latitude) }
            val circle = Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).withProperties(zone)
            val label = Feature.fromGeometry(Point.fromLngLat(zone.longitude, zone.latitude)).withProperties(zone)
            // The circle being placed needs no label: its name is in the form under the map
            if (zone.editing) listOf(circle) else listOf(circle, label)
        }
        return FeatureCollection.fromFeatures(features)
    }

    private fun Feature.withProperties(zone: PrivacyZoneShape): Feature = apply {
        addStringProperty(PROP_ID, zone.id)
        addStringProperty(PROP_NAME, zone.name)
        addBooleanProperty(PROP_EDITING, zone.editing)
    }

    private fun addLayer(style: Style, layer: Layer, below: String?) {
        if (below != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
    }

    private inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Timber.w(e, "Privacy zone map layers: %s skipped", what)
        }
    }
}
