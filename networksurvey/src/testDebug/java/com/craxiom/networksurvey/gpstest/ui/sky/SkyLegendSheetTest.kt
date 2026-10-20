package com.craxiom.networksurvey.gpstest.ui.sky

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.textLayout
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sky legend lists every mark the plot draws, at a small phone's width. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class SkyLegendSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val sections = listOf(
        "Signal strength (C/N0)",
        "Signal availability",
        "Global Navigation Satellite Systems (GNSS)",
        "Satellite-based Augmentation Systems (SBAS)",
    )

    private val rows = listOf(
        "Not in view of device", "In view", "Used in fix",
        "NAVSTAR GPS (USA)", "GLONASS (Russia)", "Galileo (European Union)", "BeiDou/COMPASS (China)",
        "QZSS (Japan)", "IRNSS (India)",
        "WAAS (USA)", "EGNOS (European Union)", "GAGAN (India)", "MSAS (Japan)", "SDCM (Russia)",
        "SNAS (China)", "SACCSA (ICAO)", "SouthPAN (Australia / New Zealand)",
    )

    @Test
    fun everySectionAndRow_isListed() {
        compose.showPage { SkyLegendContent() }

        compose.onNodeWithText("Legend").assertIsDisplayed()
        compose.onNodeWithText("N marks north", substring = true).assertIsDisplayed()
        (sections + rows).forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atLargeText_theNamesWrapInsteadOfClipping() {
        compose.showPage(largeFont = true) { SkyLegendContent() }

        rows.forEach { compose.onNodeWithText(it).performScrollTo() }
        rows.forEach { assertFalse(it, compose.textLayout(it).isLineEllipsized(0)) }
    }
}
