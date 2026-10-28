package com.craxiom.networksurvey.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.craxiom.networksurvey.ui.showPage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Map settings: the coverage circle opacity summary writes its percentages, not a literal "%%". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapSettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun opacitySummary_writesThePercentages() {
        compose.showPage { MapSettingsScreen(onBack = {}, onOpenProviderColors = {}) }
        compose.onNodeWithText("Adjust transparency of the coverage circle fill (0% = no fill, 100% = solid fill)")
            .performScrollTo()
            .assertExists()
    }
}
