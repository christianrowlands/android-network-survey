package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.runtime.Immutable
import org.maplibre.android.style.layers.Property

@Immutable
enum class IconAnchor {
    CENTER,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    @Property.ICON_ANCHOR
    internal fun toInternal(): String = when (this) {
        CENTER -> Property.ICON_ANCHOR_CENTER
        LEFT -> Property.ICON_ANCHOR_LEFT
        RIGHT -> Property.ICON_ANCHOR_RIGHT
        TOP -> Property.ICON_ANCHOR_TOP
        BOTTOM -> Property.ICON_ANCHOR_BOTTOM
        TOP_LEFT -> Property.ICON_ANCHOR_TOP_LEFT
        TOP_RIGHT -> Property.ICON_ANCHOR_TOP_RIGHT
        BOTTOM_LEFT -> Property.ICON_ANCHOR_BOTTOM_LEFT
        BOTTOM_RIGHT -> Property.ICON_ANCHOR_BOTTOM_RIGHT
    }
}
