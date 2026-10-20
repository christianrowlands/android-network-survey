package com.craxiom.networksurvey.ui.survey.sheets

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.ui.common.NsFormBottomSheet
import com.craxiom.networksurvey.ui.survey.components.DestinationRows
import com.craxiom.networksurvey.ui.survey.components.RecordTypeTiles
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.survey.model.SurveyUiState
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * Changes a running survey. The same two questions as the Ready screen, on a draft that applies
 * to the running survey (without a new Mission ID) only when "Apply now" is tapped.
 *
 * @param preview Builds the screen state for a draft, so the sheet shows exactly the locks,
 *                warnings and counts the Survey screen would.
 */
@Composable
fun ChangeSetupSheet(
    initial: SurveySetup,
    preview: (Set<RecordType>, Set<Destination>) -> SurveyUiState,
    onApply: (Set<RecordType>, Set<Destination>) -> Unit,
    onOpenDestination: (Destination) -> Unit,
    onDismiss: () -> Unit,
) {
    var types by remember { mutableStateOf(initial.recordTypes) }
    var destinations by remember { mutableStateOf(initial.destinations) }
    val draft = remember(types, destinations) { preview(types, destinations) }
    val canApply = draft.typeCount.on > 0 && draft.destinationCount.on > 0

    NsFormBottomSheet(
        title = stringResource(R.string.survey_change_title),
        confirmText = stringResource(R.string.survey_apply_now),
        confirmEnabled = canApply,
        onConfirm = {
            onApply(types, destinations)
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        SectionHeader(
            title = stringResource(R.string.survey_question_types),
            trailing = stringResource(R.string.survey_section_count, draft.typeCount.on, draft.typeCount.total),
        )
        RecordTypeTiles(
            tiles = draft.tiles,
            onToggle = { type -> types = if (type in types) types - type else types + type },
            onLocked = {},
        )
        SectionHeader(
            title = stringResource(R.string.survey_question_destinations),
            trailing = stringResource(R.string.survey_section_count, draft.destinationCount.on, draft.destinationCount.total),
        )
        DestinationRows(
            rows = draft.destinations,
            info = draft.destinationInfo,
            userHiddenCount = 0,
            onToggle = { d -> destinations = if (d in destinations) destinations - d else destinations + d },
            onOpen = {
                onDismiss()
                onOpenDestination(it)
            },
            onLocked = {},
            onEditList = {},
            showEditList = false,
        )
        if (Destination.NS_ANALYTICS in initial.destinations && Destination.NS_ANALYTICS !in destinations) {
            Text(
                text = stringResource(
                    if (draft.destinationInfo.nsAnalyticsAutoUpload) {
                        R.string.survey_remove_ns_analytics_note
                    } else {
                        R.string.survey_remove_ns_analytics_note_manual
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = NsTheme.colors.status.warningText,
            )
        }
    }
}
