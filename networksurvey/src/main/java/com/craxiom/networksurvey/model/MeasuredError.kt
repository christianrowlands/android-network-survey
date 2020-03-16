package com.craxiom.networksurvey.model

/**
 * Model class for holding measured error between two locations.
 *
 * Originally from the GPS Test open source Android app.  https://github.com/barbeau/gpstest
 */
data class MeasuredError(val error: Float,
                         val vertError: Double = Double.NaN)