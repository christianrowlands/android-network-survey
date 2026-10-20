package com.craxiom.networksurvey.ui.survey.sheets

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.ui.common.NsFormBottomSheet
import com.craxiom.networksurvey.ui.common.NsManagedLockSheet
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.survey.components.DestinationRows
import com.craxiom.networksurvey.ui.survey.components.RecordTypeTiles
import com.craxiom.networksurvey.ui.survey.model.ItemLock
import com.craxiom.networksurvey.ui.survey.model.SurveyUiState
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * Changes a running survey. The same two questions as the Ready screen, on a draft that applies
 * to the running survey (without a new Mission ID) only when "Apply now" is tapped. The draft
 * survives a rotation, and a locked tile or row explains itself as it does on the Ready screen.
 *
 * @param initial  What the running survey asked for, which the draft starts from (not the working
 *                 setup: a survey another app or the organization started may differ from it).
 * @param preview  Builds the screen state for a draft, so the sheet shows exactly the locks,
 *                 warnings and counts the Survey screen would.
 * @param onSeeOrganizationSettings The lock sheet's link to everything the organization sets.
 * @param onUseOwnSettings The lock sheet's "Use my own settings", or null when it can't be offered.
 */
@Composable
fun ChangeSetupSheet(
    initial: SurveySetup,
    preview: (Set<RecordType>, Set<Destination>) -> SurveyUiState,
    onApply: (Set<RecordType>, Set<Destination>) -> Unit,
    onOpenDestination: (Destination) -> Unit,
    onDismiss: () -> Unit,
    onSeeOrganizationSettings: () -> Unit = {},
    onUseOwnSettings: (() -> Unit)? = null,
) {
    var types by rememberSaveable(stateSaver = enumSetSaver<RecordType>()) { mutableStateOf(initial.recordTypes) }
    var destinations by rememberSaveable(stateSaver = enumSetSaver<Destination>()) { mutableStateOf(initial.destinations) }
    var showLock by rememberSaveable { mutableStateOf(false) }
    var explainPhoneState by rememberSaveable { mutableStateOf(false) }
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
            onLocked = { tile ->
                // The Survey screen's snackbar would sit under this sheet, so the sheet says it itself
                if (tile.lock == ItemLock.INCLUDED_WITH_CELLULAR) explainPhoneState = true else showLock = true
            },
        )
        if (explainPhoneState) {
            Text(
                text = stringResource(R.string.survey_phone_state_follows_cellular),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            onLocked = { showLock = true },
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

    if (showLock) {
        NsManagedLockSheet(
            onSeeAll = {
                showLock = false
                onSeeOrganizationSettings()
            },
            onUseOwnSettings = onUseOwnSettings?.let { useOwn ->
                {
                    showLock = false
                    useOwn()
                }
            },
            onDismiss = { showLock = false },
        )
    }
}

/** Saves a set of enum values by name, so the draft survives a rotation. */
private inline fun <reified E : Enum<E>> enumSetSaver(): Saver<Set<E>, ArrayList<String>> = Saver(
    save = { set -> ArrayList(set.map { it.name }) },
    restore = { names -> names.mapNotNull { name -> enumValues<E>().firstOrNull { it.name == name } }.toSet() },
)
