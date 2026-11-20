package com.craxiom.networksurvey.ui.common

import android.graphics.Color
import android.graphics.RectF
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import timber.log.Timber

/*
 * Helpers shared by the small, self-contained MapLibre maps (the Watchlist maps and the New towers
 * map): the keyless basemap styles, framing a set of points, finding the feature under a tap, the
 * attribution placement, and the hint shown when the style cannot load.
 */

/** The keyless OpenFreeMap basemap styles. Neither needs an API key; both load tiles over the network. */
object NsMapStyles {
    /** The light basemap, also the Map tab's default tile source. */
    const val OPENFREEMAP_LIBERTY = "https://tiles.openfreemap.org/styles/liberty"

    /**
     * A dark slate-blue basemap that keeps roads and labels legible (the plain "dark" style is
     * nearly black and hard to read).
     */
    const val OPENFREEMAP_FIORD = "https://tiles.openfreemap.org/styles/fiord"

    /**
     * The font stack for a text layer on either style. The OpenFreeMap glyph server has only Noto
     * Sans, not MapLibre's default font, so a text layer without this draws nothing.
     */
    val TEXT_FONT_BOLD = arrayOf("Noto Sans Bold")

    /** The keyless style for the app's light or dark theme. */
    fun keyless(dark: Boolean): String = if (dark) OPENFREEMAP_FIORD else OPENFREEMAP_LIBERTY
}

/** A latitude and longitude, free of MapLibre types so the camera math can be unit tested. */
data class MapPoint(val latitude: Double, val longitude: Double)

/** How the camera frames a set of points: centered on one spot, or fitted to a bounding box. */
sealed interface MapFit {
    /** Centered on [point] at [zoom]: one point, or several at the same spot. */
    data class Center(val point: MapPoint, val zoom: Double) : MapFit

    /** Fitted to the box that holds every point. */
    data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double) : MapFit
}

/** Half-size of the square (in px) queried around a tap, so small circles are easy to hit. */
const val MAP_TAP_TOLERANCE_PX = 22f

/**
 * The closest zoom an auto-fit uses. Points clustered at one spot would otherwise fit to an extreme
 * zoom where the basemap has no tiles (a blank map); this keeps a street-level frame instead. Users
 * can still pinch in past it.
 */
const val MAP_MAX_FIT_ZOOM = 16.0

/** The padding (in px) kept between fitted points and the edge of the map. */
private const val FIT_PADDING_PX = 120

/**
 * How to frame [points], or null when there are none. A single spot is centered at [maxZoom]
 * rather than fitted, since a box with no size has no sensible zoom.
 */
fun mapFitFor(points: List<MapPoint>, maxZoom: Double): MapFit? {
    if (points.isEmpty()) return null
    val south = points.minOf { it.latitude }
    val north = points.maxOf { it.latitude }
    val west = points.minOf { it.longitude }
    val east = points.maxOf { it.longitude }
    if (south == north && west == east) return MapFit.Center(points.first(), maxZoom)
    return MapFit.Bounds(south = south, west = west, north = north, east = east)
}

/**
 * Moves the camera to frame [points] (see [mapFitFor]), never closer than [maxZoom]: points at
 * (nearly) one spot would otherwise fit to a zoom where the basemap has no tiles (a blank map).
 * The user can still pinch in past it.
 */
fun MapLibreMap.fitTo(points: List<MapPoint>, maxZoom: Double) {
    try {
        when (val fit = mapFitFor(points, maxZoom) ?: return) {
            is MapFit.Center -> moveCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(fit.point.latitude, fit.point.longitude), fit.zoom)
            )

            is MapFit.Bounds -> {
                val bounds = LatLngBounds.Builder()
                    .include(LatLng(fit.south, fit.west))
                    .include(LatLng(fit.north, fit.east))
                    .build()
                moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, FIT_PADDING_PX))
                if (cameraPosition.zoom > maxZoom) {
                    moveCamera(CameraUpdateFactory.newLatLngZoom(bounds.center, maxZoom))
                }
            }
        }
    } catch (e: Exception) {
        // A map that is not laid out yet can refuse a bounds update; keep the default camera.
        Timber.w(e, "Could not fit the map camera to %d points", points.size)
    }
}

/**
 * The [idProperty] of the first feature from [layerIds] within [MAP_TAP_TOLERANCE_PX] of [tap],
 * or null when the tap hit none.
 */
fun MapLibreMap.featureIdAt(tap: LatLng, layerIds: Array<String>, idProperty: String): String? {
    val screenPoint = projection.toScreenLocation(tap)
    val touch = RectF(
        screenPoint.x - MAP_TAP_TOLERANCE_PX,
        screenPoint.y - MAP_TAP_TOLERANCE_PX,
        screenPoint.x + MAP_TAP_TOLERANCE_PX,
        screenPoint.y + MAP_TAP_TOLERANCE_PX
    )
    return queryRenderedFeatures(touch, *layerIds)
        .firstNotNullOfOrNull { feature -> if (feature.hasProperty(idProperty)) feature.getStringProperty(idProperty) else null }
}

/**
 * Pins the attribution ("i") control bottom-left, [marginPx] from the edges and above a bottom
 * overlay [bottomInsetPx] tall, so it is neither hidden behind the overlay nor clipped.
 */
fun MapLibreMap.placeAttribution(marginPx: Int, bottomInsetPx: Int) {
    uiSettings.apply {
        attributionGravity = Gravity.BOTTOM or Gravity.START
        setAttributionMargins(marginPx, 0, marginPx, bottomInsetPx + marginPx)
        setAttributionTintColor(Color.LTGRAY)
    }
}

/** The "#RRGGBB" form of an ARGB color, for a MapLibre feature property (the alpha is dropped). */
fun mapColorHex(argb: Int): String = "#%06X".format(0xFFFFFF and argb)

/** The hint shown over a map whose style could not load (usually offline: the tiles need a connection). */
@Composable
fun MapOfflineHint(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.watchlist_history_map_offline),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .padding(12.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
