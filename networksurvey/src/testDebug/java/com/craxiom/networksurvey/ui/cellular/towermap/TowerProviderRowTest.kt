package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.api.PlmnRecord
import com.craxiom.networksurvey.ui.LARGE_FONT_SCALE
import com.craxiom.networksurvey.ui.plural
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
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
 * The tower sheet's provider row on a narrow phone: it keeps one height while the lookup answers
 * (so the sheet does not jump), a long name wraps without pushing its badge off screen, a shared
 * PLMN expands in place, and the list rows show the name beside the MCC-MNC.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TowerProviderRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun showStates(largeFont: Boolean) = compose.showPage(largeFont = largeFont) {
        Column {
            STATES.forEachIndexed { index, provider ->
                Box(Modifier.testTag("$STATE_TAG$index")) { Row(provider) }
            }
        }
    }

    @Composable
    private fun Row(provider: TowerProvider) {
        TowerProviderRow(
            provider = provider,
            providerColor = Color.Magenta,
            onColorClick = { calls += "color" },
            onRetry = { calls += "retry" }
        )
    }

    private fun heightOf(tag: String): Dp = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { it.bottom - it.top }

    @Test
    fun everyState_hasTheSameHeight() {
        showStates(largeFont = false)
        assertOneHeight()
    }

    @Test
    fun atLargeText_everyState_stillHasTheSameHeight() {
        showStates(largeFont = true)
        assertOneHeight()
    }

    private fun assertOneHeight() {
        val heights = STATES.indices.map { heightOf("$STATE_TAG$it") }
        assertTrue("heights $heights", heights.all { it == heights.first() })
    }

    @Test
    fun aKnownProvider_showsItsBrand_legalName_andCountry() {
        compose.showPage { Row(TowerProvider.Known(listOf(TMOBILE))) }

        compose.onNodeWithText("T-Mobile").assertIsDisplayed()
        compose.onNodeWithText("T-Mobile USA").assertIsDisplayed()
        compose.onNodeWithText("United States").assertIsDisplayed()
    }

    @Test
    fun atLargeText_aLongName_wrapsToTwoLines_andKeepsItsBadgeOnScreen() {
        val long = "Special State Protection Service of the Republic of Azerbaijan"
        compose.showPage(largeFont = true) {
            Row(TowerProvider.Known(listOf(record("400", "06", long, "AZ"), record("400", "06", "Other", "AZ"))))
        }

        assertTrue(compose.textLayout(long).lineCount <= 2)
        val badge = compose.onNodeWithContentDescription(plural(R.plurals.tower_provider_more_names, 1, 1)).getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue("badge $badge root $root", badge.right <= root.right && badge.left >= root.left)
    }

    @Test
    fun aSharedPlmn_expandsInPlace_toListEveryCountry() {
        compose.showPage { Row(TowerProvider.Known(DIGICEL)) }

        val summary = plural(R.plurals.tower_provider_shared_countries, 2, 2)
        compose.onNodeWithText(summary).assertIsDisplayed()
        compose.onNodeWithText("Digicel Bermuda").assertDoesNotExist()

        compose.onNodeWithText(summary).performClick()
        compose.onNodeWithText("Digicel Bermuda").assertIsDisplayed()
        compose.onNodeWithText("Bermuda").assertIsDisplayed()

        compose.onNodeWithText(summary).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Digicel Bermuda").assertDoesNotExist()
    }

    @Test
    fun aFailedLookup_triesAgainWhenTapped_andTheDotOpensTheColorPicker() {
        compose.showPage { Row(TowerProvider.Unavailable(offline = true)) }

        compose.onNodeWithText(string(R.string.tower_provider_offline)).performClick()
        compose.onNodeWithContentDescription(string(R.string.tower_provider_change_color)).performClick()

        assertEquals(listOf("retry", "color"), calls)
    }

    @Test
    fun aListRow_showsTheNameBesideTheMccMnc_onceItIsKnown() {
        compose.showPage(largeFont = true) {
            Column {
                TowerListProviderLine(TowerProvider.Loading, "310", "260")
                TowerListProviderLine(TowerProvider.Known(listOf(TMOBILE)), "310", "260")
            }
        }

        compose.onNodeWithText(string(R.string.label_value_pair, string(R.string.mcc_mnc_label), "310-260")).assertIsDisplayed()
        compose.onNodeWithText("T-Mobile").assertIsDisplayed()
        assertEquals(1, compose.textLayout("310-260").lineCount)
    }

    private companion object {
        const val STATE_TAG = "state"

        val TMOBILE = PlmnRecord(
            mcc = "310", mnc = "260", plmn = "310260", country = "United States", iso = "US",
            operator = "T-Mobile USA", brand = "T-Mobile"
        )

        val DIGICEL = listOf(
            PlmnRecord(mcc = "338", mnc = "050", plmn = "338050", country = "Jamaica", iso = "JM", brand = "Digicel"),
            PlmnRecord(mcc = "338", mnc = "050", plmn = "338050", country = "Bermuda", iso = "BM", brand = "Digicel Bermuda"),
        )

        val STATES = listOf(
            TowerProvider.Loading,
            TowerProvider.Known(listOf(TMOBILE)),
            TowerProvider.Known(DIGICEL),
            TowerProvider.NotListed,
            TowerProvider.Unavailable(offline = true),
            TowerProvider.Unavailable(offline = false),
        )

        fun record(mcc: String, mnc: String, brand: String, iso: String) =
            PlmnRecord(mcc = mcc, mnc = mnc, plmn = mcc + mnc, iso = iso, country = iso, brand = brand)
    }
}
