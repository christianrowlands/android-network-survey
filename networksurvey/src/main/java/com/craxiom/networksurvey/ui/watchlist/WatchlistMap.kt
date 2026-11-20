package com.craxiom.networksurvey.ui.watchlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.cellular.towermap.MapLibreMap
import com.craxiom.networksurvey.ui.cellular.towermap.MapLocationSettings
import com.craxiom.networksurvey.ui.cellular.towermap.MapUiSettings
import com.craxiom.networksurvey.ui.cellular.towermap.SurveyedPointPalette
import com.craxiom.networksurvey.ui.common.MAP_MAX_FIT_ZOOM
import com.craxiom.networksurvey.ui.common.MapOfflineHint
import com.craxiom.networksurvey.ui.common.MapPoint
import com.craxiom.networksurvey.ui.common.NsMapStyles
import com.craxiom.networksurvey.ui.common.featureIdAt
import com.craxiom.networksurvey.ui.common.fitTo
import com.craxiom.networksurvey.ui.common.mapColorHex
import com.craxiom.networksurvey.ui.common.placeAttribution
import com.craxiom.networksurvey.util.toWifiSignalCategory
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
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
 * The basemap of the Watchlist maps: the keyless dark "fiord" style, which matches the app's dark
 * look while keeping roads and labels legible. Tiles are fetched over the network, so the map is
 * blank when offline.
 */
const val WATCHLIST_MAP_STYLE_URL = NsMapStyles.OPENFREEMAP_FIORD

private const val SOURCE_ID = "watchlist-sightings-source"
private const val LAYER_ID = "watchlist-sightings-layer"
private const val PROP_ID = "pointId"
private const val PROP_COLOR = "pointColor"
private const val PROP_SELECTED = "selected"

private const val SELECTED_STROKE_RGBA = "rgba(3, 169, 244, 1)"
private const val NORMAL_STROKE_RGBA = "rgba(17, 24, 28, 1)"

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
 * and dBm text elsewhere, or a neutral gray when the signal is unknown (null). Usable off the main
 * thread (no Compose state read).
 */
internal fun signalColorHex(rssi: Int?): String =
    mapColorHex(rssi?.toWifiSignalCategory()?.color?.toArgb() ?: SurveyedPointPalette.UNKNOWN)

/**
 * A self-contained MapLibre map that plots [points] as colored circles and reports taps. Built on the
 * shared [MapLibreMap] wrapper but it manages its own source/layer (a [CircleLayer], so no icon assets
 * are needed) and click handling rather than the tower-map composition. The selected point is drawn
 * larger with an accent ring; tapping a circle invokes [onPointClick] with that point's id.
 *
 * The camera fits all points the first time they arrive and recenters on the selected point.
 *
 * @param topInset height (from the top) covered by an overlay such as floating controls.
 * @param bottomInset height (from the bottom) that is covered by an overlay such as a bottom sheet.
 *   Both are applied as map camera padding so fitting and recentering keep points in the visible band
 *   between the overlays.
 * @param attributionEnabled shows the MapLibre attribution ("i") control, positioned just above
 *   [bottomInset] so it rides above the bottom overlay instead of being hidden or clipped.
 */
@Composable
fun WatchlistMap(
    points: List<WatchlistMapPoint>,
    selectedId: String?,
    onPointClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    paddingInsets: PaddingValues = PaddingValues(0.dp),
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    attributionEnabled: Boolean = false,
) {
    val currentOnClick by rememberUpdatedState(onPointClick)
    var style by remember { mutableStateOf<Style?>(null) }
    var map by remember { mutableStateOf<MlMap?>(null) }
    var fitted by remember { mutableStateOf(false) }
    var styleFailed by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val topInsetPx = with(density) { topInset.roundToPx() }
    val bottomInsetPx = with(density) { bottomInset.roundToPx() }

    Box(modifier = modifier) {
        MapLibreMap(
            styleUri = WATCHLIST_MAP_STYLE_URL,
            modifier = Modifier.fillMaxSize(),
            paddingInsets = paddingInsets,
            uiSettings = MapUiSettings(
                tiltGesturesEnabled = false,
                attributionEnabled = attributionEnabled
            ),
            locationSettings = MapLocationSettings(locationEnabled = false),
            onStyleLoadFailed = { styleFailed = true },
            onMapReady = { _, readyMap, loadedStyle ->
                styleFailed = false
                loadedStyle.addWatchlistCircleLayer()
                style = loadedStyle
                map = readyMap
                readyMap.addOnMapClickListener { latLng ->
                    // Query a small box around the tap so the small circles are easy to hit.
                    val tappedId = readyMap.featureIdAt(latLng, arrayOf(LAYER_ID), PROP_ID)
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

        // Pin the attribution ("i") bottom-left, riding just above the bottom overlay so it is not
        // hidden behind the sheet/legend nor clipped (MapUpdater leaves gravity/margins untouched).
        LaunchedEffect(map, bottomInsetPx, attributionEnabled) {
            if (!attributionEnabled) return@LaunchedEffect
            val base = with(density) { 8.dp.roundToPx() }
            map?.placeAttribution(base, bottomInsetPx)
        }

        // Keep the camera padding in sync with the overlays (floating controls on top, a bottom sheet
        // below) so points are framed in the visible band between them.
        LaunchedEffect(map, topInsetPx, bottomInsetPx) {
            map?.setPadding(0, topInsetPx, 0, bottomInsetPx)
        }

        // Fit the camera to all points the first time we have both a map and data.
        LaunchedEffect(map, points) {
            val boundMap = map ?: return@LaunchedEffect
            if (points.isNotEmpty() && !fitted) {
                boundMap.setPadding(0, topInsetPx, 0, bottomInsetPx)
                boundMap.fitTo(points.map { MapPoint(it.latitude, it.longitude) }, MAP_MAX_FIT_ZOOM)
                fitted = true
            }
        }

        // Recenter on the selected point (re-run when an inset changes so the point stays visible
        // between the overlays, e.g. after a sheet expands).
        LaunchedEffect(selectedId, topInsetPx, bottomInsetPx) {
            val boundMap = map ?: return@LaunchedEffect
            boundMap.setPadding(0, topInsetPx, 0, bottomInsetPx)
            val target = selectedId?.let { id -> points.firstOrNull { it.id == id } }
                ?: return@LaunchedEffect
            boundMap.animateCamera(
                CameraUpdateFactory.newLatLng(LatLng(target.latitude, target.longitude)), 300
            )
        }

        // Tiles load over the network; surface a hint when the style cannot be fetched (offline).
        if (styleFailed) {
            MapOfflineHint(Modifier.align(Alignment.TopCenter))
        }
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
