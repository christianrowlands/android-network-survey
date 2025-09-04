package com.craxiom.networksurvey.ui.cellular.model

import org.maplibre.android.geometry.LatLng

/**
 * Data class representing a line between user location and serving cell tower.
 *
 * @property subscriptionId The ID of the subscription (SIM) this line is for
 * @property startPoint The user's current location
 * @property endPoint The serving cell tower location
 * @property distanceMeters The distance between start and end points in meters
 */
data class ServingCellLineData(
    val subscriptionId: Int,
    val startPoint: LatLng,
    val endPoint: LatLng,
    val distanceMeters: Double
)