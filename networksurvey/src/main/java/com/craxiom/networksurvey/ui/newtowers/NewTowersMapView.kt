package com.craxiom.networksurvey.ui.newtowers

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.ui.cellular.towermap.MapLibreMap
import com.craxiom.networksurvey.ui.cellular.towermap.MapLocationSettings
import com.craxiom.networksurvey.ui.cellular.towermap.MapUiSettings
import com.craxiom.networksurvey.ui.common.MAP_MAX_FIT_ZOOM
import com.craxiom.networksurvey.ui.common.MapOfflineHint
import com.craxiom.networksurvey.ui.common.NsEmptyState
import com.craxiom.networksurvey.ui.common.NsMapStyles
import com.craxiom.networksurvey.ui.common.featureIdAt
import com.craxiom.networksurvey.ui.common.fitTo
import com.craxiom.networksurvey.ui.common.placeAttribution
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import timber.log.Timber
import org.maplibre.android.maps.MapLibreMap as MlMap

/** "See on map" zooms in at least this far, so the find is not a dot among many. */
private const val FOCUS_MIN_ZOOM = 15.0
private const val FOCUS_ANIMATION_MS = 300

/** The gap between the legend and the map's edges. */
private val LEGEND_MARGIN = 12.dp

/**
 * The New towers page's Map view: one circle per find at the place the phone was when it first
 * connected (never the tower), filled with the provider color and labeled with the technology.
 * A find the tower database now has, with a position, also shows the real tower glyph there and a
 * dashed line to it. The legend is always on screen.
 *
 * The camera frames every marker when the map opens and moves to [focusFindId] whenever it or
 * [focusRequest] changes.
 * Finds without a location are left off; when none has one, an empty message replaces the map.
 * The basemap follows the app theme (keyless light or dark OpenFreeMap style).
 *
 * @param finds Every find, newest first; the map plots the ones with a location.
 * @param focusFindId The find to center on ("Show on map" from the detail sheet), or null.
 * @param onFindClick Opens the detail sheet of the tapped find, the same sheet as the list's.
 * @param selectedFindId The find drawn larger with an accent ring: the open sheet's, else the focus.
 * @param focusRequest A number that changes with every "Show on map", so the camera centers again
 * on a find that is already [focusFindId].
 */
@Composable
internal fun NewTowersMapView(
    finds: List<NewTowerFindEntity>,
    focusFindId: Long?,
    onFindClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    selectedFindId: Long? = focusFindId,
    focusRequest: Int = 0,
) {
    val markers = remember(finds) { newTowerMapMarkers(finds) }
    if (markers.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NsEmptyState(text = stringResource(R.string.new_towers_map_no_locations), icon = R.drawable.ic_location_pin)
        }
        return
    }
    val legend = remember(markers, finds.size) { newTowersMapLegend(markers, finds.size) }
    val description = newTowersMapDescription(markers, legend)

    Box(modifier = modifier.fillMaxSize()) {
        var legendHeightPx by remember { mutableIntStateOf(0) }
        NewTowersMap(
            markers = markers,
            focusFindId = focusFindId,
            focusRequest = focusRequest,
            selectedFindId = selectedFindId,
            bottomInsetPx = legendHeightPx,
            onFindClick = onFindClick,
            modifier = Modifier.fillMaxSize().semantics { contentDescription = description },
        )
        // The map runs under the navigation bar; the legend sits above it
        val bottomBar = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
        val density = LocalDensity.current
        val clearancePx = bottomBar.getBottom(density) + with(density) { LEGEND_MARGIN.roundToPx() * 2 }
        NewTowersMapLegendStrip(
            legend = legend,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(bottomBar)
                .padding(LEGEND_MARGIN)
                // The legend plus its margins and the navigation bar: the band of map it covers
                .onSizeChanged { size -> legendHeightPx = size.height + clearancePx },
        )
    }
}

/**
 * The MapLibre map and its layers ([NewTowerMapLayers]). Survives a style that fails or reloads
 * (a theme switch) and finds that change while it is shown: the layers are re-added to each new
 * style and refilled from the latest markers.
 *
 * @param bottomInsetPx The height the legend covers, kept clear when framing and centering.
 */
