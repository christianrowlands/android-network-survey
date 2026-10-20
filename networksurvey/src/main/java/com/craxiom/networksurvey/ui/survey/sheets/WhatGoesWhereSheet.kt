package com.craxiom.networksurvey.ui.survey.sheets

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.ui.survey.components.joinNames
import com.craxiom.networksurvey.ui.survey.components.labelRes
import com.craxiom.networksurvey.ui.survey.components.recordTypeNames
import com.craxiom.networksurvey.ui.survey.model.RoutingColumn
import com.craxiom.networksurvey.ui.survey.model.RoutingRowUi
import com.craxiom.networksurvey.ui.survey.model.RoutingTableUi
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * "What goes where": the survey's record types against the destinations that are on, with the
 * community databases split into OpenCelliD and BeaconDB. A type no destination takes is flagged.
 * The record type column stays put; the destination columns scroll sideways when they don't fit.
 * Rows and headings grow with the font scale rather than clip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatGoesWhereSheet(table: RoutingTableUi, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        WhatGoesWhereContent(table, onDismiss)
    }
}

/** The sheet's title, table, footnote and Done. */
@Composable
internal fun WhatGoesWhereContent(table: RoutingTableUi, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NsTheme.spacing.xxl)
            .padding(bottom = NsTheme.spacing.xxl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        Text(
            text = stringResource(R.string.survey_what_goes_where),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        RoutingTable(table)
        Footnote(table)
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.End)
                .heightIn(min = NsTheme.spacing.touchTarget),
        ) { Text(stringResource(R.string.survey_done)) }
    }
}

/**
 * One line of the table per record type, the type cell and its marks the height of the taller, so
 * rows grow with the font instead of clipping. Every column is at least as wide as its widest word,
 * so no name is broken in the middle. The type column takes the width the destination columns
 * leave; those scroll sideways together when they don't fit.
 */
@Composable
private fun RoutingTable(table: RoutingTableUi) {
    val headerWord = widestWord(table.columns.map { stringResource(it.labelRes) }, MaterialTheme.typography.labelMedium)
    val typeNames = table.rows.map { stringResource(it.type.labelRes) } +
            listOfNotNull(stringResource(R.string.survey_routing_device_status).takeIf { table.mqttDeviceStatus })
    val typeWord = widestWord(typeNames, MaterialTheme.typography.bodyMedium)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widths = routingWidths(
            width = maxWidth,
            columnCount = table.columns.size,
            column = maxOf(COLUMN_WIDTH, headerWord + NsTheme.spacing.xxs * 2),
            typeMin = maxOf(TYPE_COLUMN_WIDTH, typeWord),
        )
        val scroll = rememberScrollState()
        Column {
            TableLine(widths, scroll, type = { Box(it) }) {
                table.columns.forEach { HeaderCell(it, Modifier.width(widths.column)) }
            }
            table.rows.forEach { row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TableLine(widths, scroll, type = { TypeCell(row, table.columns, it) }) {
                    table.columns.forEach { column -> MarkCell(column in row.sentTo, Modifier.width(widths.column)) }
                }
            }
            if (table.mqttDeviceStatus) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TableLine(widths, scroll, type = { DeviceStatusCell(it) }) {
                    table.columns.forEach { column -> MarkCell(column == RoutingColumn.MQTT, Modifier.width(widths.column)) }
                }
            }
        }
    }
}

/** A line of the table: the type cell, then the destination cells in the shared sideways [scroll]. */
@Composable
private fun TableLine(
    widths: RoutingWidths,
    scroll: ScrollState,
    type: @Composable (Modifier) -> Unit,
    cells: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        type(
            Modifier
                .width(widths.type)
                .fillMaxHeight()
        )
        Row(
            modifier = Modifier
                .width(widths.marks)
                .fillMaxHeight()
                // Each line scrolls the shared state; without its own overscroll stretch, so the
                // lines never pull apart at the ends
                .horizontalScroll(scroll, overscrollEffect = null),
            content = cells,
        )
    }
}

