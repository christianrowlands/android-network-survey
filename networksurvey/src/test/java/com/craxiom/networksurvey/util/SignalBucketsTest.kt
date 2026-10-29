package com.craxiom.networksurvey.util

import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the single threshold table behind the survey points signal ramp and its legend.
 */
class SignalBucketsTest {

    @Test
    fun lteAndNrUseTheRsrpThresholdsAtTheirBoundaries() {
        for (protocol in listOf(
            SurveyedPointEntity.PROTOCOL_LTE,
            SurveyedPointEntity.PROTOCOL_NR
        )) {
            assertEquals(SignalBuckets.STRONG, SignalBuckets.cellular(protocol, -84))
            assertEquals(SignalBuckets.GOOD, SignalBuckets.cellular(protocol, -85))
            assertEquals(SignalBuckets.GOOD, SignalBuckets.cellular(protocol, -94))
            assertEquals(SignalBuckets.FAIR, SignalBuckets.cellular(protocol, -95))
            assertEquals(SignalBuckets.FAIR, SignalBuckets.cellular(protocol, -97))
            assertEquals(SignalBuckets.WEAK, SignalBuckets.cellular(protocol, -105))
            assertEquals(SignalBuckets.WEAK, SignalBuckets.cellular(protocol, -114))
            assertEquals(SignalBuckets.VERY_WEAK, SignalBuckets.cellular(protocol, -115))
            assertEquals(SignalBuckets.VERY_WEAK, SignalBuckets.cellular(protocol, -140))
        }
    }

    @Test
    fun umtsAndGsmUseTheRssiThresholds() {
        for (protocol in listOf(
            SurveyedPointEntity.PROTOCOL_UMTS,
            SurveyedPointEntity.PROTOCOL_GSM
        )) {
            assertEquals(SignalBuckets.STRONG, SignalBuckets.cellular(protocol, -74))
            assertEquals(SignalBuckets.GOOD, SignalBuckets.cellular(protocol, -75))
            assertEquals(SignalBuckets.FAIR, SignalBuckets.cellular(protocol, -90))
            assertEquals(SignalBuckets.WEAK, SignalBuckets.cellular(protocol, -100))
            assertEquals(SignalBuckets.VERY_WEAK, SignalBuckets.cellular(protocol, -106))
        }
    }

    @Test
    fun wifiAndBluetoothUseTheWifiCategoryThresholds() {
        assertEquals(SignalBuckets.STRONG, SignalBuckets.rssi(-59))
        assertEquals(SignalBuckets.GOOD, SignalBuckets.rssi(-60))
        assertEquals(SignalBuckets.GOOD, SignalBuckets.rssi(-65))
        assertEquals(SignalBuckets.FAIR, SignalBuckets.rssi(-75))
        assertEquals(SignalBuckets.WEAK, SignalBuckets.rssi(-85))
        assertEquals(SignalBuckets.VERY_WEAK, SignalBuckets.rssi(-91))
    }

    @Test
    fun unknownForCdmaMissingAndImpossibleValues() {
        assertEquals(
            SignalBuckets.UNKNOWN,
            SignalBuckets.cellular(SurveyedPointEntity.PROTOCOL_CDMA, -80)
        )
        assertEquals(
            SignalBuckets.UNKNOWN,
            SignalBuckets.cellular(SurveyedPointEntity.PROTOCOL_NONE, -80)
        )
        assertEquals(
            SignalBuckets.UNKNOWN,
            SignalBuckets.cellular(SurveyedPointEntity.PROTOCOL_LTE, 0)
        )
        assertEquals(
            SignalBuckets.UNKNOWN,
            SignalBuckets.cellular(SurveyedPointEntity.PROTOCOL_LTE, 12)
        )
        assertEquals(SignalBuckets.UNKNOWN, SignalBuckets.rssi(0))
    }

    @Test
    fun rsrqUsesTheQualityThresholdsAtTheirBoundaries() {
        assertEquals(SignalBuckets.STRONG, SignalBuckets.rsrq(5))
        assertEquals(SignalBuckets.STRONG, SignalBuckets.rsrq(0))
        assertEquals(SignalBuckets.STRONG, SignalBuckets.rsrq(-10))
        assertEquals(SignalBuckets.GOOD, SignalBuckets.rsrq(-11))
        assertEquals(SignalBuckets.GOOD, SignalBuckets.rsrq(-13))
        assertEquals(SignalBuckets.FAIR, SignalBuckets.rsrq(-14))
        assertEquals(SignalBuckets.FAIR, SignalBuckets.rsrq(-16))
        assertEquals(SignalBuckets.WEAK, SignalBuckets.rsrq(-17))
        assertEquals(SignalBuckets.WEAK, SignalBuckets.rsrq(-19))
        assertEquals(SignalBuckets.VERY_WEAK, SignalBuckets.rsrq(-20))
        assertEquals(SignalBuckets.VERY_WEAK, SignalBuckets.rsrq(-34))
    }

    @Test
    fun sinrUsesTheQualityThresholdsAtTheirBoundaries() {
        assertEquals(SignalBuckets.STRONG, SignalBuckets.sinr(30))
        assertEquals(SignalBuckets.STRONG, SignalBuckets.sinr(20))
        assertEquals(SignalBuckets.GOOD, SignalBuckets.sinr(19))
        assertEquals(SignalBuckets.GOOD, SignalBuckets.sinr(13))
        assertEquals(SignalBuckets.FAIR, SignalBuckets.sinr(12))
        assertEquals(SignalBuckets.FAIR, SignalBuckets.sinr(5))
        assertEquals(SignalBuckets.WEAK, SignalBuckets.sinr(4))
        // Zero is a real SINR, not the protobuf default.
        assertEquals(SignalBuckets.WEAK, SignalBuckets.sinr(0))
        assertEquals(SignalBuckets.VERY_WEAK, SignalBuckets.sinr(-1))
        assertEquals(SignalBuckets.VERY_WEAK, SignalBuckets.sinr(-20))
    }

    @Test
    fun strongIsTheHighestBucketSoMaxPicksTheBest() {
        assertEquals(5, SignalBuckets.STRONG)
        assertEquals(1, SignalBuckets.VERY_WEAK)
        assertEquals(0, SignalBuckets.UNKNOWN)
    }

    @Test
    fun legendThresholdsComeFromTheSameTable() {
        assertArrayEquals(
            intArrayOf(-85, -95, -105, -115),
            SignalBuckets.cellularThresholds(SurveyedPointEntity.PROTOCOL_LTE)
        )
        assertArrayEquals(
            intArrayOf(-75, -85, -95, -105),
            SignalBuckets.cellularThresholds(SurveyedPointEntity.PROTOCOL_GSM)
        )
        assertArrayEquals(intArrayOf(-60, -70, -80, -90), SignalBuckets.rssiThresholds())
    }
}
