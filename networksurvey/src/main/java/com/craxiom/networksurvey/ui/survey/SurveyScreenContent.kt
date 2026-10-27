package com.craxiom.networksurvey.ui.survey

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.common.NsManagedLockSheet
import com.craxiom.networksurvey.ui.common.NsScrollEdgeFade
import com.craxiom.networksurvey.ui.survey.components.AttentionPlate
import com.craxiom.networksurvey.ui.survey.components.AttentionPlateActions
import com.craxiom.networksurvey.ui.survey.components.CommunityUploadProgressRow
import com.craxiom.networksurvey.ui.survey.components.DestinationRows
import com.craxiom.networksurvey.ui.survey.components.LiveDestinationRows
import com.craxiom.networksurvey.ui.survey.components.LocationLine
import com.craxiom.networksurvey.ui.survey.components.OrgStatusLine
import com.craxiom.networksurvey.ui.survey.components.RecordTypeTiles
import com.craxiom.networksurvey.ui.survey.components.RecordingHero
import com.craxiom.networksurvey.ui.survey.components.RecordingSetupPanel
import com.craxiom.networksurvey.ui.survey.components.RecordsPanel
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.survey.components.SurveyCtaBar
import com.craxiom.networksurvey.ui.survey.components.StillToDoPanel
import com.craxiom.networksurvey.ui.survey.components.SurveySentence
import com.craxiom.networksurvey.ui.survey.model.AttentionItemUi
import com.craxiom.networksurvey.ui.survey.model.ItemLock
import com.craxiom.networksurvey.ui.survey.model.SectionCount
import com.craxiom.networksurvey.ui.survey.model.SurveyUiState
import com.craxiom.networksurvey.ui.survey.sheets.ChangeSetupSheet
import com.craxiom.networksurvey.ui.survey.sheets.DisableQueueLimitDialog
import com.craxiom.networksurvey.ui.survey.sheets.EditDestinationListSheet
import com.craxiom.networksurvey.ui.survey.sheets.RestoreOrganizationDialog
import com.craxiom.networksurvey.ui.survey.sheets.StopManagedSurveyDialog
import com.craxiom.networksurvey.ui.survey.sheets.UseOwnSettingsDialog
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.main.appbar.AppBarAction
import com.craxiom.networksurvey.ui.main.appbar.TabTopBar
import com.craxiom.networksurvey.ui.survey.sheets.SetupsSheet
import com.craxiom.networksurvey.ui.survey.sheets.WhatGoesWhereSheet

/** Which overlay the Survey screen is showing, if any. */
enum class SurveyOverlay { NONE, LOCK, CHANGE, EDIT_LIST, STOP, USE_OWN, RESTORE, DISABLE_LIMIT, SETUPS, WHAT_GOES_WHERE }

