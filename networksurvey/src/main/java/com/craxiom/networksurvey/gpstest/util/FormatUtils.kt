package com.craxiom.networksurvey.gpstest.util

/**
 * Provides access to formatting utilities for view in the UI
 *
 * Pulled from the GPSTest Android app source code.
 **/
object FormatUtils {
    /**
     * This function prevents really small speed values from being displayed in scientific notation
     * when converted to a string (for JSON serialization). If the speed is less than 0.1, then
     * the speed is set to 0.
     */
    @JvmStatic
    fun formatSpeed(speed: Float): Float {
        // If less then 0.1 then don't set the value
        return if (speed < 0.1) {
            0f
        } else {
            speed
        }
    }
}
