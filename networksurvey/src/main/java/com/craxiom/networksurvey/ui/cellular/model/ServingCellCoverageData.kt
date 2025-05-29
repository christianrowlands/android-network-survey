package com.craxiom.networksurvey.ui.cellular.model

import org.maplibre.android.geometry.LatLng

/**
 * Data class representing coverage circle for a serving cell tower.
 */
data class ServingCellCoverageData(
    val subscriptionId: Int,
    val center: LatLng,
    val radiusMeters: Int
)