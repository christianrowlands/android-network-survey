package com.craxiom.networksurvey.ui.cellular.towermap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.view.Gravity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import timber.log.Timber

private const val LOCATION_REQUEST_INTERVAL = 750L

internal class MapPropertiesNode(
    val map: MapLibreMap,
    style: Style,
    context: Context,
    cameraPositionState: CameraPositionState,
    locationSettings: MapLocationSettings,
    private val onMyLocationChanged: (Location) -> Unit,
    private val onTowersClick: ((List<String>) -> Unit)? = null,
) : MapNode {
    private var locationEngine: LocationEngine? = null
    private var mapClickListener: MapLibreMap.OnMapClickListener? = null
    private var cameraIdleListener: MapLibreMap.OnCameraIdleListener? = null
    private var cameraMoveCancelListener: MapLibreMap.OnCameraMoveCanceledListener? = null
    private var cameraMoveStartedListener: MapLibreMap.OnCameraMoveStartedListener? = null
    private var cameraMoveListener: MapLibreMap.OnCameraMoveListener? = null
    private var isLocationCallbackRegistered = false
    private val locationCallback: LocationEngineCallback<LocationEngineResult> =
        object : LocationEngineCallback<LocationEngineResult> {
            override fun onSuccess(result: LocationEngineResult) {
                result.lastLocation?.let { location ->
                    // FIXME I don't think I need this camera update
                    //cameraPositionState.location = location
                    onMyLocationChanged(location)
                }
            }

            override fun onFailure(exception: Exception) {
                Timber.e(exception, "Location update for the tower map failed")
            }
        }

    init {
        map.locationComponent.activateLocationComponent(
            LocationComponentActivationOptions.Builder(context, style)
                .locationComponentOptions(
                    LocationComponentOptions.builder(context)
                        .backgroundTintColor(locationSettings.backgroundTintColor.toArgb())
                        .foregroundTintColor(locationSettings.foregroundTintColor.toArgb())
                        .backgroundStaleTintColor(locationSettings.backgroundStaleTintColor.toArgb())
                        .foregroundStaleTintColor(locationSettings.foregroundStaleTintColor.toArgb())
                        .accuracyColor(locationSettings.accuracyColor.toArgb())
                        .pulseEnabled(locationSettings.pulseEnabled)
                        .pulseColor(locationSettings.pulseColor.toArgb())
                        .build()
                )
                .locationEngineRequest(
                    LocationEngineRequest.Builder(LOCATION_REQUEST_INTERVAL)
                        .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                        .setFastestInterval(LOCATION_REQUEST_INTERVAL)
                        .build()
                )
                .build()
        )

        locationEngine = map.locationComponent.locationEngine
        if (locationEngine != null) {
            val request = LocationEngineRequest.Builder(LOCATION_REQUEST_INTERVAL)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setFastestInterval(LOCATION_REQUEST_INTERVAL)
                .build()

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationEngine?.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )
                isLocationCallbackRegistered = true
            }
        }

        // Set up tower click listener. Features carry only the tower id; the caller resolves
        // ids against its own tower state. Search results take priority over regular towers.
        onTowersClick?.let { clickHandler ->
            val listener = MapLibreMap.OnMapClickListener { point ->
                val screenPoint = map.projection.toScreenLocation(point)
                val searchFeatures = map.queryRenderedFeatures(screenPoint, SEARCH_TOWER_LAYER_KEY)
                val features = searchFeatures.ifEmpty {
                    map.queryRenderedFeatures(screenPoint, TOWER_LAYER_KEY)
                }
                val ids = features.mapNotNull { it.properties()?.get(TOWER_ID_PROPERTY)?.asString }
                if (ids.isNotEmpty()) {
                    clickHandler(ids)
                    true
                } else {
                    false
                }
            }
            map.addOnMapClickListener(listener)
            mapClickListener = listener
        }

        cameraPositionState.setMap(map)
    }

    fun cleanup() {
        try {
            // Remove location updates first - only if they were registered
            if (isLocationCallbackRegistered && locationEngine != null) {
                try {
                    locationEngine?.removeLocationUpdates(locationCallback)
                    isLocationCallbackRegistered = false
                    Timber.d("Successfully removed location updates from MapPropertiesNode")
                } catch (e: Exception) {
                    Timber.w(e, "Failed to remove location updates during cleanup")
                }
            }
            locationEngine = null

            // Disable location component to prevent any further updates
            try {
                val locationComponent = map.locationComponent
                if (locationComponent != null && locationComponent.isLocationComponentActivated) {
                    // Force disable the component
                    locationComponent.isLocationComponentEnabled = false

                    // Try to stop the location engine if accessible
                    locationComponent.locationEngine?.removeLocationUpdates(locationCallback)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to disable location component during cleanup")
            }

            // Remove the listeners this node registered so nothing fires after cleanup
            try {
                cameraIdleListener?.let { map.removeOnCameraIdleListener(it) }
                cameraMoveCancelListener?.let { map.removeOnCameraMoveCancelListener(it) }
                cameraMoveStartedListener?.let { map.removeOnCameraMoveStartedListener(it) }
                cameraMoveListener?.let { map.removeOnCameraMoveListener(it) }
                mapClickListener?.let { map.removeOnMapClickListener(it) }
            } catch (e: Exception) {
                Timber.w(e, "Failed to remove map listeners during cleanup")
            }
            cameraIdleListener = null
            cameraMoveCancelListener = null
            cameraMoveStartedListener = null
            cameraMoveListener = null
            mapClickListener = null

            // Clear the camera position state map reference
            cameraPositionState.setMap(null)
        } catch (e: Exception) {
            Timber.e(e, "Error during MapPropertiesNode cleanup")
        }
    }

    var cameraPositionState = cameraPositionState
        set(value) {
            if (value == field) return
            field.setMap(null)
            field = value
            value.setMap(map)
        }

    override fun onAttached() {
        val idle = MapLibreMap.OnCameraIdleListener {
            cameraPositionState.isMoving = false
            // addOnCameraIdleListener is only invoked when the camera position
            // is changed via .animate(). To handle updating state when .move()
            // is used, it's necessary to set the camera's position here as well
            cameraPositionState.rawPosition = map.cameraPosition
            // Updating user location on every camera move due to lack of a better location updates API.
            cameraPositionState.location = map.locationComponent.lastKnownLocation
        }
        val moveCancel = MapLibreMap.OnCameraMoveCanceledListener {
            cameraPositionState.isMoving = false
        }
        val moveStarted = MapLibreMap.OnCameraMoveStartedListener {
            cameraPositionState.cameraMoveStartedReason = CameraMoveStartedReason.fromInt(it)
            cameraPositionState.isMoving = true
        }
        val move = MapLibreMap.OnCameraMoveListener {
            cameraPositionState.rawPosition = map.cameraPosition
            // Updating user location on every camera move due to lack of a better location updates API.
            cameraPositionState.location = map.locationComponent.lastKnownLocation
        }
        map.addOnCameraIdleListener(idle)
        map.addOnCameraMoveCancelListener(moveCancel)
        map.addOnCameraMoveStartedListener(moveStarted)
        map.addOnCameraMoveListener(move)
        cameraIdleListener = idle
        cameraMoveCancelListener = moveCancel
        cameraMoveStartedListener = moveStarted
        cameraMoveListener = move
        map.locationComponent.addOnCameraTrackingChangedListener(object :
            OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() {}

            override fun onCameraTrackingChanged(currentMode: Int) {
                cameraPositionState.rawCameraMode = CameraMode.fromInternal(currentMode)
            }
        })
    }

    override fun onRemoved() {
        Timber.d("MapPropertiesNode onRemoved called - performing cleanup")
        cleanup()
    }

    override fun onCleared() {
        Timber.d("MapPropertiesNode onCleared called - performing cleanup")
        cleanup()
    }
}