@Composable
private fun NewTowersMap(
    markers: List<NewTowerMapMarker>,
    focusFindId: Long?,
    focusRequest: Int,
    selectedFindId: Long?,
    bottomInsetPx: Int,
    onFindClick: (Long) -> Unit,
    modifier: Modifier,
) {
    val currentOnClick by rememberUpdatedState(onFindClick)
    var style by remember { mutableStateOf<Style?>(null) }
    var map by remember { mutableStateOf<MlMap?>(null) }
    var fitted by remember { mutableStateOf(false) }
    var styleFailed by remember { mutableStateOf(false) }
    val isDarkMap = isSystemInDarkTheme()
    val accentArgb = MaterialTheme.colorScheme.primary.toArgb()
    val lineArgb = colorResource(R.color.serving_cell_line).toArgb()
    val attributionMarginPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    Box(modifier = modifier) {
        MapLibreMap(
            styleUri = NsMapStyles.keyless(isDarkMap),
            modifier = Modifier.fillMaxSize(),
            paddingInsets = PaddingValues(0.dp),
            sdfImages = NewTowerMapLayers.SDF_IMAGES,
            uiSettings = MapUiSettings(tiltGesturesEnabled = false, attributionEnabled = true),
            locationSettings = MapLocationSettings(locationEnabled = false),
            onStyleLoadFailed = { styleFailed = true },
            onMapReady = { _, readyMap, loadedStyle ->
                styleFailed = false
                NewTowerMapLayers.install(loadedStyle)
                style = loadedStyle
                // A reloaded style hands back the same map: keep a single click listener on it
                if (map !== readyMap) {
                    map = readyMap
                    readyMap.addOnMapClickListener { latLng ->
                        val id = readyMap.featureIdAt(latLng, NewTowerMapLayers.TAPPABLE_LAYERS, NEW_TOWER_PROP_ID)
                            ?.toLongOrNull()
                        if (id != null) currentOnClick(id)
                        id != null
                    }
                }
            },
            onMapDestroyed = {
                style = null
                map = null
            },
        )

        LaunchedEffect(style, markers, selectedFindId) {
            style?.let { NewTowerMapLayers.setData(it, markers, selectedFindId) }
        }

        LaunchedEffect(style, isDarkMap, accentArgb, lineArgb) {
            style?.let { NewTowerMapLayers.applyColors(it, isDarkMap, accentArgb, lineArgb) }
        }

        // Keep the legend's band clear, for framing and for the attribution ("i") control
        LaunchedEffect(map, bottomInsetPx) {
            map?.run {
                setPadding(0, 0, 0, bottomInsetPx)
                placeAttribution(attributionMarginPx, bottomInsetPx)
            }
        }

        // Frame every marker (and drawn tower) once, the first time there is a map
        LaunchedEffect(map, markers) {
            val boundMap = map ?: return@LaunchedEffect
            if (fitted) return@LaunchedEffect
            boundMap.setPadding(0, 0, 0, bottomInsetPx)
            boundMap.fitTo(newTowerMapFitPoints(markers), MAP_MAX_FIT_ZOOM)
            fitted = true
        }

        // "See on map": center on the find, after the first framing when both happen on open
        LaunchedEffect(map, focusFindId, focusRequest) {
            val boundMap = map ?: return@LaunchedEffect
            val target = markers.firstOrNull { it.id == focusFindId } ?: return@LaunchedEffect
            try {
                val zoom = maxOf(boundMap.cameraPosition.zoom, FOCUS_MIN_ZOOM)
                boundMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(target.sighting.latitude, target.sighting.longitude), zoom),
                    FOCUS_ANIMATION_MS,
                )
            } catch (e: Exception) {
                Timber.w(e, "Could not center the New towers map on find %d", target.id)
            }
        }

        if (styleFailed) {
            MapOfflineHint(Modifier.align(Alignment.TopCenter))
        }
    }
}
