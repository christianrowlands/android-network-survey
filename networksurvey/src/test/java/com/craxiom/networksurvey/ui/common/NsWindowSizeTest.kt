package com.craxiom.networksurvey.ui.common

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the tabs move to the rail and when the bars take their compact sizes. */
class NsWindowSizeTest {

    @Test
    fun aPortraitPhone_keepsTheBottomBar() {
        assertFalse(useRail(DpSize(412.dp, 915.dp)))
    }

    @Test
    fun aLandscapePhone_narrowerThanTheRailWidth_stillGetsTheRail() {
        // A small phone in landscape with the display size turned up
        assertTrue(useRail(DpSize(560.dp, 300.dp)))
    }

    @Test
    fun aWidePortraitWindow_getsTheRail() {
        assertTrue(useRail(DpSize(700.dp, 1000.dp)))
    }

    @Test
    fun onlyAShortWindow_isCompact() {
        assertTrue(isCompactHeight(DpSize(800.dp, 360.dp)))
        assertFalse(isCompactHeight(DpSize(360.dp, 740.dp)))
    }
}
