package com.craxiom.networksurvey.ui.theme

import com.craxiom.networksurvey.util.WifiSignalCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every [WifiSignalCategory] takes its color from the shared [NsPalette.Signal] ramp.
 */
@RunWith(RobolectricTestRunner::class)
class SignalRampTest {

    private val paletteColorFor = mapOf(
        WifiSignalCategory.STRONG to NsPalette.Signal.Strong,
        WifiSignalCategory.GOOD to NsPalette.Signal.Good,
        WifiSignalCategory.FAIR to NsPalette.Signal.Fair,
        WifiSignalCategory.WEAK to NsPalette.Signal.Weak,
        WifiSignalCategory.VERY_WEAK to NsPalette.Signal.VeryWeak,
    )

    @Test
    fun categoryColorsComeFromThePalette() {
        for (category in WifiSignalCategory.entries) {
            assertEquals(category.name, paletteColorFor.getValue(category), category.color)
        }
    }
}
