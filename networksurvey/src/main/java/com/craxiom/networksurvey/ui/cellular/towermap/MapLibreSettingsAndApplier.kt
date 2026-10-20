package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.runtime.AbstractApplier
import androidx.compose.ui.graphics.Color
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager

internal val DefaultMapUiSettings = MapUiSettings()
internal val DefaultMapLocationSettings = MapLocationSettings()

/**
 * UI-related settings for MapLibre map controls.
 */
data class MapUiSettings(
    val compassEnabled: Boolean = true,
    val rotationGesturesEnabled: Boolean = true,
    val scrollGesturesEnabled: Boolean = true,
    val tiltGesturesEnabled: Boolean = true,
    val zoomGesturesEnabled: Boolean = true,
    /**
     * Whether the MapLibre attribution ("i") control is shown. Off by default (most maps hide it and
     * position it elsewhere); the Watchlist maps opt in and place it via [UiSettings] margins.
     */
    val attributionEnabled: Boolean = false,
)

/**
 * Location component styling and enablement settings.
 */
data class MapLocationSettings(
    val locationEnabled: Boolean = true,
    val backgroundTintColor: Color = Color.White,
    val foregroundTintColor: Color = Color.Blue,
    val backgroundStaleTintColor: Color = Color.Gray,
    val foregroundStaleTintColor: Color = Color.Red,
    val accuracyColor: Color = Color.Cyan,
    val pulseEnabled: Boolean = true,
    val pulseColor: Color = Color.Magenta
)

/**
 * Marker interface for nodes in MapApplier's tree.
 */
internal interface MapNode {
    fun onAttached() {}
    fun onRemoved() {}
    fun onCleared() {}
}

private object MapNodeRoot : MapNode

/**
 * Applies map, style, and symbol manager to Compose.
 */
internal class MapApplier(
    val map: MapLibreMap,
    val style: Style,
    val symbolManager: SymbolManager,
) : AbstractApplier<MapNode>(MapNodeRoot) {
    private val decorations = mutableListOf<MapNode>()

    override fun onClear() {
        symbolManager.deleteAll()
        decorations.forEach { it.onCleared() }
        decorations.clear()
    }

    override fun insertBottomUp(index: Int, instance: MapNode) {
        decorations.add(index, instance)
        instance.onAttached()
    }

    override fun insertTopDown(index: Int, instance: MapNode) {
        // insertBottomUp is preferred
    }

    override fun move(from: Int, to: Int, count: Int) {
        decorations.move(from, to, count)
    }

    override fun remove(index: Int, count: Int) {
        repeat(count) {
            decorations[index + it].onRemoved()
        }
        decorations.remove(index, count)
    }
}
