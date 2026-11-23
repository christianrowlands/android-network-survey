package com.craxiom.networksurvey.privacy

import com.craxiom.networksurvey.GpsListener
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The pure inside/outside rules for privacy zones, free of Android types so they are unit tested on
 * the JVM. [PrivacyZoneEngine] adds the sticky per-session state on top.
 *
 * - A fix is usable when it has an accuracy of [MAX_FIX_ACCURACY_METERS] or better and is at most
 *   [MAX_FIX_AGE_MS] old, on every API level.
 * - A usable fix is inside a zone when its distance to the center minus its accuracy is within the
 *   radius, so a coarse fix near a zone fails closed (the same test as iOS).
 */
object PrivacyZoneRules {

    /** The worst accuracy a fix may have and still decide. A coarser fix makes no new decision. */
    const val MAX_FIX_ACCURACY_METERS = 500f

    /** The oldest a fix may be and still decide, the same limit records use for their location. */
    const val MAX_FIX_AGE_MS = GpsListener.LOCATION_AGE_THRESHOLD_MS

    /** Mean Earth radius in meters, the same value the iOS app uses. */
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /** @return The great-circle distance in meters between two WGS84 points (haversine). */
    @JvmStatic
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    /**
     * @param nowElapsedRealtimeMs The current time on the elapsed realtime clock.
     * @return True if [fix] may decide: it has an accuracy of [MAX_FIX_ACCURACY_METERS] or better and
     * was taken at most [MAX_FIX_AGE_MS] ago.
     */
    @JvmStatic
    fun isUsableFix(fix: PrivacyZoneFix?, nowElapsedRealtimeMs: Long): Boolean {
        if (fix == null || !PrivacyZone.isValidCenter(fix.latitude, fix.longitude)) return false
        val accuracy = fix.accuracyMeters ?: return false
        if (!accuracy.isFinite() || accuracy < 0f || accuracy > MAX_FIX_ACCURACY_METERS) return false
        val takenAt = fix.elapsedRealtimeMs ?: return false
        val age = nowElapsedRealtimeMs - takenAt
        // A fix from the future means the clocks disagree; treat it as just taken.
        return age <= MAX_FIX_AGE_MS
    }

    /**
     * @param accuracyMeters The fix's accuracy; it widens the test so it fails closed. Null or
     *                       negative counts as zero.
     * @return True if the point, widened by [accuracyMeters], may be inside [zone].
     */
    @JvmStatic
    fun contains(zone: PrivacyZone, latitude: Double, longitude: Double, accuracyMeters: Float?): Boolean {
        val widen = accuracyMeters?.takeIf { it.isFinite() && it > 0f }?.toDouble() ?: 0.0
        return distanceMeters(zone.latitude, zone.longitude, latitude, longitude) - widen <= zone.radiusMeters
    }

    /**
     * @return The zone the point may be inside, or null when it is outside every zone. When it may be
     * inside several, the one with the nearest center, so the name shown is the most likely place.
     */
    @JvmStatic
    fun zoneContaining(zones: List<PrivacyZone>, latitude: Double, longitude: Double, accuracyMeters: Float?): PrivacyZone? =
        zones.asSequence()
            .filter { contains(it, latitude, longitude, accuracyMeters) }
            .minByOrNull { distanceMeters(it.latitude, it.longitude, latitude, longitude) }

    /** @return The zone [fix] may be inside (see the other overload), ignoring whether it is usable. */
    @JvmStatic
    fun zoneContaining(zones: List<PrivacyZone>, fix: PrivacyZoneFix): PrivacyZone? =
        zoneContaining(zones, fix.latitude, fix.longitude, fix.accuracyMeters)
}