/**
 * Used to keep the primary map properties up to date. This should never leave the map composition.
 */
@SuppressLint("MissingPermission")
@Suppress("NOTHING_TO_INLINE")
@Composable
internal inline fun MapUpdater(
    cameraPositionState: CameraPositionState,
    locationSettings: MapLocationSettings,
    uiSettings: MapUiSettings,
    symbolManagerSettings: MapSymbolManagerSettings,
    paddingInsets: PaddingValues,
    noinline onMyLocationChanged: (Location) -> Unit,
    noinline onTowersClick: ((List<String>) -> Unit)? = null,
) {
    val mapApplier = currentComposer.applier as MapApplier
    val map = mapApplier.map
    val style = mapApplier.style
    val symbolManager = mapApplier.symbolManager
    val context = LocalContext.current

    val statusBarHeight = paddingInsets.calculateTopPadding()
    val totalTopPaddingPxPx = with(LocalDensity.current) { (statusBarHeight + 4.dp).toPx().toInt() }

    ComposeNode<MapPropertiesNode, MapApplier>(
        factory = {
            MapPropertiesNode(
                map = map,
                style = style,
                context = context,
                cameraPositionState = cameraPositionState,
                locationSettings = locationSettings,
                onMyLocationChanged = onMyLocationChanged,
                onTowersClick = onTowersClick,
            )
        },
        update = {
            set(locationSettings.locationEnabled) {
                map.locationComponent.isLocationComponentEnabled = it
            }

            map.uiSettings.isLogoEnabled = false
            set(uiSettings.attributionEnabled) { map.uiSettings.isAttributionEnabled = it }
            map.uiSettings.compassGravity = Gravity.END or Gravity.TOP
            map.uiSettings.setCompassMargins(0, totalTopPaddingPxPx, 24, 0)
            set(uiSettings.compassEnabled) { map.uiSettings.isCompassEnabled = it }
            set(uiSettings.rotationGesturesEnabled) { map.uiSettings.isRotateGesturesEnabled = it }
            set(uiSettings.scrollGesturesEnabled) { map.uiSettings.isScrollGesturesEnabled = it }
            set(uiSettings.tiltGesturesEnabled) { map.uiSettings.isTiltGesturesEnabled = it }
            set(uiSettings.zoomGesturesEnabled) { map.uiSettings.isZoomGesturesEnabled = it }

            set(symbolManagerSettings.iconAllowOverlap) { symbolManager.iconAllowOverlap = it }
            set(symbolManagerSettings.iconIgnorePlacement) {
                symbolManager.iconIgnorePlacement = it
            }
            set(symbolManagerSettings.textAllowOverlap) { symbolManager.textAllowOverlap = it }
            set(symbolManagerSettings.textIgnorePlacement) {
                symbolManager.textIgnorePlacement = it
            }

            update(cameraPositionState) { this.cameraPositionState = it }
        }
    )
}
