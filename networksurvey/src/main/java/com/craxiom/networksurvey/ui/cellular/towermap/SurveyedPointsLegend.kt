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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.DbUploadStore
import com.craxiom.networksurvey.logging.db.SurveyedPointGate
import com.craxiom.networksurvey.logging.db.SurveyedPointStore
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointColorMode
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointFeatures
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointKind
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointUploadFilter
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointsData
import com.craxiom.networksurvey.ui.common.NsSegmentedToggle
import com.craxiom.networksurvey.ui.common.SegmentedOption
import com.craxiom.networksurvey.ui.common.dialogs.NsMessageDialog
import com.craxiom.networksurvey.util.SignalBuckets
import java.text.NumberFormat

/** The modal host for [SurveyedPointsOptions], opened from the pill or the Layers sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyedPointsOptionsSheet(
    mode: SurveyedPointColorMode,
    kind: SurveyedPointKind,
    availableKinds: Set<SurveyedPointKind>,
    data: SurveyedPointsData?,
    uploadFilter: SurveyedPointUploadFilter,
    onModeChange: (SurveyedPointColorMode) -> Unit,
    onKindChange: (SurveyedPointKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAbout by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.survey_points_options_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showAbout = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.survey_points_about_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            SurveyedPointsOptions(
                mode, kind, availableKinds, data, uploadFilter, onModeChange, onKindChange
            )
        }
    }

    if (showAbout) {
        SurveyPointsAboutDialog(onDismiss = { showAbout = false })
    }
}

/**
 * Explains where the layer's data comes from and what it does not cover, so the dots are not read
 * as a complete record of everywhere the device has been. The spacing, accuracy, and cap numbers
 * are pulled from the constants that enforce them so the text cannot drift from the behavior.
 *
 * Reachable from both the options sheet and the Layers sheet row, because the options sheet is
 * gated on the layer having points and a user with none is exactly who needs this.
 */
@Composable
fun SurveyPointsAboutDialog(onDismiss: () -> Unit) {
    val message = listOf(
        stringResource(R.string.survey_points_about_sources),
        stringResource(R.string.survey_points_about_filters),
        stringResource(
            R.string.survey_points_about_spacing,
            SurveyedPointGate.WALKING_THRESHOLD_METERS,
            DbUploadStore.DISTANCE_MOVED_THRESHOLD_METERS,
            DbUploadStore.ACCURACY_THRESHOLD_METERS,
        ),
        stringResource(
            R.string.survey_points_about_storage,
            NumberFormat.getInstance().format(SurveyedPointStore.MAX_ROWS)
        ),
        stringResource(R.string.survey_points_about_reading),
    ).joinToString("\n\n")

    NsMessageDialog(
        title = stringResource(R.string.survey_points_about_title),
        message = message,
        onDismiss = onDismiss,
        icon = Icons.Outlined.Info,
    )
}

/** Show (kind) toggle, Color by radios, and the legend for the selected mode. */
@Composable
fun SurveyedPointsOptions(
    mode: SurveyedPointColorMode,
    kind: SurveyedPointKind,
    availableKinds: Set<SurveyedPointKind>,
    data: SurveyedPointsData?,
    uploadFilter: SurveyedPointUploadFilter,
    onModeChange: (SurveyedPointColorMode) -> Unit,
    onKindChange: (SurveyedPointKind) -> Unit,
) {
    Text(
        text = stringResource(R.string.survey_points_show),
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
        text = stringResource(R.string.survey_points_color_by),
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
        text = stringResource(R.string.survey_points_advanced),
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
    SurveyedPointsLegend(mode = mode, kind = kind, data = data, uploadFilter = uploadFilter)
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
    data: SurveyedPointsData?,
    uploadFilter: SurveyedPointUploadFilter,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (data?.coarse == true) {
            Note(stringResource(R.string.survey_points_coarse_note))
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
                Note(stringResource(R.string.survey_points_colors_repeat))
                UnknownRow()
            }

            SurveyedPointColorMode.AREA -> {
                data?.legend?.forEach { entry ->
                    val area = entry.key.substringAfterLast('-')
                    val plmn = entry.key.substringBeforeLast('-')
                    LegendRow(
                        SurveyedPointPalette.hashed(SurveyedPointFeatures.colorIndex(entry.key)),
                        stringResource(R.string.survey_points_area_label, area),
                        entry.label ?: plmn
                    )
                }
                MoreRow(data?.legendMore ?: 0)
                Note(stringResource(R.string.survey_points_colors_repeat))
                UnknownRow()
            }

            SurveyedPointColorMode.SENT -> {
                // A Sent status filter makes one of these impossible; do not promise a color
                // that cannot appear on the map.
                if (uploadFilter != SurveyedPointUploadFilter.SENT) {
                    LegendRow(
                        SurveyedPointPalette.PENDING,
                        stringResource(R.string.survey_points_legend_pending)
                    )
                }
                if (uploadFilter != SurveyedPointUploadFilter.NOT_SENT) {
                    LegendRow(
                        SurveyedPointPalette.SENT,
                        stringResource(R.string.survey_points_legend_uploaded)
                    )
                }
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
        stringResource(R.string.survey_points_signal_above, t[0])
    )
    LegendRow(
        SurveyedPointPalette.bucket(SignalBuckets.GOOD),
        bucketLabel(SignalBuckets.GOOD),
        stringResource(R.string.survey_points_signal_between, t[0], t[1])
    )
    LegendRow(
        SurveyedPointPalette.bucket(SignalBuckets.FAIR),
        bucketLabel(SignalBuckets.FAIR),
        stringResource(R.string.survey_points_signal_between, t[1], t[2])
    )
    LegendRow(
        SurveyedPointPalette.bucket(SignalBuckets.WEAK),
        bucketLabel(SignalBuckets.WEAK),
        stringResource(R.string.survey_points_signal_between, t[2], t[3])
    )
    LegendRow(
        SurveyedPointPalette.bucket(SignalBuckets.VERY_WEAK),
        bucketLabel(SignalBuckets.VERY_WEAK),
        stringResource(R.string.survey_points_signal_below, t[3])
    )
    UnknownRow()
    if (cellular) Note(stringResource(R.string.survey_points_thresholds_note))
}

@Composable
private fun UnknownRow() =
    LegendRow(SurveyedPointPalette.UNKNOWN, stringResource(R.string.survey_points_unknown))

@Composable
private fun MoreRow(more: Int) {
    if (more > 0) Note(pluralStringResource(R.plurals.survey_points_and_more, more, more))
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
