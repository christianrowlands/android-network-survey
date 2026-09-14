package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointColorMode
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointFeatures
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointKind
import com.craxiom.networksurvey.util.SignalBuckets
import java.text.NumberFormat

/*
 * The small chrome around the surveyed places layer: the user-facing names of its modes, kinds,
 * buckets, and technologies, the on-map pill, and the Layers sheet row.
 */

/** The user-facing name of a color mode. */
@Composable
fun SurveyedPointColorMode.label(): String = stringResource(
    when (this) {
        SurveyedPointColorMode.SIGNAL -> R.string.surveyed_places_mode_signal
        SurveyedPointColorMode.TECHNOLOGY -> R.string.surveyed_places_mode_technology
        SurveyedPointColorMode.PROVIDER -> R.string.surveyed_places_mode_provider
        SurveyedPointColorMode.SENT -> R.string.surveyed_places_mode_sent
        SurveyedPointColorMode.CELL -> R.string.surveyed_places_mode_cell
        SurveyedPointColorMode.AREA -> R.string.surveyed_places_mode_area
    }
)

/** The user-facing name of a kind. */
@Composable
fun SurveyedPointKind.label(): String = stringResource(
    when (this) {
        SurveyedPointKind.CELLULAR -> R.string.surveyed_places_kind_cellular
        SurveyedPointKind.WIFI -> R.string.surveyed_places_kind_wifi
        SurveyedPointKind.BLUETOOTH -> R.string.surveyed_places_kind_bluetooth
    }
)

/** The user-facing name of a signal bucket. */
@Composable
fun bucketLabel(bucket: Int): String = stringResource(
    when (bucket) {
        SignalBuckets.STRONG -> R.string.surveyed_places_signal_strong
        SignalBuckets.GOOD -> R.string.surveyed_places_signal_good
        SignalBuckets.FAIR -> R.string.surveyed_places_signal_fair
        SignalBuckets.WEAK -> R.string.surveyed_places_signal_weak
        SignalBuckets.VERY_WEAK -> R.string.surveyed_places_signal_very_weak
        else -> R.string.surveyed_places_unknown_short
    }
)

/** The user-facing name of a technology string from [SurveyedPointFeatures]. */
@Composable
fun techLabel(tech: String): String = when (tech) {
    SurveyedPointFeatures.TECH_NR -> stringResource(R.string.surveyed_places_tech_nr)
    SurveyedPointFeatures.TECH_LTE -> stringResource(R.string.surveyed_places_tech_lte)
    SurveyedPointFeatures.TECH_UMTS -> stringResource(R.string.surveyed_places_tech_umts)
    SurveyedPointFeatures.TECH_GSM -> stringResource(R.string.surveyed_places_tech_gsm)
    SurveyedPointFeatures.TECH_CDMA -> SurveyedPointFeatures.TECH_CDMA
    else -> stringResource(R.string.surveyed_places_unknown_short)
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
                contentDescription = stringResource(R.string.surveyed_places_key_description),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.surveyed_places_summary, mode.label(), kind.label()),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * The Layers sheet row for the surveyed places layer: checkbox, live count, and a one-line
 * summary of the current encoding with a "Change" button that opens the options sheet.
 */
@Composable
fun SurveyedPlacesLayerSummary(
    checked: Boolean,
    count: Int,
    mode: SurveyedPointColorMode,
    kind: SurveyedPointKind,
    onCheckedChange: (Boolean) -> Unit,
    onChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = checked, onClick = { onCheckedChange(!checked) })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Text(
                text = stringResource(R.string.surveyed_places_layer_title),
                style = MaterialTheme.typography.bodyMedium
            )
            if (count > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.surveyed_places_count,
                        count,
                        NumberFormat.getInstance().format(count)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (checked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                R.string.surveyed_places_summary,
                                mode.label(),
                                kind.label()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onChange) { Text(stringResource(R.string.surveyed_places_change)) }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.surveyed_places_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

