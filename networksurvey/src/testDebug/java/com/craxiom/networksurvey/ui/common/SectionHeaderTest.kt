package com.craxiom.networksurvey.ui.common

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shared section header: its right-hand text or action sits beside the title only when the
 * whole title fits beside it, and moves under the title otherwise, so the title is never squeezed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class SectionHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aShortCount_sitsAtTheEndOfTheTitleLine() {
        compose.showPage { SectionHeader(title = SHORT_TITLE, trailing = COUNT) }

        val title = compose.onNodeWithText(SHORT_TITLE).getUnclippedBoundsInRoot()
        val count = compose.onNodeWithText(COUNT).getUnclippedBoundsInRoot()
        assertTrue("title $title count $count", count.top < title.bottom && count.right > 300.dp)
        assertEquals(1, compose.textLayout(SHORT_TITLE).lineCount)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aLongTrailingText_movesUnderTheTitle() {
        compose.showPage(largeFont = true) { SectionHeader(title = SHORT_TITLE, trailing = LONG_TRAILING) }

        assertBelowTitle(SHORT_TITLE, LONG_TRAILING)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aMultiWordTitle_isNotSqueezedByAMediumTrailingText() {
        compose.showPage(largeFont = true) { SectionHeader(title = LONG_TITLE, trailing = MEDIUM_TRAILING) }

        assertBelowTitle(LONG_TITLE, MEDIUM_TRAILING)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun anActionThatDoesNotFit_movesUnderTheTitle() {
        compose.showPage(largeFont = true) {
            SectionHeader(title = ACTION_TITLE, action = { NsTextAction(text = ACTION, onClick = {}) })
        }

        assertBelowTitle(ACTION_TITLE, ACTION)
    }

    private fun assertBelowTitle(titleText: String, endText: String) {
        val title = compose.onNodeWithText(titleText).getUnclippedBoundsInRoot()
        val end = compose.onNodeWithText(endText).getUnclippedBoundsInRoot()
        assertTrue("title $title end $end", end.top >= title.bottom && end.right <= 360.dp)
        assertEquals(titleText, 1, compose.textLayout(titleText).lineCount)
    }

    private companion object {
        const val SHORT_TITLE = "Satellites"
        const val LONG_TITLE = "Where should the data go?"
        const val ACTION_TITLE = "Saved survey setups on this phone"
        const val COUNT = "3 of 6"
        const val MEDIUM_TRAILING = "Sorted by Constellation"
        const val LONG_TRAILING = "Sorted by Constellation, Carrier Frequency"
        const val ACTION = "Manage"
    }
}
