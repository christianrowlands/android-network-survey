package com.craxiom.networksurvey.gpstest.ui.sky

import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import kotlin.math.cos
import kotlin.math.sin

/**
 * A point on the sky disk, in units of the horizon radius: (0, 0) is the zenith, distance 1 is the
 * horizon, x grows to the right and y grows down, as on a canvas.
 */
data class SkyPoint(val x: Float, val y: Float)

/** The mark a constellation draws on the sky plot and in its legend. */
enum class SatelliteShape { CIRCLE, SQUARE, TRIANGLE, PENTAGON, HEXAGON, OVAL, DIAMOND, NONE }

/**
 * Where a C/N0 falls on the color scale: between stop [index] and the next one, [fraction] of the
 * way (0 at the stop itself). The last stop has fraction 0.
 */
data class Cn0Stop(val index: Int, val fraction: Float)

/**
 * The geometry and color scale of the GNSS sky plot, with no Android or Compose types so the plot,
 * the averages and the legend share one tested definition.
 */
object SkyProjection {

    private const val ZENITH_DEGREES = 90f
    private const val FULL_SCALE_DB_HZ_MIN = 10f
    private const val FULL_SCALE_DB_HZ_MAX = 45f

    /**
     * The C/N0 values (dB-Hz) of the color scale's stops, weakest first: gray at 10 and below, then
     * red, yellow, and green at 45 and above, blended in between (GPSTest's scale).
     */
    val CN0_STOPS = floatArrayOf(FULL_SCALE_DB_HZ_MIN, 21.67f, 33.3f, FULL_SCALE_DB_HZ_MAX)

    /**
     * @param elevationDegrees 90 at the zenith, 0 at the horizon; values outside are clamped.
     * @param azimuthDegrees Clockwise from north.
     * @param headingDegrees Where the top of the phone points, clockwise from north; the plot turns
     * so that direction is up.
     * @return The satellite's point on the disk, or null when GnssStatus gave no angles (it reports
     * both as 0 then).
     */
    fun project(elevationDegrees: Float, azimuthDegrees: Float, headingDegrees: Float = 0f): SkyPoint? {
        if (elevationDegrees == SatelliteStatus.NO_DATA && azimuthDegrees == SatelliteStatus.NO_DATA) return null
        if (elevationDegrees.isNaN() || azimuthDegrees.isNaN()) return null
        val radius = 1f - elevationDegrees.coerceIn(0f, ZENITH_DEGREES) / ZENITH_DEGREES
        val angle = Math.toRadians((azimuthDegrees - headingDegrees).toDouble())
        return SkyPoint(x = (radius * sin(angle)).toFloat(), y = (-radius * cos(angle)).toFloat())
    }

    /** @return The mark for [gnssType]; an unknown constellation draws only its number, as before. */
    fun shape(gnssType: GnssType): SatelliteShape = when (gnssType) {
        GnssType.NAVSTAR -> SatelliteShape.CIRCLE
        GnssType.GLONASS -> SatelliteShape.SQUARE
        GnssType.GALILEO -> SatelliteShape.TRIANGLE
        GnssType.BEIDOU -> SatelliteShape.PENTAGON
        GnssType.QZSS -> SatelliteShape.HEXAGON
        GnssType.IRNSS -> SatelliteShape.OVAL
        GnssType.SBAS -> SatelliteShape.DIAMOND
        GnssType.UNKNOWN -> SatelliteShape.NONE
    }

    /** @return True when the satellite is heard at all (GnssStatus reports a C/N0 of 0 otherwise). */
    fun inView(cn0DbHz: Float): Boolean = cn0DbHz > SatelliteStatus.NO_DATA

    /** @return Where [cn0DbHz] falls between the [CN0_STOPS]. */
    fun cn0Stop(cn0DbHz: Float): Cn0Stop {
        val last = CN0_STOPS.lastIndex
        if (cn0DbHz <= CN0_STOPS[0]) return Cn0Stop(0, 0f)
        if (cn0DbHz >= CN0_STOPS[last]) return Cn0Stop(last, 0f)
        for (i in 0 until last) {
            val low = CN0_STOPS[i]
            val high = CN0_STOPS[i + 1]
            if (cn0DbHz < high) return Cn0Stop(i, (cn0DbHz - low) / (high - low))
        }
        return Cn0Stop(last, 0f)
    }

    /** @return [cn0DbHz] along the averages bar, 0 at 10 dB-Hz and 1 at 45, clamped. */
    fun meterFraction(cn0DbHz: Float): Float =
        ((cn0DbHz - FULL_SCALE_DB_HZ_MIN) / (FULL_SCALE_DB_HZ_MAX - FULL_SCALE_DB_HZ_MIN)).coerceIn(0f, 1f)
}
