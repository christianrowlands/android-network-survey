package com.craxiom.networksurvey.ui.map

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NetworkTagVariant
import com.craxiom.networksurvey.ui.common.NsNetworkTag
import com.craxiom.networksurvey.ui.common.NsSegmentedToggle
import com.craxiom.networksurvey.ui.common.ROW_ICON
import com.craxiom.networksurvey.ui.common.SegmentedOption
import com.craxiom.networksurvey.ui.common.SegmentedStyle
import com.craxiom.networksurvey.ui.common.signalTextColor
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.MeasurementFormatter

/** Joins the parts of a card line ("Mint · LTE Band 66 · -79 dBm"). */
private const val PART_SEPARATOR = " · "

/** The lines of the collapsed card; each may wrap once at large font sizes. */
private const val CARD_LINE_MAX_LINES = 2

/** The tag tests use to find the card. */
const val SERVING_CELL_CARD_TAG = "serving_cell_card"

/** One SIM the card can show. */
data class SimChoice(val subscriptionId: Int, val label: String)

/**
 * What the serving-cell card shows: the selected SIM's [summary] and every SIM that has a serving
 * cell, so the user can switch between them inside the expanded card.
 */
data class ServingCellCardState(
    val summary: ServingCellSummary,
    val sims: List<SimChoice>,
) {
    /** The label of the SIM shown, when there is more than one to choose from. */
    val simLabel: String? get() = sims.takeIf { it.size > 1 }?.firstOrNull { it.subscriptionId == summary.subscriptionId }?.label
}

/**
 * The serving cell at the top of the Map. Collapsed it reads as two lines, "operator · LTE Band 66
 * · -79 dBm [NEW]" and "eNB 100000 · Sector 3 · 400 m away · SIM 1"; expanded it adds the cell's
 * details and, with more than one SIM, the choice of SIM. The expanded part scrolls within
 * [maxExpandedHeight] so it never runs into the map's controls.
 */
@Composable
fun ServingCellCard(
    state: ServingCellCardState,
    onSelectSim: (Int) -> Unit,
    maxExpandedHeight: Dp,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val summary = state.summary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SERVING_CELL_CARD_TAG),
        shape = NsTheme.shapes.panel,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = NsTheme.spacing.xs,
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            CardHeader(state, expanded) { expanded = !expanded }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .heightIn(max = maxExpandedHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = NsTheme.spacing.panelPaddingHorizontal,
                            end = NsTheme.spacing.panelPaddingHorizontal,
                            bottom = NsTheme.spacing.m,
                        ),
                    verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
                ) {
                    MetricsGrid(summary.metrics)
                    if (state.sims.size > 1) {
                        NsSegmentedToggle(
                            options = state.sims.map { SegmentedOption(it.label) },
                            selectedIndex = state.sims.indexOfFirst { it.subscriptionId == summary.subscriptionId },
                            onSelected = { index -> onSelectSim(state.sims[index].subscriptionId) },
                            style = SegmentedStyle.TABS,
                        )
                    }
                }
            }
        }
    }
}

/** The two collapsed lines and the chevron; the whole row expands and collapses the card. */
@Composable
private fun CardHeader(state: ServingCellCardState, expanded: Boolean, onToggle: () -> Unit) {
    val summary = state.summary
    val expandedWord = stringResource(R.string.map_card_expanded)
    val collapsedWord = stringResource(R.string.map_card_collapsed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(if (expanded) R.string.map_card_hide_details else R.string.map_card_show_details),
                onClick = onToggle,
            )
            .semantics(mergeDescendants = true) { stateDescription = if (expanded) expandedWord else collapsedWord }
            .heightIn(min = NsTheme.spacing.touchTarget)
            .padding(horizontal = NsTheme.spacing.panelPaddingHorizontal, vertical = NsTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_cell_tower),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ROW_ICON),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
                Text(
                    text = titleLine(summary),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = CARD_LINE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (summary.isNew) {
                    val newDescription = stringResource(R.string.map_card_new_description)
                    NsNetworkTag(
                        text = stringResource(R.string.map_card_new),
                        variant = NetworkTagVariant.New,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = newDescription },
                    )
                }
            }
            detailLine(summary, state.simLabel)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = CARD_LINE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Mint · LTE Band 66 · -79 dBm", with the signal colored by its strength. */
@Composable
private fun titleLine(summary: ServingCellSummary) = buildAnnotatedString {
    val technology = buildList {
        add(summary.protocol.name)
        summary.lteBand?.let { add(stringResource(R.string.map_card_band_lte, it)) }
        summary.nrBands?.let { add(it) }
    }.joinToString(" ")
    val leading = listOfNotNull(summary.operator ?: summary.plmn, technology).joinToString(PART_SEPARATOR)
    append(leading)
    summary.signalDbm?.let { dBm ->
        append(PART_SEPARATOR)
        withStyle(SpanStyle(color = signalTextColor(summary.signalBucket))) {
            append(stringResource(R.string.dbm_value, dBm.toString()))
        }
    }
}

/** "eNB 100000 · Sector 3 · 400 m away · SIM 1", or null when there is nothing to say. */
@Composable
private fun detailLine(summary: ServingCellSummary, simLabel: String?): String? {
    val context = LocalContext.current
    val parts = listOfNotNull(
        summary.enbSector?.let { (enb, sector) -> stringResource(R.string.survey_point_enb_sector, enb, sector) }
            ?: summary.identity?.let { "${it.label} ${it.value}" },
        summary.distanceMeters?.let { stringResource(R.string.map_card_distance, MeasurementFormatter.formatDistance(context, it)) },
        simLabel,
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(PART_SEPARATOR)
}

/** The expanded card's details, as label-over-value pairs that wrap to the card's width. */
@Composable
private fun MetricsGrid(metrics: List<CellMetric>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
    ) {
        metrics.forEach { metric ->
            Column(modifier = Modifier.widthIn(min = METRIC_MIN_WIDTH).semantics(mergeDescendants = true) {}) {
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = metric.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private val METRIC_MIN_WIDTH = NsTheme.spacing.touchTarget + NsTheme.spacing.xl

