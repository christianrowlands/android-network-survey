package com.craxiom.networksurvey.ui.cellular.model

/**
 * Enum representing the available map tile sources.
 */
enum class MapTileSource(val displayName: String) {
    MAPTILER("MapTiler"),
    OPENSTREETMAP("OpenStreetMap");

    companion object {
        fun fromString(value: String): MapTileSource {
            return entries.firstOrNull { it.name == value } ?: MAPTILER
        }
    }
}