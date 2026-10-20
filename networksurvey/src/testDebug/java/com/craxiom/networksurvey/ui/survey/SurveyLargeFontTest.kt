package com.craxiom.networksurvey.ui.survey

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.status.DestinationStatus
import com.craxiom.networksurvey.survey.status.StatusAction
import com.craxiom.networksurvey.survey.status.StatusDetail
import com.craxiom.networksurvey.survey.status.StatusWord
import com.craxiom.networksurvey.ui.plural
import com.craxiom.networksurvey.ui.showPage
import com.craxiom.networksurvey.ui.string
import com.craxiom.networksurvey.ui.survey.components.AttentionPlate
import com.craxiom.networksurvey.ui.survey.components.AttentionPlateActions
import com.craxiom.networksurvey.ui.survey.components.RecordTypeTiles
import com.craxiom.networksurvey.ui.survey.components.RecordsPanel
import com.craxiom.networksurvey.ui.survey.components.StillToDoPanel
import com.craxiom.networksurvey.ui.survey.components.labelRes
import com.craxiom.networksurvey.ui.survey.model.AttentionItemUi
import com.craxiom.networksurvey.ui.survey.model.AttentionPlateUi
import com.craxiom.networksurvey.ui.survey.model.ItemLock
import com.craxiom.networksurvey.ui.survey.model.RecordTypeTileUi
import com.craxiom.networksurvey.ui.survey.model.RoutingColumn
import com.craxiom.networksurvey.ui.survey.model.RoutingRowUi
import com.craxiom.networksurvey.ui.survey.model.RoutingTableUi
import com.craxiom.networksurvey.ui.survey.model.StillToDoItemUi
import com.craxiom.networksurvey.ui.survey.sheets.WhatGoesWhereContent
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
 * The Survey tab at font scale 2.0 on a 360 dp phone (found on a Pixel 6 at 2.0): record type tiles
 * go to one column instead of breaking words, Upload now moves under its text, What goes where rows
 * grow instead of clipping and the table spans the sheet, a plate's two actions stay on screen, and
 * the Records legend goes to one column. With four destinations the table's columns still hold
 * every word. At the default scale the tiles and the legend keep their two columns, and a tile
 * toggled at a slightly larger font doesn't reflow the grid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
