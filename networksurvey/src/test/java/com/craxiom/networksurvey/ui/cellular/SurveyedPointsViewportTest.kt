package com.craxiom.networksurvey.ui.cellular

import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointsController
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the longitude handling that lets the survey points query survive a viewport that
 * crosses the antimeridian, where MapLibre reports a longitude past 180 or an inverted box.
 */
class SurveyedPointsViewportTest {

    @Test
    fun ordinaryViewportIsOneRange() {
        assertEquals(listOf(10.0 to 20.0), SurveyedPointsController.longitudeRanges(10.0, 20.0))
        assertEquals(10.0, SurveyedPointsController.longitudeSpan(10.0, 20.0), 0.0)
    }

    @Test
    fun eastPast180SplitsAtTheDateline() {
        assertEquals(
            listOf(170.0 to 180.0, -180.0 to -175.0),
            SurveyedPointsController.longitudeRanges(170.0, 185.0)
        )
        assertEquals(15.0, SurveyedPointsController.longitudeSpan(170.0, 185.0), 0.0)
    }

    @Test
    fun westBelowMinus180SplitsAtTheDateline() {
        assertEquals(
            listOf(175.0 to 180.0, -180.0 to -170.0),
            SurveyedPointsController.longitudeRanges(-185.0, -170.0)
        )
        assertEquals(15.0, SurveyedPointsController.longitudeSpan(-185.0, -170.0), 0.0)
    }

    @Test
    fun invertedBoxIsTreatedAsCrossingTheDateline() {
        assertEquals(
            listOf(170.0 to 180.0, -180.0 to -175.0),
            SurveyedPointsController.longitudeRanges(170.0, -175.0)
        )
        assertEquals(15.0, SurveyedPointsController.longitudeSpan(170.0, -175.0), 0.0)
    }

    @Test
    fun wholeWorldIsOneFullRange() {
        assertEquals(
            listOf(-180.0 to 180.0),
            SurveyedPointsController.longitudeRanges(-200.0, 200.0)
        )
        assertEquals(360.0, SurveyedPointsController.longitudeSpan(-200.0, 200.0), 0.0)
    }
}
