package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointColorMode
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointFeatures
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointKind
import com.craxiom.networksurvey.ui.common.NsIconAction
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.SignalBuckets
import java.text.NumberFormat

/*
 * The small chrome around the survey points layer: the user-facing names of its modes, kinds,
 * buckets, and technologies, the on-map pill, and the Layers sheet row.
 */

/** The user-facing name of a color mode. */
@Composable
fun SurveyedPointColorMode.label(): String = stringResource(
    when (this) {
        SurveyedPointColorMode.SIGNAL -> R.string.survey_points_mode_signal
        SurveyedPointColorMode.TECHNOLOGY -> R.string.survey_points_mode_technology
        SurveyedPointColorMode.PROVIDER -> R.string.survey_points_mode_provider
        SurveyedPointColorMode.SENT -> R.string.survey_points_mode_sent
        SurveyedPointColorMode.CELL -> R.string.survey_points_mode_cell
        SurveyedPointColorMode.AREA -> R.string.survey_points_mode_area
    }
)

/** The user-facing name of a kind. */
@Composable
fun SurveyedPointKind.label(): String = stringResource(
    when (this) {
        SurveyedPointKind.CELLULAR -> R.string.survey_points_kind_cellular
        SurveyedPointKind.WIFI -> R.string.survey_points_kind_wifi
        SurveyedPointKind.BLUETOOTH -> R.string.survey_points_kind_bluetooth
    }
)

/** The user-facing name of a signal bucket. */
@Composable
fun bucketLabel(bucket: Int): String = stringResource(
    when (bucket) {
        SignalBuckets.STRONG -> R.string.survey_points_signal_strong
        SignalBuckets.GOOD -> R.string.survey_points_signal_good
        SignalBuckets.FAIR -> R.string.survey_points_signal_fair
        SignalBuckets.WEAK -> R.string.survey_points_signal_weak
        SignalBuckets.VERY_WEAK -> R.string.survey_points_signal_very_weak
        else -> R.string.survey_points_unknown_short
    }
)

/** The user-facing name of a technology string from [SurveyedPointFeatures]. */
@Composable
fun techLabel(tech: String): String = when (tech) {
    SurveyedPointFeatures.TECH_NR -> stringResource(R.string.survey_points_tech_nr)
    SurveyedPointFeatures.TECH_LTE -> stringResource(R.string.survey_points_tech_lte)
    SurveyedPointFeatures.TECH_UMTS -> stringResource(R.string.survey_points_tech_umts)
    SurveyedPointFeatures.TECH_GSM -> stringResource(R.string.survey_points_tech_gsm)
    SurveyedPointFeatures.TECH_CDMA -> SurveyedPointFeatures.TECH_CDMA
    else -> stringResource(R.string.survey_points_unknown_short)
}

/**
 * The on-map pill naming the current encoding, e.g. "Signal strength · Cellular". Tapping it
 * opens the options sheet, so the map never needs a permanent legend card.
 */
@Composable
fun SurveyedPointsPill(
    mode: SurveyedPointColorMode,
    kind: SurveyedPointKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_layers),
                contentDescription = stringResource(R.string.survey_points_key_description),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.survey_points_summary, mode.label(), kind.label()),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * The Layers sheet row for the survey points layer: a switch row with the live count, the About
 * button and, when on, a one-line summary of the current encoding with a "Change" button that
 * opens the options sheet. Sits in the sheet's overlay row group.
 */
@Composable
fun SurveyPointsLayerSummary(
    checked: Boolean,
    count: Int,
    mode: SurveyedPointColorMode,
    kind: SurveyedPointKind,
    onCheckedChange: (Boolean) -> Unit,
    onChange: () -> Unit,
    onAbout: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = NsTheme.spacing.rowMinHeight)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = NsTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.survey_points_layer_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (count > 0) {
                        pluralStringResource(R.plurals.survey_points_count, count, NumberFormat.getInstance().format(count))
                    } else {
                        stringResource(R.string.survey_points_empty)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NsIconAction(R.drawable.ic_info, stringResource(R.string.survey_points_about_title), onAbout)
            // The row carries the semantics, so the switch itself is not a second target
            Switch(checked = checked, onCheckedChange = null)
        }
        if (checked && count > 0) {
            Row(
                modifier = Modifier.padding(bottom = NsTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.survey_points_summary, mode.label(), kind.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                NsTextAction(stringResource(R.string.survey_points_change), onChange)
            }
        }
    }
}
