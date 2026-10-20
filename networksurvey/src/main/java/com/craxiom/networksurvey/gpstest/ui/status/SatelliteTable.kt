package com.craxiom.networksurvey.gpstest.ui.status

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.CappedFontScale
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.common.panelPart
import com.craxiom.networksurvey.ui.theme.NsTheme

/** The width of the fade that says the table scrolls sideways. */
private val FADE_WIDTH = 24.dp

/** The thickness of the line under the sorted column's header. */
private val SORT_UNDERLINE = 2.dp

/**
 * The sticky top of a satellite table: the section's title, the sort, the key to the status
 * letters, and the column headers on the panel's rounded top. Tapping a sortable header sorts by
 * it. Drawn on the page's color so the rows scroll under it.
 *
 * @param sortLabel The sort the user chose ("Signal strength"), or null to leave the line out.
 * @param showKey False to leave out the key to the status letters (a second table under the first).
 * @param sortIndex The chosen sort's index in the sort_sats array, for the header it marks.
 * @param scroll The shared sideways scroll when the table [TableLayout.scrolls], else null.
 */
@Composable
fun SatelliteTableHeader(
    title: String,
    sortLabel: String?,
    showKey: Boolean,
    layout: TableLayout,
    sortIndex: Int,
    onSort: (Int) -> Unit,
    scroll: ScrollState?,
    modifier: Modifier = Modifier,
) {
    val legend = stringResource(
        R.string.gnss_flags_legend,
        stringResource(R.string.gnss_flag_used),
        stringResource(R.string.gnss_flag_ephemeris),
        stringResource(R.string.gnss_flag_almanac),
    )
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .nsContentWidth()
            .padding(horizontal = NsTheme.spacing.screenGutter),
    ) {
        SectionHeader(title = title, topPadding = NsTheme.spacing.s)
        if (sortLabel != null) {
            Text(
                text = stringResource(R.string.gnss_status_sorted_by, sortLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showKey) {
            Text(
                text = legend,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier
                .padding(top = NsTheme.spacing.s)
                .fillMaxWidth()
                .clip(panelPart(first = true, last = false))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(top = NsTheme.spacing.xs)
                .padding(horizontal = NsTheme.spacing.panelPaddingHorizontal)
        ) {
            CappedFontScale {
                Row(
                    modifier = Modifier.sideways(scroll),
                    horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    layout.columns.forEach { column ->
                        HeaderCell(column, layout.widths.getValue(column), sortIndex, onSort)
                    }
                }
            }
        }
    }
}

/** One column header: its name; the one the table is sorted by is bold, colored and underlined. */
@Composable
private fun HeaderCell(column: TableColumn, width: Dp, sortIndex: Int, onSort: (Int) -> Unit) {
    val name = when (column) {
        TableColumn.ID -> stringResource(R.string.gnss_col_id)
        // The flags say what they are; the column has no name
        TableColumn.FLAG -> ""
        TableColumn.TREND -> stringResource(R.string.gnss_trend_window)
        TableColumn.BAND -> stringResource(R.string.gnss_col_band)
        TableColumn.CN0 -> stringResource(R.string.gnss_col_cn0)
        TableColumn.AGC -> stringResource(R.string.sort_sats_agc)
        TableColumn.EL -> stringResource(R.string.gnss_col_el)
        TableColumn.AZ -> stringResource(R.string.gnss_col_az)
        // Its own letters; the key above the table says what they mean
        TableColumn.STATUS -> listOf(R.string.gnss_flag_used, R.string.gnss_flag_ephemeris, R.string.gnss_flag_almanac)
            .map { stringResource(it) }.joinToString("")
    }
    val sorts = column.sortIndex
    val active = sorts != null && sorts == sortIndex
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val sortLabels = stringArrayResource(R.array.sort_sats)
    val sortedWord = stringResource(R.string.gnss_sorted)
    val clickable = if (sorts == null) {
        Modifier.clearAndSetSemantics {}
    } else {
        Modifier
            .clickable(
                onClickLabel = stringResource(R.string.gnss_sort_action, sortLabels.getOrElse(sorts) { name }),
                role = Role.Button,
            ) { onSort(sorts) }
            .semantics {
                // The status letters are read as what they sort by
                contentDescription = if (column == TableColumn.STATUS) sortLabels.getOrElse(sorts) { name } else name
                if (active) stateDescription = sortedWord
            }
    }
    Box(
        modifier = Modifier
            .width(width)
            .heightIn(min = NsTheme.spacing.touchTarget)
            .then(clickable),
        contentAlignment = column.alignment,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else null,
            color = color,
            maxLines = 1,
            modifier = Modifier
                .clearAndSetSemantics {}
                .then(if (active) Modifier.underline(color) else Modifier),
        )
    }
}

/**
 * The mark under the header the table is sorted by. Each header has one sort, so no arrow is
 * needed for its direction, and an underline takes no width from the narrow columns.
 */
private fun Modifier.underline(color: Color): Modifier = drawBehind {
    val stroke = SORT_UNDERLINE.toPx()
    val y = size.height + stroke
    drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
}

/** How a column lines up its header and values: numbers at the end, words at the start. */
internal val TableColumn.alignment: Alignment
    get() = when (this) {
        TableColumn.ID, TableColumn.CN0, TableColumn.AGC, TableColumn.EL, TableColumn.AZ -> Alignment.CenterEnd
        TableColumn.STATUS, TableColumn.FLAG -> Alignment.Center
        TableColumn.BAND, TableColumn.TREND -> Alignment.CenterStart
    }

/**
 * Scrolls a row's columns sideways with [scroll] when the table does not fit; the header and every
 * row share the one state and have the same column widths, so they move together. A fade at the
 * end says there is more.
 */
@Composable
internal fun Modifier.sideways(scroll: ScrollState?): Modifier {
    if (scroll == null) return this
    val fade = MaterialTheme.colorScheme.surfaceContainerLow
    return this
        .drawWithContent {
            drawContent()
            if (scroll.canScrollForward) {
                val width = FADE_WIDTH.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, fade), startX = size.width - width, endX = size.width),
                    topLeft = Offset(size.width - width, 0f),
                )
            }
        }
        .horizontalScroll(scroll, overscrollEffect = null)
}