/**
 * The Survey screen, stateless. Ready shows the two questions as a form above Start; Recording
 * shows the live survey above Stop. Snackbars sit above the button bar so they never cover Stop.
 * Until the service has said whether a survey runs ([SurveyUiState.known]), neither is drawn, so a
 * running survey never shows Start. The app bar holds Saved setups (Ready only) and Settings.
 *
 * @param preview Builds the screen state for a draft setup, for the Change sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyScreenContent(
    state: SurveyUiState,
    actions: SurveyActions,
    preview: (Set<RecordType>, Set<Destination>) -> SurveyUiState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var overlay by rememberSaveable { mutableStateOf(SurveyOverlay.NONE) }
    val plateActions = remember(actions) { plateActions(actions) { overlay = SurveyOverlay.DISABLE_LIMIT } }

    // The bar slides away as the form scrolls, so a short (landscape) window has room for it
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        TabTopBar(
            title = stringResource(R.string.nav_survey),
            actions = appBarActions(state, actions) { overlay = it },
            scrollBehavior = scrollBehavior,
        )
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(scrollState),
            ) {
                Column(
                    modifier = Modifier
                        .nsContentWidth()
                        .padding(horizontal = NsTheme.spacing.screenGutter)
                        .padding(bottom = NsTheme.spacing.l),
                    verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
                ) {
                    LocationLine(
                        status = state.location,
                        lastSession = state.lastSession,
                        onTurnOnLocation = actions::onTurnOnLocation,
                        onAllowLocation = actions::onAllowLocation,
                        onCopyMissionId = actions::onCopyMissionId,
                    )
                    state.orgLine?.let { line ->
                        OrgStatusLine(
                            line = line,
                            onReview = { overlay = SurveyOverlay.RESTORE },
                            onRestore = { overlay = SurveyOverlay.RESTORE },
                            onSeeAll = actions::onOpenOrganizationSettings,
                        )
                    }
                    state.plates.forEach { AttentionPlate(it, plateActions) }
                    state.communityUpload?.let { CommunityUploadProgressRow(it, actions::onCancelCommunityUpload) }
                    if (state.known) {
                        AnimatedContent(
                            targetState = state.isRecording,
                            transitionSpec = { fadeIn(tween(TRANSITION_MS)) togetherWith fadeOut(tween(TRANSITION_MS)) },
                            label = "surveyState",
                        ) { recording ->
                            if (recording) RecordingBody(state, actions, onOverlay = { overlay = it })
                            else ReadyBody(state, actions, onOverlay = { overlay = it })
                        }
                    }
                }
            }
            if (state.known) NsScrollEdgeFade(scrollState.canScrollForward, Modifier.align(Alignment.BottomCenter))
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
        if (state.known) {
            SurveyCtaBar(
                recording = state.isRecording,
                startBlock = state.startBlock,
                onStart = actions::onStart,
                onStop = { if (state.stopNeedsConfirmation) overlay = SurveyOverlay.STOP else actions.onStop() },
                onUseLogFiles = actions::onUseLogFiles,
                onConnectNsAnalytics = actions::onConnectNsAnalytics,
                commandInFlight = state.commandInFlight,
            )
        }
    }

    SurveyOverlays(overlay, state, actions, preview) { overlay = it }
}

/**
 * Saved setups while Ready (a setup can't be picked mid-survey; Change does that) and when the
 * organization leaves something to choose, then Settings.
 */
private fun appBarActions(state: SurveyUiState, actions: SurveyActions, onOverlay: (SurveyOverlay) -> Unit): List<AppBarAction> =
    listOfNotNull(
        if (state.known && !state.isRecording && state.savedSetups != null) {
            AppBarAction(R.drawable.ic_bookmarks, R.string.survey_setups_heading, { onOverlay(SurveyOverlay.SETUPS) })
        } else {
            null
        },
        AppBarAction(R.drawable.ic_settings, R.string.settings, actions::onOpenSettings),
    )

@Composable
private fun ReadyBody(state: SurveyUiState, actions: SurveyActions, onOverlay: (SurveyOverlay) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
        if (state.stillToDo.isNotEmpty()) {
            StillToDoPanel(state.stillToDo, state.nowMs, actions::onUploadNow, actions::onOpenDestination)
        }
        SectionHeader(stringResource(R.string.survey_question_types), trailing = countText(state.typeCount))
        RecordTypeTiles(
            tiles = state.tiles,
            onToggle = actions::onToggleRecordType,
            onLocked = { tile ->
                if (tile.lock == ItemLock.INCLUDED_WITH_CELLULAR) actions.onExplainPhoneState() else onOverlay(SurveyOverlay.LOCK)
            },
        )
        SectionHeader(stringResource(R.string.survey_question_destinations), trailing = countText(state.destinationCount))
        DestinationRows(
            rows = state.destinations,
            info = state.destinationInfo,
            userHiddenCount = state.userHiddenCount,
            onToggle = actions::onToggleDestination,
            onOpen = actions::onOpenDestination,
            onLocked = { onOverlay(SurveyOverlay.LOCK) },
            onEditList = { onOverlay(SurveyOverlay.EDIT_LIST) },
            onWhatGoesWhere = state.routing?.let { { onOverlay(SurveyOverlay.WHAT_GOES_WHERE) } },
        )
        state.sentence?.let { SurveySentence(it) }
    }
}

