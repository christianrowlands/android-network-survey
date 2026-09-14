package com.craxiom.networksurvey.logging.db

import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the movement gate that decides when a survey batch becomes a surveyed point.
 */
class SurveyedPointGateTest {

    private val gate = SurveyedPointGate()

    // One degree of latitude is roughly 111,320 m, so these deltas are approximately the
    // distances named in the test methods.
    private val baseLat = 38.0
    private val baseLon = -77.0
    private val walkingSpeed = 1.2f
    private val drivingSpeed = 15f

    private fun latOffset(meters: Double): Double = baseLat + meters / 111_320.0

    @Test
    fun firstObservationIsRecorded() {
        assertTrue(
            gate.shouldRecord(
                SurveyedPointEntity.OBSERVED_CELLULAR,
                baseLat,
                baseLon,
                10,
                walkingSpeed
            )
        )
    }

    @Test
    fun badAccuracyIsNeverRecorded() {
        assertFalse(
            gate.shouldRecord(
                SurveyedPointEntity.OBSERVED_CELLULAR,
                baseLat,
                baseLon,
                0,
                walkingSpeed
            )
        )
        assertFalse(
            gate.shouldRecord(
                SurveyedPointEntity.OBSERVED_CELLULAR,
                baseLat,
                baseLon,
                101,
                walkingSpeed
            )
        )
    }

    @Test
    fun zeroLocationIsNeverRecorded() {
        assertFalse(
            gate.shouldRecord(
                SurveyedPointEntity.OBSERVED_CELLULAR,
                0.0,
                0.0,
                10,
                walkingSpeed
            )
        )
    }

    @Test
    fun walkingUsesTheShortThreshold() {
        gate.shouldRecord(SurveyedPointEntity.OBSERVED_CELLULAR, baseLat, baseLon, 10, walkingSpeed)
        assertFalse(
            gate.shouldRecord(
                SurveyedPointEntity.OBSERVED_CELLULAR,
                latOffset(6.0),
                baseLon,
                10,
                walkingSpeed
            )
        )
        assertTrue(
            gate.shouldRecord(
                SurveyedPointEntity.OBSERVED_CELLULAR,
                latOffset(12.0),
                baseLon,
                10,
                walkingSpeed
            )
        )
    }

    @Test
    fun drivingUsesTheLongThreshold() {
        gate.shouldRecord(SurveyedPointEntity.OBSERVED_CELLULAR, baseLat, baseLon, 10, drivingSpeed)
        assertFalse(
            gate.shouldRecord(
                SurveyedPointEntity.OBSERVED_CELLULAR,
                latOffset(20.0),
                baseLon,
                10,
                drivingSpeed
            )
        )
        assertTrue(
            gate.shouldRecord(
                SurveyedPointEntity.OBSERVED_CELLULAR,
                latOffset(40.0),
                baseLon,
                10,
                drivingSpeed
            )
        )
    }

    @Test
    fun kindsAreGatedIndependently() {
        gate.shouldRecord(SurveyedPointEntity.OBSERVED_CELLULAR, baseLat, baseLon, 10, walkingSpeed)
        assertTrue(
            gate.shouldRecord(
                SurveyedPointEntity.OBSERVED_WIFI,
                baseLat,
                baseLon,
                10,
                walkingSpeed
            )
        )
        assertTrue(
            gate.shouldRecord(
                SurveyedPointEntity.OBSERVED_BLUETOOTH,
                baseLat,
                baseLon,
                10,
                walkingSpeed
            )
        )
        assertFalse(
            gate.shouldRecord(
                SurveyedPointEntity.OBSERVED_WIFI,
                baseLat,
                baseLon,
                10,
                walkingSpeed
            )
        )
    }

    @Test
    fun rejectedObservationDoesNotMoveTheAnchor() {
        gate.shouldRecord(SurveyedPointEntity.OBSERVED_CELLULAR, baseLat, baseLon, 10, walkingSpeed)
        gate.shouldRecord(
            SurveyedPointEntity.OBSERVED_CELLULAR,
            latOffset(6.0),
            baseLon,
            10,
            walkingSpeed
        )
        // 12 m from the anchor but only 6 m from the rejected observation
        assertTrue(
            gate.shouldRecord(
                SurveyedPointEntity.OBSERVED_CELLULAR,
                latOffset(12.0),
                baseLon,
                10,
                walkingSpeed
            )
        )
    }

    @Test
    fun resetForgetsTheAnchors() {
        gate.shouldRecord(SurveyedPointEntity.OBSERVED_CELLULAR, baseLat, baseLon, 10, walkingSpeed)
        gate.reset()
        assertTrue(
            gate.shouldRecord(
                SurveyedPointEntity.OBSERVED_CELLULAR,
                baseLat,
                baseLon,
                10,
                walkingSpeed
            )
        )
    }
}
