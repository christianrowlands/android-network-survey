package com.craxiom.networksurvey.ui.cellular.model

import org.osmdroid.util.GeoPoint

/**
 * Simple wrapper data class to group together a tower maker GeoPoint and the range of the cell tower.
 */
data class GeoPointRange(val geoPoint: GeoPoint, val range: Int = 0)
