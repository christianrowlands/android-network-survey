package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.util.SignalBuckets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Signal colors for every cellular measurement. */
class CellularSignalBucketsTest {

    @Test
    fun primaryMeasurements_useTheMapThresholds() {
        assertEquals(SignalBuckets.STRONG, CellularSignalBuckets.bucket(CellularProtocol.LTE, CellFieldKind.RSRP, -80))
        assertEquals(SignalBuckets.VERY_WEAK, CellularSignalBuckets.bucket(CellularProtocol.NR, CellFieldKind.SS_RSRP, -120))
        assertEquals(SignalBuckets.FAIR, CellularSignalBuckets.bucket(CellularProtocol.GSM, CellFieldKind.RSSI, -90))
        assertEquals(SignalBuckets.WEAK, CellularSignalBuckets.bucket(CellularProtocol.UMTS, CellFieldKind.RSCP, -100))
    }

    @Test
    fun qualityMeasurements_useTheProtocolRangeInFifths() {
        // LTE RSRQ: -23 over 11 dB; SNR: 2 over 13 dB.
        assertEquals(SignalBuckets.STRONG, CellularSignalBuckets.bucket(CellularProtocol.LTE, CellFieldKind.RSRQ, -14))
        assertEquals(SignalBuckets.VERY_WEAK, CellularSignalBuckets.bucket(CellularProtocol.LTE, CellFieldKind.RSRQ, -22))
        assertEquals(SignalBuckets.FAIR, CellularSignalBuckets.bucket(CellularProtocol.LTE, CellFieldKind.SNR, 9))
        // NR SS-SINR: -5 over 29 dB; a positive value is still a reading.
        assertEquals(SignalBuckets.STRONG, CellularSignalBuckets.bucket(CellularProtocol.NR, CellFieldKind.SS_SINR, 25))
    }

    @Test
    fun missingValues_areUnknown() {
        assertEquals(SignalBuckets.UNKNOWN, CellularSignalBuckets.bucket(CellularProtocol.LTE, CellFieldKind.RSRQ, null))
        assertNull(CellularSignalBuckets.fraction(CellularProtocol.LTE, CellFieldKind.RSRP, null))
    }

    @Test
    fun fraction_isClamped() {
        assertEquals(0f, CellularSignalBuckets.fraction(CellularProtocol.LTE, CellFieldKind.RSRP, -140)!!, 0f)
        assertEquals(1f, CellularSignalBuckets.fraction(CellularProtocol.LTE, CellFieldKind.RSRP, -40)!!, 0f)
        assertEquals(0f, CellularSignalBuckets.fraction(CellularProtocol.LTE, CellFieldKind.RSRQ, -40)!!, 0f)
    }

    @Test
    fun fraction_putsEachThresholdOnAFifth() {
        // LTE RSRP thresholds -85, -95, -105, -115, with a 10 dB step of headroom at each end.
        assertEquals(0.2f, CellularSignalBuckets.fraction(CellularProtocol.LTE, CellFieldKind.RSRP, -115)!!, 0.0001f)
        assertEquals(0.58f, CellularSignalBuckets.fraction(CellularProtocol.NR, CellFieldKind.SS_RSRP, -96)!!, 0.0001f)
        assertEquals(0.8f, CellularSignalBuckets.fraction(CellularProtocol.LTE, CellFieldKind.RSRP, -85)!!, 0.0001f)
        // GSM RSSI and UMTS RSCP thresholds -75, -85, -95, -105.
        assertEquals(0.5f, CellularSignalBuckets.fraction(CellularProtocol.GSM, CellFieldKind.RSSI, -90)!!, 0.0001f)
        assertEquals(0.3f, CellularSignalBuckets.fraction(CellularProtocol.UMTS, CellFieldKind.RSCP, -100)!!, 0.0001f)
    }

    @Test
    fun theProtobufDefault_hasNoBar() {
        assertNull(CellularSignalBuckets.fraction(CellularProtocol.LTE, CellFieldKind.RSRP, 0))
        assertEquals(SignalBuckets.UNKNOWN, CellularSignalBuckets.bucket(CellularProtocol.LTE, CellFieldKind.RSRP, 0))
    }

    @Test
    fun theBarAndTheColor_alwaysAgree() {
        val measurements = listOf(
            CellularProtocol.GSM to CellFieldKind.RSSI,
            CellularProtocol.UMTS to CellFieldKind.RSSI,
            CellularProtocol.UMTS to CellFieldKind.RSCP,
            CellularProtocol.LTE to CellFieldKind.RSRP,
            CellularProtocol.LTE to CellFieldKind.RSRQ,
            CellularProtocol.LTE to CellFieldKind.SNR,
            CellularProtocol.NR to CellFieldKind.SS_RSRP,
            CellularProtocol.NR to CellFieldKind.SS_RSRQ,
            CellularProtocol.NR to CellFieldKind.SS_SINR,
        )
        for ((protocol, kind) in measurements) {
            for (value in -150..40) {
                val fraction = CellularSignalBuckets.fraction(protocol, kind, value) ?: continue
                val bucket = CellularSignalBuckets.bucket(protocol, kind, value)
                val (low, high) = BUCKET_FRACTIONS.getValue(bucket)
                val inside = fraction in low..high
                assertEquals("$protocol $kind $value: bucket $bucket, fraction $fraction", true, inside)
            }
        }
        // A value on a threshold belongs to the bucket below it.
        assertEquals(SignalBuckets.GOOD, CellularSignalBuckets.bucket(CellularProtocol.LTE, CellFieldKind.RSRP, -85))
        assertEquals(SignalBuckets.STRONG, CellularSignalBuckets.bucket(CellularProtocol.LTE, CellFieldKind.RSRP, -84))
    }

    @Test
    fun hasRange_tellsAMissingScaleFromAMissingValue() {
        assertEquals(true, CellularSignalBuckets.hasRange(CellularProtocol.LTE, CellFieldKind.RSRP))
        assertEquals(true, CellularSignalBuckets.hasRange(CellularProtocol.NR, CellFieldKind.SS_SINR))
        assertEquals(false, CellularSignalBuckets.hasRange(CellularProtocol.LTE, CellFieldKind.CQI))
        assertEquals(false, CellularSignalBuckets.hasRange(CellularProtocol.LTE, CellFieldKind.TA))
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
