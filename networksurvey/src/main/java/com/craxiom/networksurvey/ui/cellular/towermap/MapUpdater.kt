package com.craxiom.networksurvey.ui.cellular.towermap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
) : MapNode {
    private val locationCallback: LocationEngineCallback<LocationEngineResult>
        get() {
            val locationCallback = object : LocationEngineCallback<LocationEngineResult> {
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
            return locationCallback
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

        val locationEngine = map.locationComponent.locationEngine
        val locationCallback = locationCallback
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
                locationEngine.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
        }

        cameraPositionState.setMap(map)
    }

    private lateinit var locationEngine: LocationEngine

    var cameraPositionState = cameraPositionState
        set(value) {
            if (value == field) return
            field.setMap(null)
            field = value
            value.setMap(map)
        }

    override fun onAttached() {
        map.addOnCameraIdleListener {
            cameraPositionState.isMoving = false
            // addOnCameraIdleListener is only invoked when the camera position
            // is changed via .animate(). To handle updating state when .move()
            // is used, it's necessary to set the camera's position here as well
            cameraPositionState.rawPosition = map.cameraPosition
            // Updating user location on every camera move due to lack of a better location updates API.
            cameraPositionState.location = map.locationComponent.lastKnownLocation
        }
        map.addOnCameraMoveCancelListener {
            cameraPositionState.isMoving = false
        }
        map.addOnCameraMoveStartedListener {
            cameraPositionState.cameraMoveStartedReason = CameraMoveStartedReason.fromInt(it)
            cameraPositionState.isMoving = true
        }
        map.addOnCameraMoveListener {
            cameraPositionState.rawPosition = map.cameraPosition
            // Updating user location on every camera move due to lack of a better location updates API.
            cameraPositionState.location = map.locationComponent.lastKnownLocation
        }
        map.locationComponent.addOnCameraTrackingChangedListener(object :
            OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() {}

            override fun onCameraTrackingChanged(currentMode: Int) {
                cameraPositionState.rawCameraMode = CameraMode.fromInternal(currentMode)
            }
        })
    }

    override fun onRemoved() {
        cameraPositionState.setMap(null)
        locationEngine.removeLocationUpdates(locationCallback)
    }

    override fun onCleared() {
        cameraPositionState.setMap(null)
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
    noinline onMyLocationChanged: (Location) -> Unit,
) {
    val mapApplier = currentComposer.applier as MapApplier
    val map = mapApplier.map
    val style = mapApplier.style
    val symbolManager = mapApplier.symbolManager
    val context = LocalContext.current
    ComposeNode<MapPropertiesNode, MapApplier>(
        factory = {
            MapPropertiesNode(
                map = map,
                style = style,
                context = context,
                cameraPositionState = cameraPositionState,
                locationSettings = locationSettings,
                onMyLocationChanged = onMyLocationChanged,
            )
        },
        update = {
            set(locationSettings.locationEnabled) {
                map.locationComponent.isLocationComponentEnabled = it
            }

            map.uiSettings.compassGravity = Gravity.END or Gravity.BOTTOM
            map.uiSettings.setCompassMargins(0, 0, 24, 20)
            map.uiSettings.logoGravity = Gravity.CENTER
            set(uiSettings.compassEnabled) { map.uiSettings.isCompassEnabled = it }
            set(uiSettings.rotationGesturesEnabled) { map.uiSettings.isRotateGesturesEnabled = it }
            set(uiSettings.scrollGesturesEnabled) { map.uiSettings.isScrollGesturesEnabled = it }
            set(uiSettings.tiltGesturesEnabled) { map.uiSettings.isTiltGesturesEnabled = it }
            set(uiSettings.zoomGesturesEnabled) { map.uiSettings.isZoomGesturesEnabled = it }
            set(uiSettings.logoGravity) { map.uiSettings.logoGravity = it }
            set(uiSettings.attributionGravity) { map.uiSettings.attributionGravity = it }
            set(uiSettings.attributionTintColor) { map.uiSettings.setAttributionTintColor(it.toArgb()) }

            set(symbolManagerSettings.iconAllowOverlap) { symbolManager.iconAllowOverlap = it }
            set(symbolManagerSettings.iconIgnorePlacement) { symbolManager.iconIgnorePlacement = it }
            set(symbolManagerSettings.textAllowOverlap) { symbolManager.textAllowOverlap = it }
            set(symbolManagerSettings.textIgnorePlacement) { symbolManager.textIgnorePlacement = it }

            update(cameraPositionState) { this.cameraPositionState = it }
        }
    )
}
