package com.craxiom.networksurvey.ui.cellular

import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointSourceFilter
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointTimeFilter
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointUploadFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [SurveyedPointFilterSelection]. Both the on-map chips and the "no matching points in view"
 * message hang off [SurveyedPointFilterSelection.isActive], so a wrong answer here means either a
 * blank map with no explanation or chips that never go away.
 */
class SurveyedPointFilterSelectionTest {

    @Test
    fun defaultsAreNotActive() {
        assertFalse(SurveyedPointFilterSelection().isActive)
    }

    @Test
    fun anyNonDefaultFilterIsActive() {
        assertTrue(
            SurveyedPointFilterSelection(time = SurveyedPointTimeFilter.LAST_HOUR).isActive
        )
        assertTrue(
            SurveyedPointFilterSelection(time = SurveyedPointTimeFilter.LATEST_SURVEY).isActive
        )
        assertTrue(
            SurveyedPointFilterSelection(source = SurveyedPointSourceFilter.NS_ANALYTICS).isActive
        )
        assertTrue(
            SurveyedPointFilterSelection(upload = SurveyedPointUploadFilter.NOT_SENT).isActive
        )
        assertTrue(
            SurveyedPointFilterSelection(upload = SurveyedPointUploadFilter.SENT).isActive
        )
    }

    @Test
    fun clearedReturnsEveryFilterToItsDefault() {
        val filtered = SurveyedPointFilterSelection(
            time = SurveyedPointTimeFilter.LATEST_SURVEY,
            source = SurveyedPointSourceFilter.COMMUNITY,
            upload = SurveyedPointUploadFilter.NOT_SENT,
        )

        assertEquals(SurveyedPointFilterSelection(), filtered.cleared())
        assertFalse(filtered.cleared().isActive)
    }
}
