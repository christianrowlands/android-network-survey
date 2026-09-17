package com.craxiom.networksurvey.ui.cellular

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointSourceFilter
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointTimeFilter
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointUploadFilter

/**
 * The active survey point filters, as dismissible chips under the on-map pill.
 *
 * Without this the layer can go blank with nothing on screen explaining why: the count in the
 * Layers sheet is the stored total rather than the filtered one, the pill names only the color
 * mode, and the filters reset when the map screen is recreated, so points vanish and later return.
 * Renders nothing at all when no filter is narrowing the layer, so the common case stays clean.
 *
 * @param noneInView true when the filters are on and the current view has no matching points.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SurveyedPointFilterChips(
    selection: SurveyedPointFilterSelection,
    noneInView: Boolean,
    onChange: (SurveyedPointFilterSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!selection.isActive) return

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (selection.time != SurveyedPointTimeFilter.ANY) {
                FilterChip(
                    label = stringResource(surveyedTimeFilterLabel(selection.time)),
                    onRemove = { onChange(selection.copy(time = SurveyedPointTimeFilter.ANY)) }
                )
            }
            if (selection.source != SurveyedPointSourceFilter.BOTH) {
                FilterChip(
                    label = stringResource(surveyedSourceFilterLabel(selection.source)),
                    onRemove = { onChange(selection.copy(source = SurveyedPointSourceFilter.BOTH)) }
                )
            }
            if (selection.upload != SurveyedPointUploadFilter.ANY) {
                FilterChip(
                    label = stringResource(surveyedUploadFilterLabel(selection.upload)),
                    onRemove = { onChange(selection.copy(upload = SurveyedPointUploadFilter.ANY)) }
                )
            }
        }

        if (noneInView) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 2.dp,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.survey_points_filter_none_in_view),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onChange(selection.cleared()) }) {
                        Text(stringResource(R.string.survey_points_filter_clear_all))
                    }
                }
            }
        }
    }
}

/** One filter, with a close affordance that clears just that filter. */
@Composable
private fun FilterChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.survey_points_filter_remove, label),
                modifier = Modifier.size(16.dp),
            )
        },
    )
}
