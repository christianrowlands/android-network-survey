package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.networksurvey.util.SignalBuckets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Signal colors for every cellular measurement. */
class CellularSignalBucketsTest {

    @Test
    fun primaryMeasurements_useTheMapThresholds() {
        assertEquals(SignalBuckets.STRONG, CellularSignalBuckets.bucket(CellFieldKind.RSRP, -80))
        assertEquals(SignalBuckets.VERY_WEAK, CellularSignalBuckets.bucket(CellFieldKind.SS_RSRP, -120))
        assertEquals(SignalBuckets.FAIR, CellularSignalBuckets.bucket(CellFieldKind.RSSI, -90))
        assertEquals(SignalBuckets.WEAK, CellularSignalBuckets.bucket(CellFieldKind.RSCP, -100))
    }

    @Test
    fun qualityMeasurements_useTheQualityThresholds() {
        // RSRQ and SS-RSRQ: strong from -10, very weak at -20 and below.
        assertEquals(SignalBuckets.STRONG, CellularSignalBuckets.bucket(CellFieldKind.RSRQ, -10))
        assertEquals(SignalBuckets.FAIR, CellularSignalBuckets.bucket(CellFieldKind.RSRQ, -14))
        assertEquals(SignalBuckets.VERY_WEAK, CellularSignalBuckets.bucket(CellFieldKind.RSRQ, -22))
        assertEquals(SignalBuckets.GOOD, CellularSignalBuckets.bucket(CellFieldKind.SS_RSRQ, -11))
        // SNR and SS-SINR: strong from 20, very weak below 0.
        assertEquals(SignalBuckets.FAIR, CellularSignalBuckets.bucket(CellFieldKind.SNR, 9))
        assertEquals(SignalBuckets.GOOD, CellularSignalBuckets.bucket(CellFieldKind.SNR, 13))
        assertEquals(SignalBuckets.STRONG, CellularSignalBuckets.bucket(CellFieldKind.SS_SINR, 25))
        assertEquals(SignalBuckets.VERY_WEAK, CellularSignalBuckets.bucket(CellFieldKind.SS_SINR, -3))
    }

    @Test
    fun qualityMeasurements_zeroAndPositiveValuesAreReadings() {
        assertEquals(SignalBuckets.WEAK, CellularSignalBuckets.bucket(CellFieldKind.SNR, 0))
        assertEquals(0.24f, CellularSignalBuckets.fraction(CellFieldKind.SNR, 0)!!, 0.0001f)
        // NR SS-RSRQ reports up to +20 dB.
        assertEquals(SignalBuckets.STRONG, CellularSignalBuckets.bucket(CellFieldKind.SS_RSRQ, 3))
        assertEquals(1f, CellularSignalBuckets.fraction(CellFieldKind.SS_RSRQ, 3)!!, 0f)
    }

    @Test
    fun qualityFraction_putsEachThresholdOnAFifth() {
        // RSRQ thresholds -11, -14, -17, -20, with a 3 dB step of headroom at each end.
        assertEquals(0.2f, CellularSignalBuckets.fraction(CellFieldKind.RSRQ, -20)!!, 0.0001f)
        assertEquals(0.8f, CellularSignalBuckets.fraction(CellFieldKind.RSRQ, -11)!!, 0.0001f)
        assertEquals(0f, CellularSignalBuckets.fraction(CellFieldKind.RSRQ, -23)!!, 0f)
        assertEquals(1f, CellularSignalBuckets.fraction(CellFieldKind.RSRQ, -8)!!, 0f)
        // SINR thresholds 19, 12, 4, -1: uneven steps, so each end's headroom matches its neighbor.
        assertEquals(0f, CellularSignalBuckets.fraction(CellFieldKind.SS_SINR, -6)!!, 0f)
        assertEquals(0.4f, CellularSignalBuckets.fraction(CellFieldKind.SS_SINR, 4)!!, 0.0001f)
        assertEquals(1f, CellularSignalBuckets.fraction(CellFieldKind.SS_SINR, 26)!!, 0f)
    }

