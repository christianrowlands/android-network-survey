package com.craxiom.networksurvey.ui.privacy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.ui.cellular.towermap.CameraMoveStartedReason
import com.craxiom.networksurvey.ui.cellular.towermap.MapLibreMap
import com.craxiom.networksurvey.ui.cellular.towermap.MapLocationSettings
import com.craxiom.networksurvey.ui.cellular.towermap.MapUiSettings
import com.craxiom.networksurvey.ui.cellular.towermap.rememberCameraPositionState
import com.craxiom.networksurvey.ui.common.MapOfflineHint
import com.craxiom.networksurvey.ui.common.MapPoint
import com.craxiom.networksurvey.ui.common.NsMapStyles
import com.craxiom.networksurvey.ui.common.placeAttribution
import com.craxiom.networksurvey.ui.common.rememberPermissionRequest
import com.craxiom.networksurvey.ui.common.requestOrOpenSettings
import kotlinx.coroutines.flow.distinctUntilChanged
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import timber.log.Timber
import org.maplibre.android.maps.MapLibreMap as MlMap

/** The zoom a map opens at with no place to show: the whole world. */
private const val WORLD_ZOOM = 1.0

/** The zoom used before the circle is framed, when the editor opens on a place. */
private const val PLACE_ZOOM = 15.0

/** The share of the map's shorter side kept clear around the framed circle, on each side. */
private const val FRAME_PADDING_FRACTION = 0.2f
private const val CAMERA_ANIMATION_MS = 300

private val PIN_SIZE = 40.dp

/**
 * Moves the editor's map: frame the circle (after the radius changed) or go to a place (the phone's
 * location). Each request carries a new number, so the same request twice still runs.
 */
@Stable
class ZoneEditorMapControl {
    internal var frameRequest by mutableIntStateOf(0)
    internal var goTo by mutableStateOf<Pair<Int, MapPoint>?>(null)

    /** The newest location known: the map's own location engine's, else the phone's last known one. */
    var latestLocation by mutableStateOf<MapPoint?>(null)

    /** True when "Use my location" was tapped before any location was known: the next fix moves the map. */
    internal var moveOnNextFix by mutableStateOf(false)

    /** Frames the circle so it fills about half the map. */
    fun frameCircle() {
        frameRequest++
    }

    /** Centers the map on [point] and frames the circle there. */
    fun moveTo(point: MapPoint) {
        goTo = (goTo?.first ?: 0) + 1 to point
    }
}

/**
 * The editor's map: a fixed pin at the center, the zone's circle around it (redrawn as the map pans
 * and the radius changes), the other saved zones in the Map tab's zone style, and the phone's
 * location dot. Panning the map moves the zone. The basemap follows the app theme.
 *
 * "Use my location" always shows, since it is the only way to place a zone without panning (which
 * TalkBack users cannot do): without location permission it asks for it (or opens the app's system
 * settings once it was denied for good), and the map is rebuilt once it is granted so its location
 * engine starts.
 *
 * @param form             The center and radius to draw; the center is where the camera is.
 * @param otherZones       The saved zones other than this one, drawn for context.
 * @param initialZoom      The zoom to reopen at (after a rotation), or null to frame the circle.
 * @param onCameraMoved    Reports the map's new center (the zone's center) and zoom; byUser is
 *                         true for a gesture.
 * @param onLocation       Reports each location from the map's location engine.
 * @param onLocationUnknown "Use my location" was tapped before any location is known.
 */
