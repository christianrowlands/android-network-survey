package com.craxiom.networksurvey.ui.cellular.model

import org.maplibre.android.geometry.LatLng

/**
 * Data class representing a line between user location and serving cell tower.
 */
data class ServingCellLineData(
    val subscriptionId: Int,
    val startPoint: LatLng,
    val endPoint: LatLng
)