package com.craxiom.networksurvey.ui.theme

import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.util.ColorUtils
import com.craxiom.networksurvey.util.WifiSignalCategory
import com.craxiom.networksurvey.util.toWifiSignalCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The app has one signal ramp. The View screens (via [ColorUtils.getColorForSignalStrength]) and
 * the Compose screens (via [toWifiSignalCategory]) must bucket every dBm value the same way and
 * land on the same color.
 */
@RunWith(RobolectricTestRunner::class)
class SignalRampTest {

    private val xmlColorFor = mapOf(
        WifiSignalCategory.STRONG to R.color.rssi_green,
        WifiSignalCategory.GOOD to R.color.rssi_yellow,
        WifiSignalCategory.FAIR to R.color.rssi_orange,
        WifiSignalCategory.WEAK to R.color.rssi_red,
        WifiSignalCategory.VERY_WEAK to R.color.rssi_deep_red,
    )

    private val paletteColorFor = mapOf(
        WifiSignalCategory.STRONG to NsPalette.Signal.Strong,
        WifiSignalCategory.GOOD to NsPalette.Signal.Good,
        WifiSignalCategory.FAIR to NsPalette.Signal.Fair,
        WifiSignalCategory.WEAK to NsPalette.Signal.Weak,
        WifiSignalCategory.VERY_WEAK to NsPalette.Signal.VeryWeak,
    )

    @Test
    fun viewAndComposeBucketEveryBoundaryTheSameWay() {
        for (boundary in listOf(-60, -70, -80, -90)) {
            for (dbm in listOf(boundary + 1, boundary, boundary - 1)) {
                val category = dbm.toWifiSignalCategory()
                assertEquals("dBm $dbm", xmlColorFor.getValue(category), ColorUtils.getColorForSignalStrength(dbm.toFloat()))
            }
        }
    }

    @Test
    fun categoryColorsComeFromThePalette() {
        for (category in WifiSignalCategory.entries) {
            assertEquals(category.name, paletteColorFor.getValue(category), category.color)
        }
    }
}