@Composable
private fun RecordingBody(state: SurveyUiState, actions: SurveyActions, onOverlay: (SurveyOverlay) -> Unit) {
    val recording = state.recording ?: return
    Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.panelGap)) {
        RecordingHero(recording, actions::onCopyMissionId)
        RecordingSetupPanel(
            types = recording.types,
            canChange = recording.canChange,
            onChange = { onOverlay(SurveyOverlay.CHANGE) },
            onWhatGoesWhere = { onOverlay(SurveyOverlay.WHAT_GOES_WHERE) },
            setupName = state.savedSetups?.selected?.name,
            notSent = recording.notSent,
            deviceStatusOnly = recording.deviceStatusOnly,
            availableTypes = recording.availableTypes,
        )
        val countedTypes = recording.types.filter { it in recording.counts }
        if (countedTypes.isNotEmpty()) RecordsPanel(countedTypes, recording.counts, recording.countsSavedForUpload)
        LiveDestinationRows(
            destinations = recording.destinations,
            info = state.destinationInfo,
            nowMs = state.nowMs,
            onOpen = actions::onOpenDestination,
            onUploadNow = actions::onUploadNow,
        )
    }
}

@Composable
private fun SurveyOverlays(
    overlay: SurveyOverlay,
    state: SurveyUiState,
    actions: SurveyActions,
    preview: (Set<RecordType>, Set<Destination>) -> SurveyUiState,
    show: (SurveyOverlay) -> Unit,
) {
    val close = { show(SurveyOverlay.NONE) }
    when (overlay) {
        SurveyOverlay.NONE -> Unit
        SurveyOverlay.LOCK -> NsManagedLockSheet(
            onSeeAll = actions::onOpenOrganizationSettings,
            onUseOwnSettings = { show(SurveyOverlay.USE_OWN) },
            onDismiss = close,
        )

        // From what runs, which an Intent or the organization may have set apart from the working setup
        SurveyOverlay.CHANGE -> ChangeSetupSheet(
            initial = state.recording?.changeSeed ?: state.userSetup,
            preview = preview,
            onApply = actions::onApplyDraft,
            onOpenDestination = actions::onOpenDestination,
            onDismiss = close,
            onSeeOrganizationSettings = actions::onOpenOrganizationSettings,
            onUseOwnSettings = { show(SurveyOverlay.USE_OWN) },
        )
        SurveyOverlay.EDIT_LIST -> EditDestinationListSheet(state.editList, actions::onSetDestinationShown, close)
        SurveyOverlay.STOP -> StopManagedSurveyDialog(state.stopRestartsAtBoot, actions::onStop, close)
        SurveyOverlay.USE_OWN -> UseOwnSettingsDialog(actions::onUseOwnSettings, close)
        SurveyOverlay.RESTORE -> RestoreOrganizationDialog(state.isRecording, actions::onRestoreOrganizationSettings, close)
        SurveyOverlay.DISABLE_LIMIT -> DisableQueueLimitDialog(actions::onDisableQueueLimit, close)
        // A survey started underneath (auto start, another app) closes it: a pick would edit a running survey
        SurveyOverlay.SETUPS -> state.savedSetups?.takeIf { !state.isRecording }?.let { setups ->
            SetupsSheet(
                state = setups,
                onPick = actions::onPickSetup,
                onSave = actions::onSaveSetup,
                onStar = actions::onStarSetup,
                onRename = actions::onRenameSetup,
                onDelete = actions::onDeleteSetup,
                onDismiss = close,
            )
        } ?: close()

        SurveyOverlay.WHAT_GOES_WHERE -> state.routing?.let { WhatGoesWhereSheet(it, close) } ?: close()
    }
}

@Composable
private fun countText(count: SectionCount): String = if (count.allLocked) {
    stringResource(R.string.survey_set_by_organization)
} else {
    stringResource(R.string.survey_section_count, count.on, count.total)
}

private fun plateActions(actions: SurveyActions, confirmDisableLimit: () -> Unit) = object : AttentionPlateActions {
    override fun onChangeBatteryThreshold() = actions.onChangeBatteryThreshold()
    override fun onAdjustQueueLimit() = actions.onAdjustQueueLimit()
    override fun onDisableQueueLimit() = confirmDisableLimit()
    override fun onOpenOrganizationSettings() = actions.onOpenOrganizationSettings()
    override fun onOpenDestination(destination: Destination) = actions.onOpenDestination(destination)
    override fun onFixItem(item: AttentionItemUi) = actions.onFixItem(item)
}

private const val TRANSITION_MS = 200
