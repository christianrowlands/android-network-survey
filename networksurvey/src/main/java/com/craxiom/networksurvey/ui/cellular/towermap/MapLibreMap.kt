package com.craxiom.networksurvey.ui.cellular.towermap

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.location.Location
import android.os.Bundle
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import kotlinx.coroutines.awaitCancellation
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import com.craxiom.networksurvey.ui.cellular.Tower
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
    MapLifecycle(mapView)

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

private suspend inline fun CompositionContext.newComposition(
    context: Context,
    mapView: MapView,
    styleUri: String,
    images: Map<String, Int>,
    noinline onMapReady: ((MapView, MapLibreMap, Style) -> Unit)?,
    noinline content: @Composable () -> Unit
): Composition {
    val map = mapView.awaitMap()
    val style = map.awaitStyle(context, styleUri, images)
    val symbolManager = SymbolManager(mapView, map, style)

    // Call the onMapReady callback if provided
    onMapReady?.invoke(mapView, map, style)

    return Composition(
        MapApplier(map, style, symbolManager),
        this
    ).apply {
        setContent(content)
    }
}

private suspend fun MapView.awaitMap(): MapLibreMap = suspendCoroutine { cont ->
    getMapAsync(OnMapReadyCallback { map -> cont.resume(map) })
}

private suspend fun MapLibreMap.awaitStyle(
    context: Context,
    styleUri: String,
    images: Map<String, Int>
): Style = suspendCoroutine { cont ->
    setStyle(Style.Builder().fromUri(styleUri).apply {
        images.forEach { (id, res) ->
            withImage(id, requireNotNull(AppCompatResources.getDrawable(context, res)))
        }
    }) { style -> cont.resume(style) }
}

/**
 * Registers lifecycle observers to drive MapView lifecycle events from Compose.
 */
@Composable
private fun MapLifecycle(mapView: MapView) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val prev = remember { mutableStateOf(Lifecycle.Event.ON_CREATE) }

    DisposableEffect(context, lifecycle, mapView) {
        val observer = mapView.lifecycleObserver(prev)
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
            mapView.onDestroy()
            mapView.removeAllViews()
        }
    }
}

private fun MapView.lifecycleObserver(prev: MutableState<Lifecycle.Event>) =
    LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> if (prev.value != Lifecycle.Event.ON_STOP) this.onCreate(
                Bundle()
            )

            Lifecycle.Event.ON_START -> this.onStart()
            Lifecycle.Event.ON_RESUME -> this.onResume()
            Lifecycle.Event.ON_PAUSE -> this.onPause()
            Lifecycle.Event.ON_STOP -> this.onStop()
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
