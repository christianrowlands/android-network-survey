package com.craxiom.networksurvey.privacy

/**
 * A circle on the map where the survey saves and sends nothing (see [PrivacyZoneRules] for the
 * inside test). Zones hide where the phone was, not what it heard.
 *
 * @property id           Unique and stable, so an organization's zones can later be merged with the
 *                        user's without clashing.
 * @property name         What the user called it, shown in the list and in the Paused state. May be
 *                        blank.
 * @property latitude     The center, WGS84 degrees.
 * @property longitude    The center, WGS84 degrees.
 * @property radiusMeters Between [MIN_RADIUS_METERS] and [MAX_RADIUS_METERS].
 * @property source       Who set the zone.
 */
data class PrivacyZone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val source: PrivacyZoneSource = PrivacyZoneSource.USER,
) {
    companion object {
        /** The smallest radius; anything smaller does not hide much once Wi-Fi range is counted. */
        const val MIN_RADIUS_METERS = 100

        /** The radius a new zone starts with. */
        const val DEFAULT_RADIUS_METERS = 250

        /** The largest radius. */
        const val MAX_RADIUS_METERS = 2000

        /** The most zones the user can save. */
        const val MAX_ZONES = 20

        /** @return [radiusMeters] limited to the allowed range. */
        @JvmStatic
        fun clampRadius(radiusMeters: Int): Int = radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)

        /** @return True if the coordinates are finite and on the globe. */
        @JvmStatic
        fun isValidCenter(latitude: Double, longitude: Double): Boolean =
            latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}

/** Who set a [PrivacyZone]. */
enum class PrivacyZoneSource {
    /** Added on this phone by the person using it. */
    USER,

    /** Pushed by the organization (reserved; the managed configuration has no zone list yet). */
    ORGANIZATION,
}

/**
 * One location fix, reduced to what the zone decision needs, so the rules have no Android types.
 *
 * @property accuracyMeters    Horizontal accuracy (68% radius), or null when the fix has none.
 * @property elapsedRealtimeMs When the fix was taken, on the monotonic elapsed realtime clock, or
 *                             null when unknown (such a fix never decides).
 */
data class PrivacyZoneFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val elapsedRealtimeMs: Long?,
)
