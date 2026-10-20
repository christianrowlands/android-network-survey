package com.craxiom.networksurvey.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.textLayout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shared action layouts at font scale 2.0 on a 360 dp phone: the lock sheet's actions wrap
 * instead of running off the screen, OK stays last at the right edge, and the primary button grows
 * with its text. At the default scale on a Pixel 6 width the lock sheet keeps its one line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
// Real text measurement, so the large-font checks measure the actual font
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LargeFontActionsTest {

    @get:Rule
    val compose = createComposeRule()

    private val seeAll = string(R.string.survey_org_see_all)
    private val useOwn = string(R.string.survey_lock_use_own)
    private val ok = string(R.string.ok)

    @Test
    fun lockSheet_atFontScale2_everyActionIsOnScreenAndOkIsLast() {
        compose.showPage(fontScale = HUGE_FONT) { NsManagedLockContent(onSeeAll = {}, onUseOwnSettings = {}, onDismiss = {}) }

        val bounds = listOf(seeAll, useOwn, ok).map { text ->
            compose.onNodeWithText(text).assertIsDisplayed().getUnclippedBoundsInRoot().also { assertOnScreen(text, it) }
        }
        val (seeAllBounds, useOwnBounds, okBounds) = bounds
        assertTrue("OK $okBounds after $useOwnBounds", okBounds.top >= useOwnBounds.top && okBounds.top >= seeAllBounds.top)
        // OK keeps the right edge of the sheet's content (360 dp less the 24 dp padding)
        assertTrue("OK $okBounds", okBounds.right >= SCREEN_WIDTH - SHEET_PADDING - 1.dp)
        listOf(seeAll, useOwn, ok).forEach { assertTrue(it, compose.textLayout(it).lineCount == 1) }
    }

    @Test
    @Config(qualifiers = "w411dp-h900dp-xxhdpi")
    fun lockSheet_atTheDefaultScale_keepsOneLine() {
        compose.showPage(fontScale = 1f) { NsManagedLockContent(onSeeAll = {}, onUseOwnSettings = {}, onDismiss = {}) }

        val seeAllBounds = compose.onNodeWithText(seeAll).getUnclippedBoundsInRoot()
        val useOwnBounds = compose.onNodeWithText(useOwn).getUnclippedBoundsInRoot()
        val okBounds = compose.onNodeWithText(ok).getUnclippedBoundsInRoot()
        assertTrue("$seeAllBounds $useOwnBounds", useOwnBounds.left >= seeAllBounds.right && useOwnBounds.top < seeAllBounds.bottom)
        assertTrue("$useOwnBounds $okBounds", okBounds.left >= useOwnBounds.right && okBounds.top < useOwnBounds.bottom)
        assertTrue("OK $okBounds", okBounds.right >= PIXEL_6_WIDTH - SHEET_PADDING - 1.dp)
    }

    @Test
    fun primaryCta_atFontScale2_growsWithATwoLineLabel() {
        compose.showPage(fontScale = HUGE_FONT) { NsPrimaryCta(text = LONG_CTA, onClick = {}) }

        val button = compose.onNodeWithText(LONG_CTA).getUnclippedBoundsInRoot()
        assertTrue(compose.textLayout(LONG_CTA).lineCount >= 2)
        assertTrue("CTA $button", button.bottom - button.top > CTA_HEIGHT)
    }

    private fun assertOnScreen(text: String, bounds: DpRect) {
        assertTrue("$text $bounds", bounds.left >= 0.dp && bounds.right <= SCREEN_WIDTH)
    }

    private companion object {
        const val HUGE_FONT = 2f
        const val LONG_CTA = "Spustiť nový prieskum mobilnej siete teraz"
        val SCREEN_WIDTH = 360.dp
        val PIXEL_6_WIDTH = 411.dp
        val SHEET_PADDING = 24.dp
        val CTA_HEIGHT = 56.dp
    }
}
