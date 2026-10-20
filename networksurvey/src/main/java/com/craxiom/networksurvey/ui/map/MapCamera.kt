package com.craxiom.networksurvey.ui.map

/**
 * Where the Map's camera was: its center, zoom and rotation. Kept by [MapTabViewModel] while the
 * MapView is recreated on each visit to the tab, so the map opens where it was left.
 */
data class MapCamera(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double = 0.0,
)
