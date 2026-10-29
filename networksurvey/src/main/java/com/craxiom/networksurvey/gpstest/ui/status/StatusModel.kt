package com.craxiom.networksurvey.gpstest.ui.status

import android.location.Location
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.gpstest.ui.GnssSystem
import com.craxiom.networksurvey.gpstest.ui.GnssSystems
import com.craxiom.networksurvey.gpstest.ui.gnss.data.FixState
import com.craxiom.networksurvey.gpstest.ui.gnss.model.SatelliteMetadata
import com.craxiom.networksurvey.gpstest.ui.sky.SkyProjection
import com.craxiom.networksurvey.gpstest.util.CarrierFreqUtils
import com.craxiom.networksurvey.gpstest.util.DateTimeUtils
import com.craxiom.networksurvey.gpstest.util.MathUtils
import com.craxiom.networksurvey.gpstest.util.SatelliteUtil.isBearingAccuracySupported
import com.craxiom.networksurvey.gpstest.util.SatelliteUtil.isSpeedAccuracySupported
import com.craxiom.networksurvey.gpstest.util.SatelliteUtil.isVerticalAccuracySupported
import com.craxiom.networksurvey.gpstest.util.SatelliteUtils
import com.craxiom.networksurvey.model.DilutionOfPrecision
import com.craxiom.networksurvey.util.SignalBuckets

/** A signal's carrier: its band ("L1", "E5a") or, when no band matches, its frequency. */
sealed interface Carrier {
    data class Band(val label: String) : Carrier
    data class Frequency(val mhz: Double) : Carrier
}

/**
 * One row of the Status page: a signal, as GnssStatus reports it. A satellite heard on two
 * frequencies has two rows, one per carrier.
 *
 * @property key The signal's identity across scans ([SatelliteStatus.signalKey]), unique in a list.
 * @property system The constellation, or for SBAS the augmentation system; null when unknown.
 * @property cn0DbHz The signal's C/N0, null when it is not heard.
 * @property agcDb The receiver's AGC for the signal or its band, null when the phone gives none.
 * @property elevationDegrees Null, with [azimuthDegrees], when the satellite has no position.
 */
data class SatelliteRow(
    val key: String,
    val svid: Int,
    val system: GnssSystem?,
    val carrier: Carrier?,
    val cn0DbHz: Float?,
    val agcDb: Float?,
    val elevationDegrees: Float?,
    val azimuthDegrees: Float?,
    val usedInFix: Boolean,
    val hasAlmanac: Boolean,
    val hasEphemeris: Boolean,
) {
    /** The C/N0's [SignalBuckets] bucket, for its text color ([cn0Bucket]). */
    val cn0Bucket: Int get() = cn0DbHz?.let(::cn0Bucket) ?: SignalBuckets.UNKNOWN

    companion object {
        fun from(status: SatelliteStatus): SatelliteRow {
            // Both at 0 means no position; one at exactly 0 degrees is a real angle (as on the sky)
            val placed = status.elevationDegrees != SatelliteStatus.NO_DATA ||
                    status.azimuthDegrees != SatelliteStatus.NO_DATA
            return SatelliteRow(
                key = status.signalKey.ifEmpty { SatelliteUtils.createGnssStatusKey(status) },
                svid = status.svid,
                system = GnssSystems.of(status.gnssType, status.sbasType),
                carrier = carrier(status),
                cn0DbHz = status.cn0DbHz.takeIf { SkyProjection.inView(it) },
                agcDb = status.agcDb.takeIf { status.hasAgc },
                elevationDegrees = status.elevationDegrees.takeIf { placed },
                azimuthDegrees = status.azimuthDegrees.takeIf { placed },
                usedInFix = status.usedInFix,
                hasAlmanac = status.hasAlmanac,
                hasEphemeris = status.hasEphemeris,
            )
        }

        private fun carrier(status: SatelliteStatus): Carrier? {
            if (!status.hasCarrierFrequency) return null
            val label = CarrierFreqUtils.getCarrierFrequencyLabel(status)
            return if (label == CarrierFreqUtils.CF_UNKNOWN) {
                Carrier.Frequency(MathUtils.toMhz(status.carrierFrequencyHz))
            } else {
                Carrier.Band(label)
            }
        }
    }
}

