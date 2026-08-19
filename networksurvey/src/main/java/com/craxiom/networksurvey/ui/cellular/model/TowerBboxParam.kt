package com.craxiom.networksurvey.ui.cellular.model

/**
 * Builds the `bbox` query parameter for the NS Tower Service `/v2/cells/area` endpoint, or
 * returns null when the supplied map bounds do not describe a queryable area.
 *
 * The tower map calls this with whatever MapLibre currently reports for the visible region,
 * and that value is not always usable. MapLibre reports an all zero region before the map
 * surface has been laid out, and reports longitudes outside [-180, 180] for world scale
 * views. The tower service rejects both with a 400, so there is nothing to gain by sending
 * them.
 *
 * Out of range longitudes are rejected rather than clamped. Clamping a world scale view to
 * the +/-180 boundary would produce a box the server happily accepts, and the user would get
 * an arbitrary truncated slice of the world presented as if it were their requested area. A
 * skipped query is the honest outcome.
 *
 * The validation here intentionally mirrors ValidateBBox in the tower service
 * (network-survey-tower-service/util/util.go), so that anything this function returns is
 * something the server will accept.
 *
 * @return the bbox parameter formatted as "southWestLat,southWestLon,northEastLat,northEastLon",
 *         or null if the bounds are not queryable.
 */
internal fun buildBboxParam(
    southWestLat: Double,
    southWestLon: Double,
    northEastLat: Double,
    northEastLon: Double
): String? {
    if (!southWestLat.isFinite() || !southWestLon.isFinite() ||
        !northEastLat.isFinite() || !northEastLon.isFinite()
    ) {
        return null
    }

    if (southWestLat < -90.0 || southWestLat > 90.0 ||
        northEastLat < -90.0 || northEastLat > 90.0
    ) {
        return null
    }

    if (southWestLon < -180.0 || southWestLon > 180.0 ||
        northEastLon < -180.0 || northEastLon > 180.0
    ) {
        return null
    }

    // A zero area or inverted box is not queryable. This is the check that catches the all
    // zero region reported by a map that has not been laid out yet.
    if (southWestLat >= northEastLat || southWestLon >= northEastLon) return null

    return listOf(southWestLat, southWestLon, northEastLat, northEastLon).joinToString(",")
}
