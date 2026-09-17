package com.craxiom.networksurvey.ui.cellular

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointSourceFilter
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointTimeFilter
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointUploadFilter
import com.craxiom.networksurvey.ui.cellular.towermap.MILLIS_PER_SECOND
import com.craxiom.networksurvey.ui.cellular.towermap.getRelativeTimeString

/**
 * The three filters that narrow the survey points layer, as one value so the sheet, the on-map
 * chips, and the controller all speak about the same thing.
 */
data class SurveyedPointFilterSelection(
    val time: SurveyedPointTimeFilter = SurveyedPointTimeFilter.ANY,
    val source: SurveyedPointSourceFilter = SurveyedPointSourceFilter.BOTH,
    val upload: SurveyedPointUploadFilter = SurveyedPointUploadFilter.ANY,
) {
    /** True when anything is hiding points, so the map can say so rather than just going blank. */
    val isActive: Boolean
        get() = time != SurveyedPointTimeFilter.ANY ||
                source != SurveyedPointSourceFilter.BOTH ||
                upload != SurveyedPointUploadFilter.ANY

    /** This selection with every filter back at its default. */
    fun cleared(): SurveyedPointFilterSelection = SurveyedPointFilterSelection()
}

/**
 * The survey points half of the combined filters sheet: when the points were surveyed, which
 * upload destination they were collected for, and whether they have been sent yet.
 *
 * @param latestSurveyAvailable whether any NS Analytics survey has written points; the
 * "Latest NS Analytics survey" option is meaningless without one and is hidden.
 * @param latestSurveyStart when that survey first wrote a point, shown as the option's subtext, or
 * null to leave the subtext off.
 * @param showSourceFilter whether more than one upload pipeline has written points.
 * @param showLeadingSpacer whether the tower filters are above this section and need separating.
 */
@Composable
internal fun SurveyedPointFilterSection(
    selection: SurveyedPointFilterSelection,
    latestSurveyAvailable: Boolean,
    latestSurveyStart: Long?,
    showSourceFilter: Boolean,
    showLeadingSpacer: Boolean,
    onChange: (SurveyedPointFilterSelection) -> Unit,
) {
    if (showLeadingSpacer) Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.survey_points_options_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    GroupLabel(R.string.survey_points_filter_when, top = 0.dp)
    SurveyedPointTimeFilter.entries
        .filter { it != SurveyedPointTimeFilter.LATEST_SURVEY || latestSurveyAvailable }
        .forEach { option ->
            FilterRadioRow(
                label = stringResource(surveyedTimeFilterLabel(option)),
                selected = selection.time == option,
                onClick = { onChange(selection.copy(time = option)) },
                supportingText = latestSurveyStartedLabel(option, latestSurveyStart)
            )
        }

    if (showSourceFilter) {
        GroupLabel(R.string.survey_points_filter_collected)
        SurveyedPointSourceFilter.entries.forEach { option ->
            FilterRadioRow(
                label = stringResource(surveyedSourceFilterLabel(option)),
                selected = selection.source == option,
                onClick = { onChange(selection.copy(source = option)) }
            )
        }
    }

    GroupLabel(R.string.survey_points_filter_sent_status)
    SurveyedPointUploadFilter.entries.forEach { option ->
        FilterRadioRow(
            label = stringResource(surveyedUploadFilterLabel(option)),
            selected = selection.upload == option,
            onClick = { onChange(selection.copy(upload = option)) }
        )
    }
}

/**
 * "Started 2 hours ago" under the latest survey option, so it is clear which survey is meant and
 * that it is the most recent one rather than whatever is running now. Relative rather than an
 * absolute stamp to match the wording of the options around it.
 */
@Composable
private fun latestSurveyStartedLabel(
    option: SurveyedPointTimeFilter,
    latestSurveyStart: Long?,
): String? {
    if (option != SurveyedPointTimeFilter.LATEST_SURVEY || latestSurveyStart == null) return null
    // getRelativeTimeString returns standalone, capitalized phrases ("Just now", "Yesterday"),
    // which read wrong once embedded mid-sentence.
    val relative = getRelativeTimeString(latestSurveyStart / MILLIS_PER_SECOND)
        .replaceFirstChar { it.lowercase() }
    return stringResource(R.string.survey_points_filter_latest_survey_started, relative)
}

@Composable
private fun GroupLabel(textRes: Int, top: Dp = 8.dp) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = top, bottom = 4.dp)
    )
}
