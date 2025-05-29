package com.craxiom.networksurvey.ui.cellular.model

import org.maplibre.android.geometry.LatLng

/**
 * Simple wrapper data class to group together a serving cell location and the range of the cell tower.
 */
data class ServingCellLocationInfo(val location: LatLng, val range: Int = 0)