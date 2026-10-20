package com.craxiom.networksurvey.gpstest.ui.status

import android.location.Location
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.gpstest.model.SbasType
import com.craxiom.networksurvey.gpstest.ui.gnss.data.FixState
import com.craxiom.networksurvey.gpstest.ui.gnss.model.SatelliteMetadata
import com.craxiom.networksurvey.gpstest.util.SignalKeys
import com.craxiom.networksurvey.model.DilutionOfPrecision
import com.craxiom.networksurvey.util.SignalBuckets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Status page's rows and Fix panel readings, from what the GNSS view model reports. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatusModelTest {

    private val noDop = DilutionOfPrecision(Double.NaN, Double.NaN, Double.NaN)

    private fun status(
        svid: Int = 12,
        type: GnssType = GnssType.NAVSTAR,
        cn0: Float = 38f,
        almanac: Boolean = true,
        ephemeris: Boolean = true,
        used: Boolean = true,
        elevation: Float = 45f,
        azimuth: Float = 120f,
    ) = SatelliteStatus(svid, type, cn0, almanac, ephemeris, used, elevation, azimuth)

    @Test
    fun aRow_carriesItsSystemBandAndReadings() {
        val row = SatelliteRow.from(status().apply {
            hasCarrierFrequency = true
            carrierFrequencyHz = 1_575_420_000.0
            hasAgc = true
            agcDb = 2.5f
        })

        assertEquals(12, row.svid)
        assertEquals(R.string.gps_content_description, row.system?.shortName)
        assertEquals(Carrier.Band("L1"), row.carrier)
        assertEquals(38f, row.cn0DbHz)
        assertEquals(2.5f, row.agcDb)
        assertEquals(45f, row.elevationDegrees)
        assertEquals(120f, row.azimuthDegrees)
        assertTrue(row.usedInFix)
        // 38 dB-Hz is between yellow (33.3) and halfway to green (39.15)
        assertEquals(SignalBuckets.GOOD, row.cn0Bucket)
    }

    @Test
    fun aRow_isKeyedByItsSignal_andRepeatsGetTheirOwnKey() {
        val statuses = listOf(status(), status(), status(svid = 3)).onEach {
            it.hasCarrierFrequency = true
            it.carrierFrequencyHz = 1_575_420_000.0
        }
        SignalKeys.assign(statuses)

        val keys = statuses.map { SatelliteRow.from(it).key }
        assertEquals(3, keys.toSet().size)
        assertEquals("${keys[0]}#2", keys[1])
    }

    @Test
    fun cn0Bucket_followsTheSkyColors() {
        assertEquals(SignalBuckets.UNKNOWN, cn0Bucket(9f))
        assertEquals(SignalBuckets.VERY_WEAK, cn0Bucket(15f))
        assertEquals(SignalBuckets.WEAK, cn0Bucket(25f))
        assertEquals(SignalBuckets.FAIR, cn0Bucket(30f))
        assertEquals(SignalBuckets.GOOD, cn0Bucket(36f))
        assertEquals(SignalBuckets.STRONG, cn0Bucket(42f))
    }

    @Test
    fun aSignalNotHeard_hasNoCn0() {
        val row = SatelliteRow.from(status(cn0 = 0f, used = false))

        assertNull(row.cn0DbHz)
        assertEquals(SignalBuckets.UNKNOWN, row.cn0Bucket)
        assertFalse(row.usedInFix)
    }

    @Test
    fun noPosition_onlyWhenBothAnglesAreZero() {
        val unplaced = SatelliteRow.from(status(elevation = 0f, azimuth = 0f))
        assertNull(unplaced.elevationDegrees)
        assertNull(unplaced.azimuthDegrees)

        val onTheHorizon = SatelliteRow.from(status(elevation = 0f, azimuth = 90f))
        assertEquals(0f, onTheHorizon.elevationDegrees)
        assertEquals(90f, onTheHorizon.azimuthDegrees)
    }

    @Test
    fun noCarrier_orAnUnknownOne_showsNoBand_orItsFrequency() {
        assertNull(SatelliteRow.from(status()).carrier)
        val unknown = SatelliteRow.from(status().apply {
            hasCarrierFrequency = true
            carrierFrequencyHz = 1_000_000_000.0
        })
        assertEquals(Carrier.Frequency(1000.0), unknown.carrier)
        assertNull(SatelliteRow.from(status()).agcDb)
    }

    @Test
    fun anSbasSignal_isNamedForItsAugmentationSystem_andAnUnknownOneHasNoSystem() {
        val waas = status(type = GnssType.SBAS).apply { sbasType = SbasType.WAAS }
        assertEquals(R.string.waas_content_description, SatelliteRow.from(waas).system?.shortName)
        assertNull(SatelliteRow.from(status(type = GnssType.UNKNOWN)).system)
    }

    @Test
    fun aFix_givesEveryReadingThePhoneReports() {
        val location = Location("gps").apply {
            latitude = 40.6892
            longitude = -74.0445
            altitude = 192.0
            accuracy = 4f
            verticalAccuracyMeters = 6f
            speed = 1.5f
            speedAccuracyMetersPerSecond = 0.4f
            bearing = 240f
            bearingAccuracyDegrees = 5.6f
            time = System.currentTimeMillis()
        }
        val readings = FixReadings.from(
            location, "38 s", 190.0, DilutionOfPrecision(1.1, 0.8, 1.2),
            SatelliteMetadata(numSatsUsed = 8, numSatsInView = 20), FixState.Acquired,
        )

        assertEquals(40.6892, readings.latitude!!, 0.0)
        assertEquals(-74.0445, readings.longitude!!, 0.0)
        assertEquals(192.0, readings.altitudeMeters!!, 0.0)
        assertEquals(190.0, readings.altitudeMslMeters!!, 0.0)
        assertEquals(4f, readings.accuracyMeters)
        assertEquals(6f, readings.verticalAccuracyMeters)
        assertEquals(1.5f, readings.speedMetersPerSecond)
        assertEquals(0.4f, readings.speedAccuracyMetersPerSecond)
        assertEquals(240f, readings.bearingDegrees)
        assertEquals(5.6f, readings.bearingAccuracyDegrees)
        assertEquals(1.1, readings.pdop!!, 0.0)
        assertEquals(0.8, readings.hdop!!, 0.0)
        assertEquals(1.2, readings.vdop!!, 0.0)
        assertEquals("38 s", readings.ttff)
        assertTrue(readings.timeValid)
        assertEquals(8, readings.satellitesUsed)
        assertEquals(20, readings.satellitesInView)
        assertTrue(readings.fix)
        assertTrue(readings.hasPosition)
    }

    @Test
    fun beforeTheFirstFix_nothingIsReported() {
        val readings = FixReadings.from(Location("reset"), "", Double.NaN, noDop, SatelliteMetadata(), FixState.NotAcquired)

        assertEquals(FixReadings(timeMs = null, fix = false), readings)
        assertFalse(readings.hasPosition)
    }

    @Test
    fun aFixTimeDaysAwayFromTheClock_isFlagged() {
        val stale = Location("gps").apply { time = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000 }
        val readings = FixReadings.from(stale, "", Double.NaN, noDop, SatelliteMetadata(), FixState.Acquired)

        assertFalse(readings.timeValid)
    }

    @Test
    fun aDopPair_needsBothHalves_andAZeroTimeIsNoTime() {
        val location = Location("gps").apply {
            latitude = 1.0
            longitude = 2.0
            time = 0L
        }
        val readings = FixReadings.from(location, "", 12.0, DilutionOfPrecision(1.1, 0.8, Double.NaN), SatelliteMetadata(), FixState.Acquired)

        assertEquals(1.1, readings.pdop!!, 0.0)
        assertNull(readings.hdop)
        assertNull(readings.vdop)
        assertNull(readings.timeMs)
        assertTrue(readings.timeValid)
        assertEquals(12.0, readings.altitudeMslMeters!!, 0.0)
    }
}
