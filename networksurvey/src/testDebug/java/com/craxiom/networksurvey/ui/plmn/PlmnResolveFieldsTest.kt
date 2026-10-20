package com.craxiom.networksurvey.ui.plmn

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.craxiom.networksurvey.ui.plmn.components.PlmnResolveFields
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The PLMN lookup's MCC and MNC fields sit in fixed-height cards, so their large readout must
 * still fit inside the card at a large font size.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
class PlmnResolveFieldsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atLargeText_theCodesFitInsideTheirCards() {
        compose.showPage(largeFont = true) {
            PlmnResolveFields(
                mcc = MCC,
                mnc = MNC,
                onMcc = {},
                onMnc = {},
                mccFocusRequester = remember { FocusRequester() },
                mncFocusRequester = remember { FocusRequester() },
            )
        }

        val cards = compose.onRoot().getUnclippedBoundsInRoot()
        listOf(MCC, MNC).forEach { code ->
            val field = compose.onNodeWithText(code).getUnclippedBoundsInRoot()
            assertTrue("$code field $field cards $cards", field.top >= cards.top && field.bottom <= cards.bottom)
            val layout = compose.textLayout(code)
            assertEquals(code, 1, layout.lineCount)
            // A text field clips rather than ellipsizes, so a readout taller than its slot shows as overflow
            assertFalse(code, layout.hasVisualOverflow)
        }
    }

    private companion object {
        const val MCC = "310"
        const val MNC = "260"
    }
}
