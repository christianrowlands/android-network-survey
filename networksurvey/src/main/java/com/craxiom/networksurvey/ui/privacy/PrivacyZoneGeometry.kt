package com.craxiom.networksurvey.ui.privacy

import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.ui.common.MapPoint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The math behind the privacy zone screens, free of Android and MapLibre types so it can be unit
 * tested: the radius slider's scale and the ring of points that draws a zone's circle.
 */
object PrivacyZoneGeometry {

    /** The Earth radius the ring uses, the same mean radius as the inside test. */
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /** Enough points that a 2 km circle still looks round at street zoom. */
    const val RING_SEGMENTS = 72

    /** Radii below this snap to [SMALL_STEP_METERS]. */
    private const val SMALL_LIMIT_METERS = 500

    /** Radii below this (and above [SMALL_LIMIT_METERS]) snap to [MEDIUM_STEP_METERS]. */
    private const val MEDIUM_LIMIT_METERS = 1000
    private const val SMALL_STEP_METERS = 10
    private const val MEDIUM_STEP_METERS = 25
    private const val LARGE_STEP_METERS = 50

    private val minLog = ln(PrivacyZone.MIN_RADIUS_METERS.toDouble())
    private val maxLog = ln(PrivacyZone.MAX_RADIUS_METERS.toDouble())

    /**
     * The radius for a slider [position] from 0 to 1. The scale is logarithmic, so the small radii
     * people use around a home get most of the track, and the result snaps to a round number.
     */
    fun radiusForSlider(position: Float): Int {
        val raw = exp(minLog + position.coerceIn(0f, 1f) * (maxLog - minLog))
        return snapRadius(raw.roundToInt())
    }

    /** The slider position (0 to 1) that shows [radiusMeters]. */
    fun sliderForRadius(radiusMeters: Int): Float {
        val clamped = PrivacyZone.clampRadius(radiusMeters).toDouble()
        return ((ln(clamped) - minLog) / (maxLog - minLog)).toFloat().coerceIn(0f, 1f)
    }

    /** [radiusMeters] rounded to the step for its size and kept in the allowed range. */
    fun snapRadius(radiusMeters: Int): Int {
        val step = stepFor(radiusMeters)
        val snapped = ((radiusMeters.toDouble() / step).roundToInt()) * step
        return PrivacyZone.clampRadius(snapped)
    }

    /** The next snapped radius above ([up]) or below [radiusMeters], kept in the allowed range. */
    fun steppedRadius(radiusMeters: Int, up: Boolean): Int {
        val clamped = PrivacyZone.clampRadius(radiusMeters)
        return if (up) snapRadius(clamped + stepFor(clamped)) else snapRadius(clamped - stepFor(clamped - 1))
    }

    /**
     * The radius for an accessibility adjustment that moves the slider from [currentRadius] to
     * [position]: the radius there, or the next snapped radius in that direction when the log scale
     * and the snapping would round a small step back to [currentRadius] (so TalkBack can never get
     * stuck, at 100 m for example).
     */
    fun radiusForAdjustment(currentRadius: Int, position: Float): Int {
        val target = radiusForSlider(position)
        val current = sliderForRadius(currentRadius)
        return when {
            position > current && target <= currentRadius -> steppedRadius(currentRadius, up = true)
            position < current && target >= currentRadius -> steppedRadius(currentRadius, up = false)
            else -> target
        }
    }

    private fun stepFor(radiusMeters: Int): Int = when {
        radiusMeters < SMALL_LIMIT_METERS -> SMALL_STEP_METERS
        radiusMeters < MEDIUM_LIMIT_METERS -> MEDIUM_STEP_METERS
        else -> LARGE_STEP_METERS
    }

    /**
     * The closed ring of points (the first point repeated at the end) around a circle of
     * [radiusMeters] centered on [latitude], [longitude]. Each point is the great-circle destination
     * at that distance, so the drawn edge matches the inside test at any latitude.
     */
    fun ring(latitude: Double, longitude: Double, radiusMeters: Int, segments: Int = RING_SEGMENTS): List<MapPoint> {
        val angular = radiusMeters / EARTH_RADIUS_METERS
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val points = (0 until segments).map { i ->
            val bearing = 2 * Math.PI * i / segments
            val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
            val lon2 = lon1 + atan2(sin(bearing) * sin(angular) * cos(lat1), cos(angular) - sin(lat1) * sin(lat2))
            MapPoint(Math.toDegrees(lat2), normalizeLongitude(Math.toDegrees(lon2)))
        }
        return points + points.first()
    }

    /**
     * The north, south, east and west edge points of a circle, for framing it with the camera.
     */
    fun edges(latitude: Double, longitude: Double, radiusMeters: Int): List<MapPoint> =
        ring(latitude, longitude, radiusMeters, segments = EDGE_SEGMENTS).dropLast(1)

    private const val EDGE_SEGMENTS = 4
    private const val HALF_TURN_DEGREES = 180.0
    private const val FULL_TURN_DEGREES = 360.0

    private fun normalizeLongitude(degrees: Double): Double =
        ((degrees + HALF_TURN_DEGREES) % FULL_TURN_DEGREES + FULL_TURN_DEGREES) % FULL_TURN_DEGREES - HALF_TURN_DEGREES
}