/** The record type, read by TalkBack together with where it goes, so the marks can stay silent. */
@Composable
private fun TypeCell(row: RoutingRowUi, columns: List<RoutingColumn>, modifier: Modifier) {
    val name = stringResource(row.type.labelRes)
    val sentTo = columns.filter { it in row.sentTo }.map { stringResource(it.labelRes) }
    val description = if (row.notSentAnywhere) {
        stringResource(R.string.survey_routing_row_not_sent, name)
    } else {
        stringResource(R.string.survey_routing_row, name, joinNames(sentTo))
    }
    Column(
        modifier = modifier
            .heightIn(min = ROW_HEIGHT)
            .padding(vertical = NsTheme.spacing.xs)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        if (row.notSentAnywhere) {
            Text(
                text = stringResource(R.string.survey_type_not_sent_anywhere),
                style = MaterialTheme.typography.bodySmall,
                color = NsTheme.colors.status.warningText,
            )
        }
    }
}

/** Device status, which only MQTT sends, for a survey whose MQTT stream sends nothing else. */
@Composable
private fun DeviceStatusCell(modifier: Modifier) {
    val name = stringResource(R.string.survey_routing_device_status)
    val description = stringResource(R.string.survey_routing_row, name, stringResource(Destination.MQTT.labelRes))
    Column(
        modifier = modifier
            .heightIn(min = ROW_HEIGHT)
            .padding(vertical = NsTheme.spacing.xs)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun HeaderCell(column: RoutingColumn, modifier: Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = HEADER_HEIGHT)
            .fillMaxHeight()
            .clearAndSetSemantics {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = stringResource(column.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = NsTheme.spacing.xxs).padding(bottom = NsTheme.spacing.xs),
        )
    }
}

@Composable
private fun MarkCell(sent: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = ROW_HEIGHT)
            .fillMaxHeight()
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (sent) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(MARK_SIZE))
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_remove),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(MARK_SIZE),
            )
        }
    }
}

/**
 * The table's column widths.
 *
 * @property type   The record type column.
 * @property column One destination column.
 * @property marks  The visible part of the destination columns; they scroll when wider.
 */
private data class RoutingWidths(val type: Dp, val column: Dp, val marks: Dp)

/**
 * Each destination column is [column] wide; the type column gets at least [typeMin], up to
 * [TYPE_COLUMN_MAX_FRACTION] of [width], and then everything the destination columns leave, so the
 * table always spans the sheet.
 */
private fun routingWidths(width: Dp, columnCount: Int, column: Dp, typeMin: Dp): RoutingWidths {
    val marks = minOf(column * columnCount, (width - minOf(typeMin, width * TYPE_COLUMN_MAX_FRACTION)).coerceAtLeast(0.dp))
    return RoutingWidths(type = width - marks, column = column, marks = marks)
}

/**
 * The width of the widest single word in [texts] set in [style], at the current font scale (which
 * Android applies less than linearly to large text, so a width in sp can't stand in for it).
 */
@Composable
private fun widestWord(texts: List<String>, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(texts, style, density) {
        val widest = texts.flatMap { it.split(' ') }
            .maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
        with(density) { widest.toDp() }
    }
}

/** What the table can't show: the types that aren't selected, and how to record the rest. */
@Composable
private fun Footnote(table: RoutingTableUi) {
    val lines = listOfNotNull(
        stringResource(R.string.survey_routing_log_files_hint)
            .takeIf { table.notSentAnywhere.isNotEmpty() && RoutingColumn.LOG_FILES !in table.columns },
        table.unselected.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.survey_routing_not_selected, recordTypeNames(it)) },
    )
    if (lines.isNotEmpty()) {
        Text(
            text = lines.joinToString(" "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The column heading: the destination, or the community database it stands for. */
private val RoutingColumn.labelRes: Int
    get() = when (this) {
        RoutingColumn.OPENCELLID -> R.string.survey_dest_community_opencellid
        RoutingColumn.BEACONDB -> R.string.survey_dest_community_beacondb
        else -> destination.labelRes
    }

private val TYPE_COLUMN_WIDTH = 112.dp
private const val TYPE_COLUMN_MAX_FRACTION = 0.5f
private val COLUMN_WIDTH = 76.dp
private val HEADER_HEIGHT = 48.dp
private val ROW_HEIGHT = 48.dp
private val MARK_SIZE = 18.dp