    @Test
    fun missingValues_areUnknown() {
        assertEquals(SignalBuckets.UNKNOWN, CellularSignalBuckets.bucket(CellFieldKind.RSRQ, null))
        assertNull(CellularSignalBuckets.fraction(CellFieldKind.RSRP, null))
    }

    @Test
    fun fraction_isClamped() {
        assertEquals(0f, CellularSignalBuckets.fraction(CellFieldKind.RSRP, -140)!!, 0f)
        assertEquals(1f, CellularSignalBuckets.fraction(CellFieldKind.RSRP, -40)!!, 0f)
        assertEquals(0f, CellularSignalBuckets.fraction(CellFieldKind.RSRQ, -40)!!, 0f)
    }

    @Test
    fun fraction_putsEachThresholdOnAFifth() {
        // LTE RSRP thresholds -85, -95, -105, -115, with a 10 dB step of headroom at each end.
        assertEquals(0.2f, CellularSignalBuckets.fraction(CellFieldKind.RSRP, -115)!!, 0.0001f)
        assertEquals(0.58f, CellularSignalBuckets.fraction(CellFieldKind.SS_RSRP, -96)!!, 0.0001f)
        assertEquals(0.8f, CellularSignalBuckets.fraction(CellFieldKind.RSRP, -85)!!, 0.0001f)
        // GSM RSSI and UMTS RSCP thresholds -75, -85, -95, -105.
        assertEquals(0.5f, CellularSignalBuckets.fraction(CellFieldKind.RSSI, -90)!!, 0.0001f)
        assertEquals(0.3f, CellularSignalBuckets.fraction(CellFieldKind.RSCP, -100)!!, 0.0001f)
    }

    @Test
    fun theProtobufDefault_hasNoBar() {
        assertNull(CellularSignalBuckets.fraction(CellFieldKind.RSRP, 0))
        assertEquals(SignalBuckets.UNKNOWN, CellularSignalBuckets.bucket(CellFieldKind.RSRP, 0))
    }

    @Test
    fun theBarAndTheColor_alwaysAgree() {
        val measurements = listOf(
            CellFieldKind.RSSI,
            CellFieldKind.RSCP,
            CellFieldKind.RSRP,
            CellFieldKind.RSRQ,
            CellFieldKind.SNR,
            CellFieldKind.SS_RSRP,
            CellFieldKind.SS_RSRQ,
            CellFieldKind.SS_SINR,
        )
        for (kind in measurements) {
            for (value in -150..40) {
                val fraction = CellularSignalBuckets.fraction(kind, value) ?: continue
                val bucket = CellularSignalBuckets.bucket(kind, value)
                val (low, high) = BUCKET_FRACTIONS.getValue(bucket)
                val inside = fraction in low..high
                assertEquals("$kind $value: bucket $bucket, fraction $fraction", true, inside)
            }
        }
        // A value on a threshold belongs to the bucket below it.
        assertEquals(SignalBuckets.GOOD, CellularSignalBuckets.bucket(CellFieldKind.RSRP, -85))
        assertEquals(SignalBuckets.STRONG, CellularSignalBuckets.bucket(CellFieldKind.RSRP, -84))
    }

    @Test
    fun hasRange_tellsAMissingScaleFromAMissingValue() {
        assertEquals(true, CellularSignalBuckets.hasRange(CellFieldKind.RSRP))
        assertEquals(true, CellularSignalBuckets.hasRange(CellFieldKind.SS_SINR))
        assertEquals(false, CellularSignalBuckets.hasRange(CellFieldKind.CQI))
        assertEquals(false, CellularSignalBuckets.hasRange(CellFieldKind.TA))
    }

    private companion object {
        /** Each bucket's share of the bar; the edges are shared, so both ends are inclusive. */
        val BUCKET_FRACTIONS = mapOf(
            SignalBuckets.VERY_WEAK to (0f to 0.2f),
            SignalBuckets.WEAK to (0.2f to 0.4f),
            SignalBuckets.FAIR to (0.4f to 0.6f),
            SignalBuckets.GOOD to (0.6f to 0.8f),
            SignalBuckets.STRONG to (0.8f to 1f),
        )
    }
}
