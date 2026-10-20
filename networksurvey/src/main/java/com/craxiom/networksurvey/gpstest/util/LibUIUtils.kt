package com.craxiom.networksurvey.gpstest.util

import android.content.Context
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.model.GnssType

/** UI helpers from the GPSTest fork that the GNSS view still uses. */
object LibUIUtils {
    /**
     * Returns the display name for the given GnssType
     * @param context
     * @param gnssType
     * @return the display name for the given GnssType
     */
    fun getGnssDisplayName(context: Context, gnssType: GnssType?): String {
        return when (gnssType) {
            GnssType.NAVSTAR -> context.resources.getString(R.string.sky_legend_shape_navstar)
            GnssType.GALILEO -> context.resources.getString(R.string.sky_legend_shape_galileo)
            GnssType.GLONASS -> context.resources.getString(R.string.sky_legend_shape_glonass)
            GnssType.BEIDOU -> context.resources.getString(R.string.sky_legend_shape_beidou)
            GnssType.QZSS -> context.resources.getString(R.string.sky_legend_shape_qzss)
            GnssType.IRNSS -> context.resources.getString(R.string.sky_legend_shape_irnss)
            GnssType.SBAS -> context.resources.getString(R.string.sbas)
            GnssType.UNKNOWN -> context.resources.getString(R.string.unknown)
            else -> context.resources.getString(R.string.unknown)
        }
    }
}