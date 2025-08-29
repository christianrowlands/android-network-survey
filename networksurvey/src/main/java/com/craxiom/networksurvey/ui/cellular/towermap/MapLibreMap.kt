package com.craxiom.networksurvey.ui.cellular.towermap

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.location.Location
import android.os.Bundle
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.craxiom.networksurvey.data.api.Tower
import kotlinx.coroutines.awaitCancellation
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * A Compose container for a MapLibre [MapView].
 * @param styleUri URI of the MapLibre style JSON
 * @param modifier Modifier for the MapView
 * @param images Optional map of image IDs to drawable resource IDs
 * @param cameraPositionState Controls or observes camera state
 * @param uiSettings UI-specific map settings
 */
@Composable
fun MapLibreMap(
    styleUri: String,
    modifier: Modifier = Modifier,
    paddingInsets: PaddingValues,
    images: Map<String, Int> = mapOf(),
    cameraPositionState: CameraPositionState = rememberCameraPositionState(),
    uiSettings: MapUiSettings = DefaultMapUiSettings,
    symbolManagerSettings: MapSymbolManagerSettings = DefaultMapSymbolManagerSettings,
    locationSettings: MapLocationSettings = DefaultMapLocationSettings,
    onMapReady: ((MapView, MapLibreMap, Style) -> Unit)? = null,
    onMyLocationChanged: (Location) -> Unit = {},
    onTowerClick: ((Tower) -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    if (LocalInspectionMode.current) {
        Box(modifier = modifier.background(Color.LightGray)) {
            Text("[Map]", modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
    MapLifecycle(mapView, locationSettings)

    // Remember state
    val currentCameraState by rememberUpdatedState(cameraPositionState)
    val currentUiSettings by rememberUpdatedState(uiSettings)
    val currentImages by rememberUpdatedState(images)
    val currentMapLocationSettings by rememberUpdatedState(locationSettings)
    val currentSymbolManagerSettings by rememberUpdatedState(symbolManagerSettings)
    val currentOnMapReady by rememberUpdatedState(onMapReady)
    val parentComposition = rememberCompositionContext()

    LaunchedEffect(styleUri, images) {
        disposingComposition {
            parentComposition.newComposition(
                context,
                mapView,
                styleUri,
                currentImages,
                currentOnMapReady
            ) {
                MapUpdater(
                    cameraPositionState = currentCameraState,
                    uiSettings = currentUiSettings,
                    locationSettings = currentMapLocationSettings,
                    symbolManagerSettings = currentSymbolManagerSettings,
                    paddingInsets = paddingInsets,
                    onMyLocationChanged = onMyLocationChanged,
                    onTowerClick = onTowerClick,
                )
                content()
            }
        }
    }
}

private suspend inline fun disposingComposition(factory: () -> Composition) {
    val composition = factory()
    try {
        awaitCancellation()
    } finally {
        composition.dispose()
    }
}

private suspend fun CompositionContext.newComposition(
    context: Context,
    mapView: MapView,
    styleUri: String,
    images: Map<String, Int>,
    onMapReady: ((MapView, MapLibreMap, Style) -> Unit)?,
    content: @Composable () -> Unit
): Composition = suspendCoroutine { cont ->
    // 1) Wait for the MapLibreMap instance
    mapView.getMapAsync { map ->
        // 2) Ask MapLibre to load the style; this callback only fires once it's fully parsed & ready
        map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
            // 3) Inject any custom images into the style
            images.forEach { (id, res) ->
                AppCompatResources.getDrawable(context, res)
                    ?.let { drawable -> style.addImage(id, drawable) }
            }
            // 4) Let anyone know the map+style is now ready
            onMapReady?.invoke(mapView, map, style)
            // 5) Only now can we safely build the SymbolManager
            val symbolManager = SymbolManager(mapView, map, style)
            // 6) Finally hook up your Compose tree
            val composition = Composition(
                MapApplier(map, style, symbolManager),
                this@newComposition
            ).apply {
                setContent(content)
            }
            cont.resume(composition)
        }
    }
}

/**
 * Registers lifecycle observers to drive MapView lifecycle events from Compose.
 */
@Composable
private fun MapLifecycle(mapView: MapView, locationSettings: MapLocationSettings) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val prev = remember { mutableStateOf(Lifecycle.Event.ON_CREATE) }

    DisposableEffect(context, lifecycle, mapView) {
        val observer = mapView.lifecycleObserver(prev, locationSettings)
        val callbacks = mapView.componentCallbacks()
        lifecycle.addObserver(observer)
        context.registerComponentCallbacks(callbacks)
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(callbacks)
        }
    }
    DisposableEffect(mapView) {
        onDispose {
            // First ensure location updates are stopped before destroying the map
            try {
                // Try direct access first
                val mapField = mapView.javaClass.getDeclaredField("mapLibreMap")
                mapField.isAccessible = true
                val map = mapField.get(mapView) as? MapLibreMap

                if (map != null) {
                    // Force stop all location updates
                    if (map.locationComponent.isLocationComponentActivated) {
                        map.locationComponent.isLocationComponentEnabled = false
                        // Can't remove updates without callback reference
                    }

                    // Clear any pending camera idle listeners
                    map.removeOnCameraIdleListener { }
                }
            } catch (e: Exception) {
                // Try async as fallback
                try {
                    mapView.getMapAsync { map ->
                        if (map.locationComponent.isLocationComponentActivated) {
                            map.locationComponent.isLocationComponentEnabled = false
                            // Can't remove updates without callback reference
                        }
                        map.removeOnCameraIdleListener { }
                    }
                } catch (ex: Exception) {
                    // Ignore cleanup errors
                }
            }

            // Then destroy the map view
            mapView.onDestroy()
            mapView.removeAllViews()
        }
    }
}

private fun MapView.lifecycleObserver(prev: MutableState<Lifecycle.Event>, locationSettings: MapLocationSettings) =
    LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> if (prev.value != Lifecycle.Event.ON_STOP) this.onCreate(
                Bundle()
            )

            Lifecycle.Event.ON_START -> this.onStart()
            Lifecycle.Event.ON_RESUME -> {
                this.onResume()
                // Re-enable location component if it should be enabled
                try {
                    val mapField = this.javaClass.getDeclaredField("mapLibreMap")
                    mapField.isAccessible = true
                    val map = mapField.get(this) as? MapLibreMap
                    
                    if (map != null && map.locationComponent.isLocationComponentActivated && locationSettings.locationEnabled) {
                        map.locationComponent.isLocationComponentEnabled = true
                    }
                } catch (e: Exception) {
                    // Try async as fallback
                    try {
                        this.getMapAsync { map ->
                            if (map.locationComponent.isLocationComponentActivated && locationSettings.locationEnabled) {
                                map.locationComponent.isLocationComponentEnabled = true
                            }
                        }
                    } catch (ex: Exception) {
                        // Ignore errors during resume
                    }
                }
            }
            Lifecycle.Event.ON_PAUSE -> this.onPause()
            Lifecycle.Event.ON_STOP -> {
                // Immediately disable location component to prevent updates after destroy
                try {
                    // Access the map directly if available (it should be by this point)
                    val mapField = this.javaClass.getDeclaredField("mapLibreMap")
                    mapField.isAccessible = true
                    val map = mapField.get(this) as? MapLibreMap

                    if (map != null && map.locationComponent.isLocationComponentActivated) {
                        // Disable location updates immediately
                        map.locationComponent.isLocationComponentEnabled = false

                        // Force stop location engine
                        map.locationComponent.locationEngine
                        // We can't remove updates without the callback reference, just disable the component
                    }
                } catch (e: Exception) {
                    // Fallback to async approach if reflection fails
                    try {
                        this.getMapAsync { map ->
                            if (map.locationComponent.isLocationComponentActivated) {
                                map.locationComponent.isLocationComponentEnabled = false
                                // Can't remove updates without callback reference
                            }
                        }
                    } catch (ex: Exception) {
                        // Ignore errors during cleanup
                    }
                }
                this.onStop()
            }

            else -> {}
        }
        prev.value = event
    }

private fun MapView.componentCallbacks() = object : ComponentCallbacks {
    override fun onConfigurationChanged(config: Configuration) {}
    override fun onLowMemory() {
        this.onLowMemory()
    }
}