/**
 * @return The [SignalBuckets] bucket whose text color is nearest the sky's fill for [cn0DbHz]:
 * unknown (muted) below the first stop, very weak (red) up to the red stop, weak (orange) to halfway
 * to yellow, fair (yellow) up to yellow, good (light green) to halfway to green, and strong above.
 */
fun cn0Bucket(cn0DbHz: Float): Int {
    val (gray, red, yellow, green) = SkyProjection.CN0_STOPS.toList()
    return when {
        cn0DbHz < gray -> SignalBuckets.UNKNOWN
        cn0DbHz < red -> SignalBuckets.VERY_WEAK
        cn0DbHz < (red + yellow) / 2 -> SignalBuckets.WEAK
        cn0DbHz < yellow -> SignalBuckets.FAIR
        cn0DbHz < (yellow + green) / 2 -> SignalBuckets.GOOD
        else -> SignalBuckets.STRONG
    }
}

/**
 * The Fix panel's readings, each null when the phone did not report it.
 *
 * @property timeValid False when the GNSS time is days away from the phone's clock (the GPS week
 * rollover issue), which the panel flags.
 * @property satellitesUsed Satellites used in the fix, of [satellitesInView] heard.
 */
data class FixReadings(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val altitudeMslMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val verticalAccuracyMeters: Float? = null,
    val speedMetersPerSecond: Float? = null,
    val speedAccuracyMetersPerSecond: Float? = null,
    val bearingDegrees: Float? = null,
    val bearingAccuracyDegrees: Float? = null,
    val pdop: Double? = null,
    val hdop: Double? = null,
    val vdop: Double? = null,
    val timeMs: Long? = null,
    val timeValid: Boolean = true,
    val ttff: String? = null,
    val satellitesUsed: Int = 0,
    val satellitesInView: Int = 0,
    val fix: Boolean = false,
) {
    /** True when there is a position to copy. */
    val hasPosition: Boolean get() = latitude != null && longitude != null

    companion object {
        fun from(
            location: Location,
            ttff: String,
            altitudeMsl: Double,
            dop: DilutionOfPrecision,
            metadata: SatelliteMetadata,
            fixState: FixState,
        ): FixReadings {
            // A reset location (and no fix yet) is 0, 0
            val positioned = location.latitude != 0.0 || location.longitude != 0.0
            val time = location.time.takeIf { it != 0L }
            return FixReadings(
                latitude = location.latitude.takeIf { positioned },
                longitude = location.longitude.takeIf { positioned },
                altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
                altitudeMslMeters = altitudeMsl.takeUnless { it.isNaN() },
                accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                verticalAccuracyMeters = location.verticalAccuracyMeters.takeIf { location.isVerticalAccuracySupported() },
                speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() },
                speedAccuracyMetersPerSecond = location.speedAccuracyMetersPerSecond.takeIf { location.isSpeedAccuracySupported() },
                bearingDegrees = location.bearing.takeIf { location.hasBearing() },
                bearingAccuracyDegrees = location.bearingAccuracyDegrees.takeIf { location.isBearingAccuracySupported() },
                pdop = dop.positionDop.takeUnless { it.isNaN() },
                hdop = dop.horizontalDop.takeUnless { it.isNaN() || dop.verticalDop.isNaN() },
                vdop = dop.verticalDop.takeUnless { it.isNaN() || dop.horizontalDop.isNaN() },
                timeMs = time,
                timeValid = time == null || DateTimeUtils.isTimeValid(time),
                ttff = ttff.ifEmpty { null },
                satellitesUsed = metadata.numSatsUsed,
                satellitesInView = metadata.numSatsInView,
                fix = fixState is FixState.Acquired,
            )
        }
    }
}
