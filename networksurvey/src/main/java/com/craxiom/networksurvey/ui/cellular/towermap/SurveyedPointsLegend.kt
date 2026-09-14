package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointColorMode
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointFeatures
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointKind
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointsData
import com.craxiom.networksurvey.ui.common.NsSegmentedToggle
import com.craxiom.networksurvey.ui.common.SegmentedOption
import com.craxiom.networksurvey.util.SignalBuckets

/** The modal host for [SurveyedPointsOptions], opened from the pill or the Layers sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyedPointsOptionsSheet(
    mode: SurveyedPointColorMode,
    kind: SurveyedPointKind,
    availableKinds: Set<SurveyedPointKind>,
    data: SurveyedPointsData?,
    onModeChange: (SurveyedPointColorMode) -> Unit,
    onKindChange: (SurveyedPointKind) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.surveyed_places_options_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            SurveyedPointsOptions(mode, kind, availableKinds, data, onModeChange, onKindChange)
        }
    }
}

/** Show (kind) toggle, Color by radios, and the legend for the selected mode. */
@Composable
fun SurveyedPointsOptions(
    mode: SurveyedPointColorMode,
    kind: SurveyedPointKind,
    availableKinds: Set<SurveyedPointKind>,
    data: SurveyedPointsData?,
    onModeChange: (SurveyedPointColorMode) -> Unit,
    onKindChange: (SurveyedPointKind) -> Unit,
) {
    Text(
        text = stringResource(R.string.surveyed_places_show),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    // Cellular is always offered; other kinds only once they have something to show
    val kinds =
        SurveyedPointKind.entries.filter { it == SurveyedPointKind.CELLULAR || it == kind || it in availableKinds }
    NsSegmentedToggle(
        options = kinds.map { SegmentedOption(it.label()) },
        selectedIndex = kinds.indexOf(kind).coerceAtLeast(0),
        onSelected = { index -> onKindChange(kinds[index]) },
    )

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.surveyed_places_color_by),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    val cellularSelected = kind == SurveyedPointKind.CELLULAR
    SurveyedPointColorMode.entries.filterNot { it.advanced }.forEach { option ->
        ModeRadio(
            option,
            selected = mode == option,
            enabled = cellularSelected || !option.cellularOnly,
            onSelect = onModeChange
        )
    }
    Text(
        text = stringResource(R.string.surveyed_places_advanced),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
    SurveyedPointColorMode.entries.filter { it.advanced }.forEach { option ->
        ModeRadio(
            option,
            selected = mode == option,
            enabled = cellularSelected || !option.cellularOnly,
            onSelect = onModeChange
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    SurveyedPointsLegend(mode = mode, kind = kind, data = data)
}

@Composable
private fun ModeRadio(
    option: SurveyedPointColorMode,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (SurveyedPointColorMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = { onSelect(option) })
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, enabled = enabled, onClick = { onSelect(option) })
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = option.label(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = 0.6f
            )
        )
    }
}

/** The legend for one color mode; data-driven modes list the identities currently in view. */
@Composable
fun SurveyedPointsLegend(
    mode: SurveyedPointColorMode,
    kind: SurveyedPointKind,
    data: SurveyedPointsData?
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (data?.coarse == true) {
            Note(stringResource(R.string.surveyed_places_coarse_note))
        }
        when (mode) {
            SurveyedPointColorMode.SIGNAL -> SignalLegend(kind)

            SurveyedPointColorMode.TECHNOLOGY -> {
                SurveyedPointPalette.TECHNOLOGIES.forEach { tech ->
                    LegendRow(SurveyedPointPalette.tech(tech), techLabel(tech))
                }
                UnknownRow()
            }

            SurveyedPointColorMode.PROVIDER -> {
                data?.legend?.forEach { entry ->
                    LegendRow(
                        SurveyedPointPalette.plmn(entry.key),
                        entry.label ?: entry.key,
                        if (entry.label != null) entry.key else null
                    )
                }
                MoreRow(data?.legendMore ?: 0)
                UnknownRow()
            }

            SurveyedPointColorMode.CELL -> {
                data?.legend?.forEach { entry ->
                    LegendRow(
                        SurveyedPointPalette.hashed(SurveyedPointFeatures.colorIndex(entry.key)),
                        entry.key,
                        entry.label
                    )
                }
                MoreRow(data?.legendMore ?: 0)
                Note(stringResource(R.string.surveyed_places_colors_repeat))
                UnknownRow()
            }

            SurveyedPointColorMode.AREA -> {
                data?.legend?.forEach { entry ->
                    val area = entry.key.substringAfterLast('-')
                    val plmn = entry.key.substringBeforeLast('-')
                    LegendRow(
                        SurveyedPointPalette.hashed(SurveyedPointFeatures.colorIndex(entry.key)),
                        stringResource(R.string.surveyed_places_area_label, area),
                        entry.label ?: plmn
                    )
                }
                MoreRow(data?.legendMore ?: 0)
                Note(stringResource(R.string.surveyed_places_colors_repeat))
                UnknownRow()
            }

            SurveyedPointColorMode.SENT -> {
                LegendRow(
                    SurveyedPointPalette.PENDING,
                    stringResource(R.string.surveyed_places_legend_pending)
                )
                LegendRow(
                    SurveyedPointPalette.SENT,
                    stringResource(R.string.surveyed_places_legend_uploaded)
                )
            }
        }
    }
}

@Composable
private fun SignalLegend(kind: SurveyedPointKind) {
    val cellular = kind == SurveyedPointKind.CELLULAR
    val t =
        if (cellular) SignalBuckets.cellularThresholds(SurveyedPointEntity.PROTOCOL_LTE)!! else SignalBuckets.rssiThresholds()
    LegendRow(
        SurveyedPointPalette.bucket(SignalBuckets.STRONG),
        bucketLabel(SignalBuckets.STRONG),
        stringResource(R.string.surveyed_places_signal_above, t[0])
    )
    LegendRow(
        SurveyedPointPalette.bucket(SignalBuckets.GOOD),
        bucketLabel(SignalBuckets.GOOD),
        stringResource(R.string.surveyed_places_signal_between, t[0], t[1])
    )
    LegendRow(
        SurveyedPointPalette.bucket(SignalBuckets.FAIR),
        bucketLabel(SignalBuckets.FAIR),
        stringResource(R.string.surveyed_places_signal_between, t[1], t[2])
    )
    LegendRow(
        SurveyedPointPalette.bucket(SignalBuckets.WEAK),
        bucketLabel(SignalBuckets.WEAK),
        stringResource(R.string.surveyed_places_signal_between, t[2], t[3])
    )
    LegendRow(
        SurveyedPointPalette.bucket(SignalBuckets.VERY_WEAK),
        bucketLabel(SignalBuckets.VERY_WEAK),
        stringResource(R.string.surveyed_places_signal_below, t[3])
    )
    UnknownRow()
    if (cellular) Note(stringResource(R.string.surveyed_places_thresholds_note))
}

@Composable
private fun UnknownRow() =
    LegendRow(SurveyedPointPalette.UNKNOWN, stringResource(R.string.surveyed_places_unknown))

@Composable
private fun MoreRow(more: Int) {
    if (more > 0) Note(pluralStringResource(R.plurals.surveyed_places_and_more, more, more))
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LegendRow(argb: Int, text: String, secondary: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            modifier = Modifier
                .size(12.dp)
                .background(Color(argb), CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall)
        if (secondary != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
