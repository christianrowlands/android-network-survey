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

    // AGC comes from GnssMeasurementsEvent (not GnssStatus) and is joined onto this model by
    // SignalInfoViewModel. Check hasAgc for presence since 0.0 is a valid AGC value.
    var hasAgc: Boolean = false
    var agcDb: Float = NO_DATA

    // The generated data class equals/hashCode only cover the primary constructor properties,
    // which lets Compose skip recompositions when only a body property (such as agcDb) changes.
    // Include every property so equality reflects everything displayed in the UI.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SatelliteStatus) return false
        return svid == other.svid &&
                gnssType == other.gnssType &&
                cn0DbHz == other.cn0DbHz &&
                hasAlmanac == other.hasAlmanac &&
                hasEphemeris == other.hasEphemeris &&
                usedInFix == other.usedInFix &&
                elevationDegrees == other.elevationDegrees &&
                azimuthDegrees == other.azimuthDegrees &&
                sbasType == other.sbasType &&
                hasCarrierFrequency == other.hasCarrierFrequency &&
                carrierFrequencyHz == other.carrierFrequencyHz &&
                hasBasebandCn0DbHz == other.hasBasebandCn0DbHz &&
                basebandCn0DbHz == other.basebandCn0DbHz &&
                hasAgc == other.hasAgc &&
                agcDb == other.agcDb
    }

    override fun hashCode(): Int {
        var result = svid
        result = 31 * result + gnssType.hashCode()
        result = 31 * result + cn0DbHz.hashCode()
        result = 31 * result + hasAlmanac.hashCode()
        result = 31 * result + hasEphemeris.hashCode()
        result = 31 * result + usedInFix.hashCode()
        result = 31 * result + elevationDegrees.hashCode()
        result = 31 * result + azimuthDegrees.hashCode()
        result = 31 * result + sbasType.hashCode()
        result = 31 * result + hasCarrierFrequency.hashCode()
        result = 31 * result + carrierFrequencyHz.hashCode()
        result = 31 * result + hasBasebandCn0DbHz.hashCode()
        result = 31 * result + basebandCn0DbHz.hashCode()
        result = 31 * result + hasAgc.hashCode()
        result = 31 * result + agcDb.hashCode()
        return result
    }

    companion object {
        const val NO_DATA = 0.0f
    }
}