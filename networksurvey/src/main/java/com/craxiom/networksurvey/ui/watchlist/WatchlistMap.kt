package com.craxiom.networksurvey.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.towermap.MapLibreMap
import com.craxiom.networksurvey.ui.cellular.towermap.MapLocationSettings
import com.craxiom.networksurvey.ui.cellular.towermap.MapUiSettings
import com.craxiom.networksurvey.util.toWifiSignalCategory
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.expressions.Expression.toColor
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.maps.MapLibreMap as MlMap

/**
 * The keyless OpenStreetMap style the app already falls back to (no MapTiler key required). Used by
 * the Watchlist maps so they render with no extra configuration. Tiles are fetched over the network,
 * so the map is blank when offline.
 */
const val WATCHLIST_MAP_STYLE_URL =
    "https://raw.githubusercontent.com/christianrowlands/ns-map-style-uri/refs/heads/main/openstreetmap.json"

private const val SOURCE_ID = "watchlist-sightings-source"
private const val LAYER_ID = "watchlist-sightings-layer"
private const val PROP_ID = "pointId"
private const val PROP_COLOR = "pointColor"
private const val PROP_SELECTED = "selected"

private const val SELECTED_STROKE_RGBA = "rgba(3, 169, 244, 1)"
private const val NORMAL_STROKE_RGBA = "rgba(17, 24, 28, 1)"

/** Half-size of the square (in px) queried around a tap, so small circles are easy to hit. */
private const val TAP_TOLERANCE_PX = 22f

/**
 * One plottable point on a Watchlist map: a sighting (colored by signal) or a watched network
 * (colored by its legend color).
 *
 * @property id stable identifier echoed back by [WatchlistMap]'s click callback
 * @property colorHex the fill color as a "#RRGGBB" string
 */
data class WatchlistMapPoint(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val colorHex: String,
)

/**
 * The "#RRGGBB" color for an RSSI, derived from the shared signal ramp so map pins match the meters
 * and dBm text elsewhere. Usable off the main thread (no Compose state read).
 */
internal fun signalColorHex(rssi: Int): String =
    "#%06X".format(0xFFFFFF and rssi.toWifiSignalCategory().color.toArgb())

/**
 * A self-contained MapLibre map that plots [points] as colored circles and reports taps. Built on the
 * shared [MapLibreMap] wrapper but it manages its own source/layer (a [CircleLayer], so no icon assets
 * are needed) and click handling rather than the tower-map composition. The selected point is drawn
 * larger with an accent ring; tapping a circle invokes [onPointClick] with that point's id.
 *
 * The camera fits all points the first time they arrive and recenters on the selected point.
 */
@Composable
fun WatchlistMap(
    points: List<WatchlistMapPoint>,
    selectedId: String?,
    onPointClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    paddingInsets: PaddingValues = PaddingValues(0.dp),
) {
    val currentOnClick by rememberUpdatedState(onPointClick)
    var style by remember { mutableStateOf<Style?>(null) }
    var map by remember { mutableStateOf<MlMap?>(null) }
    var fitted by remember { mutableStateOf(false) }
    var styleFailed by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        MapLibreMap(
            styleUri = WATCHLIST_MAP_STYLE_URL,
            modifier = Modifier.fillMaxSize(),
            paddingInsets = paddingInsets,
            uiSettings = MapUiSettings(tiltGesturesEnabled = false),
            locationSettings = MapLocationSettings(locationEnabled = false),
            onStyleLoadFailed = { styleFailed = true },
            onMapReady = { _, readyMap, loadedStyle ->
                styleFailed = false
                loadedStyle.addWatchlistCircleLayer()
                style = loadedStyle
                map = readyMap
                readyMap.addOnMapClickListener { latLng ->
                    val screenPoint = readyMap.projection.toScreenLocation(latLng)
                    // Query a small box around the tap so the small circles are easy to hit.
                    val touch = android.graphics.RectF(
                        screenPoint.x - TAP_TOLERANCE_PX,
                        screenPoint.y - TAP_TOLERANCE_PX,
                        screenPoint.x + TAP_TOLERANCE_PX,
                        screenPoint.y + TAP_TOLERANCE_PX
                    )
                    val features = readyMap.queryRenderedFeatures(touch, LAYER_ID)
                    val tappedId = features.firstNotNullOfOrNull { it.getStringProperty(PROP_ID) }
                    if (tappedId != null) {
                        currentOnClick(tappedId)
                        true
                    } else {
                        false
                    }
                }
            },
        )

        // Push the latest points / selection into the source as soon as the style is ready.
        LaunchedEffect(points, selectedId, style) {
            style?.getSourceAs<GeoJsonSource>(SOURCE_ID)
                ?.setGeoJson(buildFeatureCollection(points, selectedId))
        }

        // Fit the camera to all points the first time we have both a map and data.
        LaunchedEffect(map, points) {
            val boundMap = map ?: return@LaunchedEffect
            if (points.isNotEmpty() && !fitted) {
                boundMap.fitTo(points)
                fitted = true
            }
        }

        // Recenter on the selected point.
        LaunchedEffect(selectedId) {
            val boundMap = map ?: return@LaunchedEffect
            val target = selectedId?.let { id -> points.firstOrNull { it.id == id } }
                ?: return@LaunchedEffect
            boundMap.animateCamera(
                CameraUpdateFactory.newLatLng(LatLng(target.latitude, target.longitude)), 300
            )
        }

        // Tiles load over the network; surface a hint when the style cannot be fetched (offline).
        if (styleFailed) {
            Text(
                text = stringResource(R.string.watchlist_history_map_offline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

private fun MlMap.fitTo(points: List<WatchlistMapPoint>) {
    try {
        if (points.size == 1) {
            val only = points.first()
            moveCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(only.latitude, only.longitude), 15.0)
            )
        } else {
            val builder = LatLngBounds.Builder()
            points.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
            moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 120))
        }
    } catch (_: Exception) {
        // A degenerate bounds (all identical points) can throw; ignore and keep the default camera.
    }
}

private fun buildFeatureCollection(
    points: List<WatchlistMapPoint>,
    selectedId: String?,
): FeatureCollection {
    val features = points.map { point ->
        Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
            addStringProperty(PROP_ID, point.id)
            addStringProperty(PROP_COLOR, point.colorHex)
            addBooleanProperty(PROP_SELECTED, point.id == selectedId)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun Style.addWatchlistCircleLayer() {
    addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList<Feature>())))
    addLayer(
        CircleLayer(LAYER_ID, SOURCE_ID).withProperties(
            circleColor(toColor(get(PROP_COLOR))),
            circleRadius(
                switchCase(
                    eq(get(PROP_SELECTED), literal(true)),
                    literal(10f),
                    literal(6.5f)
                )
            ),
            circleStrokeWidth(
                switchCase(
                    eq(get(PROP_SELECTED), literal(true)),
                    literal(3f),
                    literal(1.5f)
                )
            ),
            circleStrokeColor(
                switchCase(
                    eq(get(PROP_SELECTED), literal(true)),
                    literal(SELECTED_STROKE_RGBA),
                    literal(NORMAL_STROKE_RGBA)
                )
            ),
        )
    )
}
