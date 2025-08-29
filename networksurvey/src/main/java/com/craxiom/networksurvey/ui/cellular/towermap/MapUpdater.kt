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
import com.craxiom.networksurvey.data.api.Tower
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
    onTowerClick: ((Tower) -> Unit)? = null,
) : MapNode {
    private var locationEngine: LocationEngine? = null
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
            }
        }

        // Set up tower click listener
        onTowerClick?.let { clickHandler ->
            map.addOnMapClickListener { point ->
                // Query for tower features at the click point - check both regular towers and search results
                val regularFeatures = map.queryRenderedFeatures(
                    map.projection.toScreenLocation(point),
                    TOWER_LAYER_KEY
                )

                val searchFeatures = map.queryRenderedFeatures(
                    map.projection.toScreenLocation(point),
                    SEARCH_TOWER_LAYER_KEY
                )

                // Prioritize search results over regular towers
                val features = searchFeatures.ifEmpty { regularFeatures }

                if (features.isNotEmpty()) {
                    val feature = features[0]
                    val properties = feature.properties()

                    // Convert feature properties back to Tower object
                    if (properties != null) {
                        try {
                            val tower = Tower(
                                lat = properties.get("lat").asDouble,
                                lon = properties.get("lon").asDouble,
                                mcc = properties.get("mcc").asInt,
                                mnc = properties.get("mnc").asInt,
                                area = properties.get("area").asInt,
                                cid = properties.get("cid").asLong,
                                unit = properties.get("unit").asInt,
                                averageSignal = properties.get("averageSignal").asInt,
                                range = properties.get("range").asInt,
                                samples = properties.get("samples").asInt,
                                changeable = properties.get("changeable").asInt,
                                createdAt = properties.get("createdAt").asLong,
                                updatedAt = properties.get("updatedAt").asLong,
                                radio = properties.get("radio").asString,
                                source = properties.get("source").asString
                            )
                            clickHandler(tower)
                            return@addOnMapClickListener true
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing tower data from feature")
                        }
                    }
                }
                false
            }
        }

        cameraPositionState.setMap(map)
    }

    fun cleanup() {
        try {
            // Remove location updates first - force synchronous removal
            locationEngine?.removeLocationUpdates(locationCallback)
            locationEngine = null

            // Disable location component to prevent any further updates
            try {
                val locationComponent = map.locationComponent
                if (locationComponent != null && locationComponent.isLocationComponentActivated) {
                    // Force disable the component
                    locationComponent.isLocationComponentEnabled = false

                    // Engine reference is already stopped in our own locationEngine variable
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to disable location component during cleanup")
            }

            // Remove all map listeners to prevent callbacks after cleanup
            try {
                map.removeOnCameraIdleListener { }
                map.removeOnCameraMoveCancelListener { }
                map.removeOnCameraMoveStartedListener { }
                map.removeOnCameraMoveListener { }
                map.removeOnMapClickListener { true }
            } catch (e: Exception) {
                Timber.w(e, "Failed to remove map listeners during cleanup")
            }

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
        cleanup()
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
    paddingInsets: PaddingValues,
    noinline onMyLocationChanged: (Location) -> Unit,
    noinline onTowerClick: ((Tower) -> Unit)? = null,
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
                onTowerClick = onTowerClick,
            )
        },
        update = {
            set(locationSettings.locationEnabled) {
                map.locationComponent.isLocationComponentEnabled = it
            }

            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false
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