// Real text measurement, so the large-font checks measure the actual font
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SurveyLargeFontTest {

    @get:Rule
    val compose = createComposeRule()

    private val tiles = listOf(RecordType.CELLULAR, RecordType.WIFI, RecordType.BLUETOOTH, RecordType.GNSS)
        .map { RecordTypeTileUi(it, on = it != RecordType.BLUETOOTH, lock = ItemLock.NONE) }

    private val legendTypes = listOf(RecordType.CELLULAR, RecordType.WIFI, RecordType.BLUETOOTH, RecordType.PHONE_STATE)
    private val legendCounts = legendTypes.associateWith { LEGEND_COUNT }

    @Test
    fun tiles_atFontScale2_useOneColumnAndKeepEveryNameWhole() {
        showScreen(HUGE_FONT) { RecordTypeTiles(tiles = tiles, onToggle = {}, onLocked = {}) }

        val cellular = tileBounds(RecordType.CELLULAR)
        val wifi = tileBounds(RecordType.WIFI)
        assertEquals(cellular.left, wifi.left)
        assertTrue("Wi-Fi $wifi under Cellular $cellular", wifi.top >= cellular.bottom)
        tiles.forEach { assertEquals(it.type.name, 1, compose.textLayout(name(it.type)).lineCount) }
    }

    @Test
    fun tiles_atTheDefaultScale_keepTwoColumns() {
        showScreen(1f) { RecordTypeTiles(tiles = tiles, onToggle = {}, onLocked = {}) }

        val cellular = tileBounds(RecordType.CELLULAR)
        val wifi = tileBounds(RecordType.WIFI)
        val bluetooth = tileBounds(RecordType.BLUETOOTH)
        assertTrue("Wi-Fi $wifi beside Cellular $cellular", wifi.left >= cellular.right && wifi.top == cellular.top)
        assertTrue("Bluetooth $bluetooth on the second row", bluetooth.top >= cellular.bottom && bluetooth.left == cellular.left)
        assertEquals(cellular.bottom - cellular.top, wifi.bottom - wifi.top)
    }

    @Test
    fun tiles_atALargerFont_keepTheirColumnsWhenOneIsToggled() {
        var shown by mutableStateOf(tiles)
        showScreen(LARGER_FONT) {
            RecordTypeTiles(
                tiles = shown,
                onToggle = { type -> shown = shown.map { if (it.type == type) it.copy(on = !it.on) else it } },
                onLocked = {},
            )
        }
        val wifi = tileBounds(RecordType.WIFI)

        compose.onNodeWithText(name(RecordType.BLUETOOTH)).performClick()

        // The check that appears takes a slot that was already there, so nothing moves
        assertTrue(shown.single { it.type == RecordType.BLUETOOTH }.on)
        assertEquals(wifi, tileBounds(RecordType.WIFI))
    }

    @Test
    fun recordsLegend_atFontScale2_usesOneColumn() {
        showScreen(HUGE_FONT) { RecordsPanel(types = legendTypes, counts = legendCounts, savedForUpload = false) }

        val cellular = compose.onNodeWithText(name(RecordType.CELLULAR)).getUnclippedBoundsInRoot()
        val wifi = compose.onNodeWithText(name(RecordType.WIFI)).getUnclippedBoundsInRoot()
        assertTrue("Wi-Fi $wifi under Cellular $cellular", wifi.top >= cellular.bottom && wifi.left == cellular.left)
        legendTypes.forEach { assertEquals(it.name, 1, compose.textLayout(name(it)).lineCount) }
    }

    @Test
    fun recordsLegend_atTheDefaultScale_keepsTwoColumns() {
        showScreen(1f) { RecordsPanel(types = legendTypes, counts = legendCounts, savedForUpload = false) }

        val cellular = compose.onNodeWithText(name(RecordType.CELLULAR)).getUnclippedBoundsInRoot()
        val wifi = compose.onNodeWithText(name(RecordType.WIFI)).getUnclippedBoundsInRoot()
        assertTrue("Wi-Fi $wifi beside Cellular $cellular", wifi.left >= cellular.right && wifi.top == cellular.top)
    }

    @Test
    fun stillToDo_atFontScale2_movesUploadNowUnderTheText() {
        val item = StillToDoItemUi(
            destination = Destination.COMMUNITY,
            pending = PENDING,
            status = DestinationStatus(StatusWord.NOT_UPLOADING, StatusDetail.Default, StatusAction.UPLOAD_NOW),
        )
        showScreen(HUGE_FONT) { StillToDoPanel(items = listOf(item), nowMs = 0L, onUploadNow = {}, onOpen = {}) }

        val records = plural(R.plurals.survey_still_to_do_records, PENDING, PENDING, string(Destination.COMMUNITY.labelRes))
        val text = compose.onNodeWithText(records, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val upload = compose.onNodeWithText(string(R.string.survey_upload_now)).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Upload now $upload under $text", upload.top >= text.bottom && upload.right <= SCREEN_WIDTH)
        val layout = compose.textLayout(records)
        // Every line ends at a space: no word is broken in the middle
        (0 until layout.lineCount - 1).forEach { line ->
            assertEquals(records, ' ', records[layout.getLineEnd(line) - 1])
        }
    }

    @Test
    fun whatGoesWhere_atFontScale2_rowsGrowWithTheirText() {
        var nameLine = 0.dp
        var noteLine = 0.dp
        showPage(HUGE_FONT) {
            // The line heights at this scale (Android scales large text less than linearly)
            with(LocalDensity.current) {
                nameLine = MaterialTheme.typography.bodyMedium.lineHeight.toDp()
                noteLine = MaterialTheme.typography.bodySmall.lineHeight.toDp()
            }
            WhatGoesWhereContent(table = routingTable(), onDismiss = {})
        }

        val bluetooth = rowBounds(R.string.survey_routing_row, RecordType.BLUETOOTH, logFiles)
        val wifi = rowBounds(R.string.survey_routing_row_not_sent, RecordType.WIFI)
        val phoneState = rowBounds(R.string.survey_routing_row, RecordType.PHONE_STATE, logFiles)
        // "Bluetooth" on one line, not "Bluetoot" and a clipped "h"
        assertTrue("Bluetooth $bluetooth line $nameLine", height(bluetooth) >= nameLine && height(bluetooth) < nameLine * 2)
        // The name and "Not sent anywhere" both show
        assertTrue("Wi-Fi $wifi lines $nameLine $noteLine", height(wifi) >= nameLine + noteLine - ROUNDING)
        assertTrue("Phone state $phoneState line $nameLine", height(phoneState) >= nameLine)
    }

    @Test
    fun whatGoesWhere_atFontScale2_withFourDestinations_keepsEveryWordWhole() {
        val headers = listOf(
            string(Destination.NS_ANALYTICS.labelRes),
            string(R.string.survey_dest_community_opencellid),
            string(R.string.survey_dest_community_beacondb),
            logFiles,
        )
        var widestHeaderWord = 0.dp
        var nameLine = 0.dp
        showPage(HUGE_FONT) {
            val measurer = rememberTextMeasurer()
            val style = MaterialTheme.typography.labelMedium
            with(LocalDensity.current) {
                nameLine = MaterialTheme.typography.bodyMedium.lineHeight.toDp()
                widestHeaderWord = headers.flatMap { it.split(' ') }.maxOf { measurer.measure(it, style).size.width }.toDp()
            }
            WhatGoesWhereContent(table = fourColumnTable(), onDismiss = {})
        }

        // The destination columns scroll: their full width is what the visible part shows plus
        // what it can scroll, shared by the four columns
        val line = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .fetchSemanticsNodes().first()
        val scrollable = line.config[SemanticsProperties.HorizontalScrollAxisRange].maxValue()
        val column = with(compose.density) { ((line.size.width + scrollable) / headers.size).toDp() }
        assertTrue("Column $column for the word $widestHeaderWord", column >= widestHeaderWord + HEADER_PADDING * 2 - ROUNDING)
        // Each one-word type name on one line
        listOf(RecordType.CELLULAR, RecordType.BLUETOOTH, RecordType.GNSS).forEach { type ->
            val row = rowBounds(R.string.survey_routing_row, type, logFiles)
            assertTrue("${type.name} $row line $nameLine", height(row) < nameLine * 2)
        }
    }

    @Test
    fun whatGoesWhere_atTheDefaultScale_spansTheSheet() {
        showPage(1f) { WhatGoesWhereContent(table = routingTable(), onDismiss = {}) }

        val bluetooth = rowBounds(R.string.survey_routing_row, RecordType.BLUETOOTH, logFiles)
        // The type column takes what one 76 dp destination column leaves of the 312 dp content
        assertEquals(SCREEN_WIDTH.value - SHEET_PADDING.value - COLUMN_WIDTH.value, bluetooth.right.value, 1f)
    }

    @Test
    fun mqttPausedPlate_atFontScale2_keepsBothActionsOnScreen() {
        showScreen(HUGE_FONT) { AttentionPlate(AttentionPlateUi.MqttOnlyPaused(), NoActions) }

        listOf(R.string.survey_plate_adjust_limit, R.string.survey_plate_disable_limit).map(::string).forEach { action ->
            val bounds = compose.onNodeWithText(action).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$action $bounds", bounds.left >= 0.dp && bounds.right <= SCREEN_WIDTH)
        }
    }

    /** Shows [content] inside the Survey tab's 16 dp side gutters. */
    private fun showScreen(fontScale: Float, content: @Composable () -> Unit) {
        compose.showPage(fontScale = fontScale) { Box(Modifier.padding(horizontal = GUTTER)) { content() } }
    }

    private fun showPage(fontScale: Float, content: @Composable () -> Unit) = compose.showPage(fontScale = fontScale, content = content)

    private fun name(type: RecordType) = string(type.labelRes)

    private fun tileBounds(type: RecordType) = compose.onNodeWithText(name(type)).getUnclippedBoundsInRoot()

    private fun rowBounds(format: Int, type: RecordType, sentTo: String? = null) =
        compose.onNodeWithContentDescription(if (sentTo == null) string(format, name(type)) else string(format, name(type), sentTo))
            .getUnclippedBoundsInRoot()

    private fun height(bounds: DpRect): Dp = bounds.bottom - bounds.top

    /** Log files takes everything; nothing takes Wi-Fi, so its row adds "Not sent anywhere". */
    private fun routingTable() = RoutingTableUi(
        columns = listOf(RoutingColumn.LOG_FILES),
        rows = listOf(
            RoutingRowUi(RecordType.PHONE_STATE, setOf(RoutingColumn.LOG_FILES)),
            RoutingRowUi(RecordType.WIFI, emptySet()),
            RoutingRowUi(RecordType.BLUETOOTH, setOf(RoutingColumn.LOG_FILES)),
        ),
        unselected = emptyList(),
    )

    /** NS Analytics, both community databases and Log files, the most columns a survey usually shows. */
    private fun fourColumnTable() = RoutingTableUi(
        columns = listOf(RoutingColumn.NS_ANALYTICS, RoutingColumn.OPENCELLID, RoutingColumn.BEACONDB, RoutingColumn.LOG_FILES),
        rows = listOf(RecordType.CELLULAR, RecordType.BLUETOOTH, RecordType.GNSS).map { RoutingRowUi(it, setOf(RoutingColumn.LOG_FILES)) },
        unselected = emptyList(),
    )

    private val logFiles: String
        get() = string(Destination.LOG_FILES.labelRes)

    private object NoActions : AttentionPlateActions {
        override fun onChangeBatteryThreshold() = Unit
        override fun onAdjustQueueLimit() = Unit
        override fun onDisableQueueLimit() = Unit
        override fun onOpenOrganizationSettings() = Unit
        override fun onOpenDestination(destination: Destination) = Unit
        override fun onFixItem(item: AttentionItemUi) = Unit
    }

    private companion object {
        const val HUGE_FONT = 2f
        const val LARGER_FONT = 1.15f
        const val PENDING = 93
        const val LEGEND_COUNT = 12_345L
        val SCREEN_WIDTH = 360.dp
        val GUTTER = 16.dp
        val SHEET_PADDING = 24.dp
        val COLUMN_WIDTH = 76.dp
        val ROUNDING = 1.dp
        val HEADER_PADDING = 2.dp
    }
}
