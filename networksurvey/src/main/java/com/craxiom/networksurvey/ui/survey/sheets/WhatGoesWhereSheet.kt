package com.craxiom.networksurvey.ui.survey.sheets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatGoesWhereSheet(table: RoutingTableUi, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
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
}

@Composable
private fun RoutingTable(table: RoutingTableUi) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.width(TYPE_COLUMN_WIDTH)) {
            Box(modifier = Modifier.height(HEADER_HEIGHT))
            table.rows.forEach { row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TypeCell(row, table.columns)
            }
            if (table.mqttDeviceStatus) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DeviceStatusCell()
            }
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            table.columns.forEach { column ->
                Column(modifier = Modifier.width(COLUMN_WIDTH)) {
                    HeaderCell(column)
                    table.rows.forEach { row ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MarkCell(column in row.sentTo)
                    }
                    if (table.mqttDeviceStatus) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MarkCell(column == RoutingColumn.MQTT)
                    }
                }
            }
        }
    }
}

/** The record type, read by TalkBack together with where it goes, so the marks can stay silent. */
@Composable
private fun TypeCell(row: RoutingRowUi, columns: List<RoutingColumn>) {
    val name = stringResource(row.type.labelRes)
    val sentTo = columns.filter { it in row.sentTo }.map { stringResource(it.labelRes) }
    val description = if (row.notSentAnywhere) {
        stringResource(R.string.survey_routing_row_not_sent, name)
    } else {
        stringResource(R.string.survey_routing_row, name, joinNames(sentTo))
    }
    Column(
        modifier = Modifier
            .height(ROW_HEIGHT)
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
private fun DeviceStatusCell() {
    val name = stringResource(R.string.survey_routing_device_status)
    val description = stringResource(R.string.survey_routing_row, name, stringResource(Destination.MQTT.labelRes))
    Column(
        modifier = Modifier
            .height(ROW_HEIGHT)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun HeaderCell(column: RoutingColumn) {
    Box(
        modifier = Modifier
            .height(HEADER_HEIGHT)
            .fillMaxWidth()
            .clearAndSetSemantics {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = stringResource(column.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = NsTheme.spacing.xs),
        )
    }
}

@Composable
private fun MarkCell(sent: Boolean) {
    Box(
        modifier = Modifier
            .height(ROW_HEIGHT)
            .fillMaxWidth()
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
private val COLUMN_WIDTH = 76.dp
private val HEADER_HEIGHT = 48.dp
private val ROW_HEIGHT = 48.dp
private val MARK_SIZE = 18.dp
