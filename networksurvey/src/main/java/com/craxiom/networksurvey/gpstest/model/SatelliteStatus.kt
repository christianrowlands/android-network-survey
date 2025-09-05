package com.craxiom.networksurvey.gpstest.model

/**
 * Mirrors the GnssStatus class (https://developer.android.com/reference/android/location/GnssStatus),
 * but uses internal GnssType and SbasType values for GNSS and SBAS constellations
 *
 * Originally from the GPS Test open source Android app.
 * https://github.com/barbeau/gpstest
 */
data class SatelliteStatus(
    val svid: Int,
    val gnssType: GnssType,
    var cn0DbHz: Float,
    val hasAlmanac: Boolean,
    val hasEphemeris: Boolean,
    val usedInFix: Boolean,
    var elevationDegrees: Float,
    var azimuthDegrees: Float
) {
    var sbasType: SbasType = SbasType.UNKNOWN
    var hasCarrierFrequency: Boolean = false
    var carrierFrequencyHz: Double = 0.0
    var hasBasebandCn0DbHz: Boolean = false
    var basebandCn0DbHz: Float = NO_DATA

    companion object {
        const val NO_DATA = 0.0f
    }
}