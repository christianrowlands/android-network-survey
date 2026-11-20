package com.craxiom.networksurvey.ui.newtowers

import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.ui.common.MapPoint
import com.craxiom.networksurvey.ui.common.mapColorHex
import com.craxiom.networksurvey.util.PlmnColorMapper
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/*
 * What the New towers map draws, worked out from the saved finds without touching the map: the
 * markers, the legend's entries, the points the camera frames, and the GeoJSON features of each
 * layer. Kept free of MapLibre's Android types so it can be unit tested.
 */

/** Feature property: the find's id, which a tap reports back. */
internal const val NEW_TOWER_PROP_ID = "findId"

/** Feature property: the provider color as "#RRGGBB". */
internal const val NEW_TOWER_PROP_COLOR = "color"

/** Feature property: the technology label beside a sighting ("LTE"). */
internal const val NEW_TOWER_PROP_TECH = "tech"

/** Feature property: true on the selected find's sighting. */
internal const val NEW_TOWER_PROP_SELECTED = "selected"

/**
 * One find on the map.
 *
 * @property sighting Where the phone was when it first connected to the tower. Never the tower.
 * @property tower The tower's own position, once the tower database has the tower; null otherwise.
 * @property colorArgb The provider's color, the same one the Map tab gives its towers.
 * @property technology The label beside the sighting ("LTE", "NR"), as the list's badge writes it.
 */
data class NewTowerMapMarker(
    val id: Long,
    val sighting: MapPoint,
    val tower: MapPoint?,
    val colorArgb: Int,
    val technology: String,
)

/**
 * What the map's legend shows.
 *
 * @property showsUnknownTowerNote At least one marker has no tower position, so the legend says
 * the tower's location isn't known yet.
 * @property showsTower At least one tower is drawn, so the legend explains the tower glyph.
 * @property unplacedCount Finds left off the map because no location was recorded for them.
 */
data class NewTowersMapLegend(
    val showsUnknownTowerNote: Boolean,
    val showsTower: Boolean,
    val unplacedCount: Int,
)

/**
 * The markers for [finds], in the same order. A find without a usable location is skipped. The
 * tower is only placed once the find is in the tower database and the database gave a position.
 *
 * @param colorOf The provider color for an MCC and MNC.
 */
internal fun newTowerMapMarkers(
    finds: List<NewTowerFindEntity>,
    colorOf: (String, String) -> Int = PlmnColorMapper::getColorArgb,
): List<NewTowerMapMarker> = finds.mapNotNull { find ->
    val sighting = mapPointOrNull(find.latitude, find.longitude) ?: return@mapNotNull null
    NewTowerMapMarker(
        id = find.id,
        sighting = sighting,
        tower = if (find.inDatabase) mapPointOrNull(find.towerLatitude, find.towerLongitude) else null,
        colorArgb = colorOf(find.mcc, find.mnc),
        technology = find.radio.uppercase(),
    )
}

/** The legend for [markers], drawn from [findCount] finds in all. */
internal fun newTowersMapLegend(markers: List<NewTowerMapMarker>, findCount: Int) = NewTowersMapLegend(
    showsUnknownTowerNote = markers.any { it.tower == null },
    showsTower = markers.any { it.tower != null },
    unplacedCount = (findCount - markers.size).coerceAtLeast(0),
)

/** Every point the camera frames when the map opens: each sighting, and each drawn tower. */
internal fun newTowerMapFitPoints(markers: List<NewTowerMapMarker>): List<MapPoint> =
    markers.flatMap { listOfNotNull(it.sighting, it.tower) }

/**
 * The sighting circles. The oldest come first so the newest draw on top; the selected one is
 * raised above the rest by the layer's sort key.
 */
internal fun newTowerSightingFeatures(markers: List<NewTowerMapMarker>, selectedId: Long?): FeatureCollection =
    FeatureCollection.fromFeatures(markers.asReversed().map { marker ->
        Feature.fromGeometry(marker.sighting.toGeoJson()).apply {
            addStringProperty(NEW_TOWER_PROP_ID, marker.id.toString())
            addStringProperty(NEW_TOWER_PROP_COLOR, mapColorHex(marker.colorArgb))
            addStringProperty(NEW_TOWER_PROP_TECH, marker.technology)
            addBooleanProperty(NEW_TOWER_PROP_SELECTED, marker.id == selectedId)
        }
    })

/** The tower glyphs, one per marker whose tower position is known. */
internal fun newTowerTowerFeatures(markers: List<NewTowerMapMarker>): FeatureCollection =
    FeatureCollection.fromFeatures(markers.asReversed().mapNotNull { marker ->
        val tower = marker.tower ?: return@mapNotNull null
        Feature.fromGeometry(tower.toGeoJson()).apply {
            addStringProperty(NEW_TOWER_PROP_ID, marker.id.toString())
            addStringProperty(NEW_TOWER_PROP_COLOR, mapColorHex(marker.colorArgb))
        }
    })

/** The dashed lines from each sighting to its tower, where the tower position is known. */
internal fun newTowerLineFeatures(markers: List<NewTowerMapMarker>): FeatureCollection =
    FeatureCollection.fromFeatures(markers.mapNotNull { marker ->
        val tower = marker.tower ?: return@mapNotNull null
        Feature.fromGeometry(LineString.fromLngLats(listOf(marker.sighting.toGeoJson(), tower.toGeoJson())))
    })

/** A [MapPoint], or null unless both values are a real latitude and longitude (MapLibre rejects others). */
private fun mapPointOrNull(latitude: Double?, longitude: Double?): MapPoint? {
    if (latitude == null || longitude == null) return null
    if (latitude.isNaN() || longitude.isNaN() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return MapPoint(latitude, longitude)
}

private fun MapPoint.toGeoJson(): Point = Point.fromLngLat(longitude, latitude)
