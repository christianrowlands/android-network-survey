package com.craxiom.networksurvey.ui.map

import com.craxiom.networksurvey.ui.cellular.SurveyedPointFilterSelection
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointSourceFilter
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointTimeFilter
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointUploadFilter
import org.junit.Assert.assertEquals
import org.junit.Test

/** The count badged on the Map's Filters button. */
class MapFilterCountTest {

    private val allPointFilters = SurveyedPointFilterSelection(
        time = SurveyedPointTimeFilter.entries.first { it != SurveyedPointTimeFilter.ANY },
        source = SurveyedPointSourceFilter.entries.first { it != SurveyedPointSourceFilter.BOTH },
        upload = SurveyedPointUploadFilter.entries.first { it != SurveyedPointUploadFilter.ANY },
    )

    @Test
    fun nothingNarrowed_countsNothing() {
        assertEquals(0, count(operatorSet = false, maxAge = 0, pointFilters = SurveyedPointFilterSelection()))
    }

    @Test
    fun theOperatorAndTheAge_countWhileTowersShow() {
        assertEquals(2, count(operatorSet = true, maxAge = 12, pointFilters = SurveyedPointFilterSelection()))
        assertEquals(0, count(showTowers = false, operatorSet = true, maxAge = 12, pointFilters = SurveyedPointFilterSelection()))
    }

    @Test
    fun eachSurveyPointFilter_countsWhileSurveyPointsShow() {
        assertEquals(4, count(operatorSet = true, maxAge = 0, pointFilters = allPointFilters))
        assertEquals(1, count(operatorSet = true, maxAge = 0, showPoints = false, pointFilters = allPointFilters))
    }

    private fun count(
        showTowers: Boolean = true,
        operatorSet: Boolean,
        maxAge: Int,
        showPoints: Boolean = true,
        pointFilters: SurveyedPointFilterSelection,
    ) = activeMapFilterCount(showTowers, operatorSet, maxAge, showPoints, pointFilters)
}