@Composable
internal fun PrivacyZoneEditorMap(
    form: PrivacyZoneEditorForm,
    otherZones: List<PrivacyZone>,
    initialZoom: Double?,
    control: ZoneEditorMapControl,
    onCameraMoved: (center: MapPoint, zoom: Double, byUser: Boolean) -> Unit,
    onLocation: (MapPoint) -> Unit,
    onLocationUnknown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDarkMap = isSystemInDarkTheme()
    val accentArgb = MaterialTheme.colorScheme.primary.toArgb()
    val attributionMarginPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val currentOnLocation by rememberUpdatedState(onLocation)
    val currentOnCameraMoved by rememberUpdatedState(onCameraMoved)
    val camera = rememberCameraPositionState {
        val center = form.center
        rawPosition = CameraPosition.Builder()
            .target(LatLng(center?.latitude ?: 0.0, center?.longitude ?: 0.0))
            .zoom(initialZoom ?: if (center == null) WORLD_ZOOM else PLACE_ZOOM)
            .build()
    }
    var map by remember { mutableStateOf<MlMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var styleFailed by remember { mutableStateOf(false) }
    var sizePx by remember { mutableIntStateOf(0) }
    var locationAllowed by remember { mutableStateOf(hasLocationPermission(context)) }
    val useLocation: () -> Unit = {
        val known = control.latestLocation ?: PrivacyZoneEditorViewModel.lastKnownLocation(context)
        if (known != null) {
            control.moveTo(known)
        } else {
            control.moveOnNextFix = true
            onLocationUnknown()
        }
    }
    val permission = rememberPermissionRequest(
        permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        // Approximate location alone is enough, which the request reads as a denial, so check here
        onAnswered = {
            if (!locationAllowed && hasLocationPermission(context)) {
                locationAllowed = true
                useLocation()
            }
        },
    ) {}
    val description = stringResource(R.string.privacy_zone_map_description)
    // True once the camera shows the zone's place (framed, or reopened at a kept zoom). Until then
    // the camera is where the map happened to start (the world view with no place yet), which must
    // not become the zone's center unless the user moves the map there.
    var framed by remember { mutableStateOf(initialZoom != null) }

    Box(
        modifier = modifier
            .onSizeChanged { sizePx = minOf(it.width, it.height) }
            .semantics { contentDescription = description },
    ) {
        // Rebuilt once location permission is granted, so the map's location engine starts
        key(locationAllowed) {
            MapLibreMap(
                styleUri = NsMapStyles.keyless(isDarkMap),
                modifier = Modifier.fillMaxSize(),
                paddingInsets = PaddingValues(0.dp),
                cameraPositionState = camera,
                uiSettings = MapUiSettings(
                    compassEnabled = false,
                    rotationGesturesEnabled = false,
                    tiltGesturesEnabled = false,
                    attributionEnabled = true,
                ),
                locationSettings = MapLocationSettings(locationEnabled = locationAllowed),
                onStyleLoadFailed = { styleFailed = true },
                onMyLocationChanged = { location: Location ->
                    val point = MapPoint(location.latitude, location.longitude)
                    control.latestLocation = point
                    if (control.moveOnNextFix) {
                        control.moveOnNextFix = false
                        control.moveTo(point)
                    }
                    currentOnLocation(point)
                },
                onMapReady = { _, readyMap, loadedStyle ->
                    styleFailed = false
                    PrivacyZoneMapLayers.install(loadedStyle)
                    readyMap.placeAttribution(attributionMarginPx, 0)
                    style = loadedStyle
                    map = readyMap
                },
                onMapDestroyed = {
                    style = null
                    map = null
                },
            )
        }

        // The camera's center is the zone's center
        LaunchedEffect(camera) {
            snapshotFlow { camera.position to camera.cameraMoveStartedReason }
                .distinctUntilChanged()
                .collect { (position, reason) ->
                    val target = position.target ?: return@collect
                    val byUser = reason == CameraMoveStartedReason.GESTURE
                    if (!framed && !byUser) return@collect
                    currentOnCameraMoved(MapPoint(target.latitude, target.longitude), position.zoom, byUser)
                }
        }

        // Frame the circle once when the map first has a place (unless reopening at a kept zoom)
        val shapes = remember(form.center, form.radiusMeters, otherZones) { editorShapes(form, otherZones) }
        LaunchedEffect(style, shapes) { style?.let { PrivacyZoneMapLayers.setData(it, shapes) } }
        LaunchedEffect(style, isDarkMap, accentArgb) { style?.let { PrivacyZoneMapLayers.applyColors(it, isDarkMap, accentArgb) } }

        LaunchedEffect(map, sizePx, form.center != null) {
            val boundMap = map ?: return@LaunchedEffect
            val center = form.center ?: return@LaunchedEffect
            if (framed || sizePx == 0) return@LaunchedEffect
            framed = true
            boundMap.frame(center, form.radiusMeters, sizePx, animate = false)
        }
        LaunchedEffect(map, control.frameRequest) {
            if (control.frameRequest == 0) return@LaunchedEffect
            val center = form.center ?: return@LaunchedEffect
            map?.frame(center, form.radiusMeters, sizePx, animate = true)
        }
        LaunchedEffect(map, control.goTo) {
            val target = control.goTo?.second ?: return@LaunchedEffect
            framed = true
            map?.frame(target, form.radiusMeters, sizePx, animate = true)
        }

        CenterPin(Modifier.align(Alignment.Center))

        if (styleFailed) {
            MapOfflineHint(Modifier.align(Alignment.TopCenter))
        } else {
            MapHint(stringResource(R.string.privacy_zone_move_hint), Modifier.align(Alignment.TopCenter))
        }

        UseMyLocationButton(
            onClick = { if (locationAllowed) useLocation() else permission.requestOrOpenSettings(context) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
        )
    }
}

/** The circle being placed, drawn over the other saved zones. */
private fun editorShapes(form: PrivacyZoneEditorForm, otherZones: List<PrivacyZone>): List<PrivacyZoneShape> {
    val others = otherZones.map(PrivacyZoneShape::of)
    val center = form.center ?: return others
    return others + PrivacyZoneShape(EDITING_ID, "", center.latitude, center.longitude, form.radiusMeters, editing = true)
}

private const val EDITING_ID = "editing"

/** The pin, with its tip on the map's center. */
@Composable
private fun CenterPin(modifier: Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_location_pin),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .size(PIN_SIZE)
            .offset(y = -PIN_SIZE / 2),
    )
}

@Composable
private fun MapHint(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .padding(12.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun UseMyLocationButton(onClick: () -> Unit, modifier: Modifier) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Icon(painterResource(R.drawable.ic_my_location), contentDescription = stringResource(R.string.privacy_zone_use_my_location))
    }
}

/**
 * Centers the camera on [center] at the zoom where the circle of [radiusMeters] fills about half
 * of the map's shorter side ([sizePx]).
 */
private fun MlMap.frame(center: MapPoint, radiusMeters: Int, sizePx: Int, animate: Boolean) {
    try {
        val bounds = LatLngBounds.Builder()
            .includes(PrivacyZoneGeometry.edges(center.latitude, center.longitude, radiusMeters).map { LatLng(it.latitude, it.longitude) })
            .build()
        val padding = (sizePx * FRAME_PADDING_FRACTION).toInt()
        val zoom = getCameraForLatLngBounds(bounds, intArrayOf(padding, padding, padding, padding))?.zoom ?: return
        val update = CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(LatLng(center.latitude, center.longitude)).zoom(zoom).build()
        )
        if (animate) animateCamera(update, CAMERA_ANIMATION_MS) else moveCamera(update)
    } catch (e: Exception) {
        // A map that is not laid out yet can refuse a bounds camera; keep the current one
        Timber.w(e, "Could not frame the privacy zone circle")
    }
}

/** @return True when the app may read the phone's location (fine or coarse). */
internal fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
