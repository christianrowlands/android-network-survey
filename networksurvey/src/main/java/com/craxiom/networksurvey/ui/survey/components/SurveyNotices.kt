package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.survey.model.LastSessionUi
import com.craxiom.networksurvey.ui.survey.model.LocationStatus
import com.craxiom.networksurvey.ui.survey.model.OrgLineUi
import com.craxiom.networksurvey.ui.survey.model.SessionLength
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.common.InlineNotice
import com.craxiom.networksurvey.ui.common.countText
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.common.SMALL_ICON

/**
 * The slim line at the top of the Survey screen: whether the phone has a location fix, and while
 * Ready, how the last survey went and its Mission ID, which can still be copied. It never blocks
 * Start; a survey without a fix still records.
 */
@Composable
fun LocationLine(
    status: LocationStatus,
    lastSession: LastSessionUi?,
    onTurnOnLocation: () -> Unit,
    onAllowLocation: () -> Unit,
    modifier: Modifier = Modifier,
    onCopyMissionId: (String) -> Unit = {},
) {
    Column(modifier = modifier) {
        LocationStatusLine(status, onTurnOnLocation, onAllowLocation)
        // Its own line: next to the location text it wraps both on a phone-width screen.
        if (lastSession != null) {
            Text(
                text = lastSessionText(lastSession),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = SMALL_ICON + NsTheme.spacing.s),
            )
            lastSession.missionId?.let { MissionIdRow(it, onCopyMissionId) }
        }
    }
}

@Composable
private fun LocationStatusLine(status: LocationStatus, onTurnOnLocation: () -> Unit, onAllowLocation: () -> Unit) {
    val warning = NsTheme.colors.status.warningText
    when (status) {
        is LocationStatus.Fixed -> InlineNotice(
            text = stringResource(R.string.survey_location_fixed, status.accuracyMeters),
            icon = R.drawable.ic_my_location,
            iconTint = NsTheme.colors.status.success.content,
        )

        LocationStatus.Finding -> InlineNotice(
            text = stringResource(R.string.survey_location_finding),
            icon = R.drawable.ic_location_searching,
        )

        LocationStatus.ProviderOff -> InlineNotice(
            text = stringResource(R.string.survey_location_off),
            icon = R.drawable.ic_location_off,
            color = warning,
            emphasized = true,
        ) { NsTextAction(stringResource(R.string.survey_location_turn_on), onTurnOnLocation) }

        LocationStatus.PermissionNeeded -> InlineNotice(
            text = stringResource(R.string.survey_location_permission),
            icon = R.drawable.ic_location_off,
            color = warning,
            emphasized = true,
        ) { NsTextAction(stringResource(R.string.survey_location_allow), onAllowLocation) }
    }
}

@Composable
private fun lastSessionText(last: LastSessionUi): String {
    val length = when (val ran = last.length) {
        is SessionLength.Seconds -> pluralStringResource(R.plurals.survey_seconds_short, ran.seconds, ran.seconds)
        is SessionLength.Minutes -> pluralStringResource(R.plurals.survey_minutes_short, ran.minutes, ran.minutes)
    }
    return stringResource(R.string.survey_last_session, length, recordsText(last.records))
}

/**
 * "3,118 records", with the count grouped for the phone's locale, or "12 saved for upload" when
 * [savedForUpload] is set.
 */
@Composable
fun recordsText(records: Long, savedForUpload: Boolean = false): String = countText(
    if (savedForUpload) R.plurals.survey_saved_for_upload_count else R.plurals.survey_records_count,
    records,
)

/** What the organization is doing to this survey, with the one action that applies. */
@Composable
fun OrgStatusLine(
    line: OrgLineUi,
    onReview: () -> Unit,
    onRestore: () -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (line) {
        OrgLineUi.UpdatedWhileOverridden -> InlineNotice(
            text = stringResource(R.string.survey_org_updated),
            icon = R.drawable.ic_domain,
            color = NsTheme.colors.status.warningText,
            emphasized = true,
            modifier = modifier,
        ) { NsTextAction(stringResource(R.string.survey_org_review), onReview) }

        OrgLineUi.Overridden -> InlineNotice(
            text = stringResource(R.string.survey_org_overridden),
            icon = R.drawable.ic_domain,
            modifier = modifier,
        ) { NsTextAction(stringResource(R.string.survey_org_restore), onRestore) }

        is OrgLineUi.SetsPartOfSurvey -> InlineNotice(
            text = stringResource(R.string.survey_org_sets_part) + turnedOffSuffix(line.turnedOffCount),
            icon = R.drawable.ic_domain,
            modifier = modifier,
        ) { NsTextAction(stringResource(R.string.survey_org_see_all), onSeeAll) }

        is OrgLineUi.TurnedOff -> InlineNotice(
            text = pluralStringResource(R.plurals.survey_org_turned_off, line.count, line.count),
            icon = R.drawable.ic_domain,
            modifier = modifier,
        ) { NsTextAction(stringResource(R.string.survey_org_see_all), onSeeAll) }
    }
}

@Composable
private fun turnedOffSuffix(count: Int): String =
    if (count == 0) "" else " " + pluralStringResource(R.plurals.survey_org_turned_off, count, count)
