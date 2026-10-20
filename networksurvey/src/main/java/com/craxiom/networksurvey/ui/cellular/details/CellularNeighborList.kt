package com.craxiom.networksurvey.ui.cellular.details

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.ui.cellular.details.model.CellField
import com.craxiom.networksurvey.ui.cellular.details.model.NeighborRow
import com.craxiom.networksurvey.ui.cellular.details.model.NeighborSection
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsRssiColumn
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.common.signalBucketWord
import com.craxiom.networksurvey.ui.common.signalFillColor
import com.craxiom.networksurvey.ui.common.signalTextColor
import com.craxiom.networksurvey.ui.theme.NsTheme

/** Joins a neighbor's details ("EARFCN 66811 · Band 66"). */
private const val DETAIL_SEPARATOR = " · "

/** One neighbor section: "LTE neighbors" with its count, then a row per cell, strongest first. */
@Composable
fun CellularNeighborList(section: NeighborSection, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionHeader(
            title = stringResource(sectionTitle(section.protocol)),
            trailing = section.rows.size.toString(),
            topPadding = NsTheme.spacing.s,
        )
        NsRowGroup {
            section.rows.forEachIndexed { index, row ->
                if (index > 0) NsRowDivider()
                NeighborRowView(row)
            }
        }
    }
}

/** A neighbor: its code over its channel and quality on the left, its signal on the right; one TalkBack item. */
@Composable
private fun NeighborRowView(row: NeighborRow) {
    val title = labeled(row.title)
    val details = row.details.map { labeled(it) }
    val signal = fieldText(row.signal)
    val spoken = buildList {
        add(title)
        addAll(details)
        add("${stringResource(fieldLabel(row.signal.kind))} $signal")
        if (row.signal.value != null) signalBucketWord(row.signal.bucket)?.let { add(stringResource(it)) }
    }.joinToString(", ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = spoken }
            .heightIn(min = NsTheme.spacing.rowMinHeight)
            .padding(vertical = NsTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(DETAIL_SEPARATOR),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        NsRssiColumn(
            text = signal,
            textColor = if (row.signal.value != null) signalTextColor(row.signal.bucket) else MaterialTheme.colorScheme.onSurfaceVariant,
            fraction = row.signal.fraction ?: 0f,
            fillColor = signalFillColor(row.signal.bucket),
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** @return The label then the value with its unit, e.g. "PCI 41" or "RSRQ -13 dB". */
@Composable
private fun labeled(field: CellField): String =
    stringResource(R.string.cellular_field_value, stringResource(fieldLabel(field.kind)), fieldText(field))

@StringRes
private fun sectionTitle(protocol: CellularProtocol): Int = when (protocol) {
    CellularProtocol.NR -> R.string.card_title_neighbors_nr
    CellularProtocol.LTE -> R.string.card_title_neighbors_lte
    CellularProtocol.UMTS -> R.string.card_title_neighbors_umts
    CellularProtocol.GSM -> R.string.card_title_neighbors_gsm
    else -> R.string.card_title_neighbors
}
