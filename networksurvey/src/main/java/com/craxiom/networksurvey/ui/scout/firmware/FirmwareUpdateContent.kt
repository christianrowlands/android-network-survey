package com.craxiom.networksurvey.ui.scout.firmware

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.firmware.FirmwareComparison
import com.craxiom.networksurvey.scout.firmware.FirmwareInstallPhase
import com.craxiom.networksurvey.ui.common.NsCtaBar
import com.craxiom.networksurvey.ui.common.NsPageScaffold
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsPrimaryCta
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.theme.NsTheme
import kotlinx.coroutines.launch

/**
 * The firmware update screen, stateless: the header (what, from and to which version), what blocks
 * an update now, the current stage, and the one main action in the bottom bar. Canceling asks
 * first. Both Back routes (the top bar arrow and the system) ask too while the update can still
 * stop, and wait after OtaEnd, when the Scout is switching images.
 */
@Composable
fun FirmwareUpdateContent(
    state: FirmwareUpdateUi,
    actions: FirmwareUpdateActions,
    modifier: Modifier = Modifier,
) {
    var confirmingCancel by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    fun goBack() {
        when {
            state.cancelable -> confirmingCancel = true
            state.installing -> scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.scout_fw_back_blocked)) }
            else -> actions.onBack()
        }
    }
    BackHandler(enabled = state.installing) { goBack() }

    NsPageScaffold(
        title = stringResource(R.string.scout_fw_title),
        onBack = { goBack() },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        bottomBar = { FirmwareCtaBar(state, actions) { confirmingCancel = true } },
    ) {
        Header(state)
        val finished = state.stage is FirmwareStage.Finished
        if (state.blocker != null && !state.installing && !finished) {
            FirmwareBlockerPanel(state.blocker, state.scoutName, state.nearbyAccess) { actions.onFixBlocker(state.blocker) }
        }
        StagePanel(state, actions)
    }
    if (confirmingCancel) {
        NsConfirmationDialog(
            title = stringResource(R.string.scout_fw_cancel_title),
            message = stringResource(R.string.scout_fw_cancel_body, state.scoutName),
            confirmText = stringResource(R.string.scout_fw_action_cancel),
            dismissText = stringResource(R.string.scout_fw_cancel_keep),
            destructive = true,
            onConfirm = {
                confirmingCancel = false
                actions.onCancel()
            },
            onDismiss = { confirmingCancel = false },
        )
    }
}

@Composable
private fun Header(state: FirmwareUpdateUi) {
    val stage = state.stage
    val offer = (stage as? FirmwareStage.Offer)?.offer ?: (stage as? FirmwareStage.Downloading)?.offer
    val date = FirmwareUpdateText.releaseDate(offer?.release?.manifest?.released)
    val signedLine = when {
        offer != null && date != null -> stringResource(R.string.scout_fw_released, date)
        offer != null || stage is FirmwareStage.Installing -> stringResource(R.string.scout_fw_signed)
        else -> null
    }
    FirmwareHeader(state.scoutName, state.installedVersion, state.targetVersion, signedLine)
}

@Composable
private fun StagePanel(state: FirmwareUpdateUi, actions: FirmwareUpdateActions) {
    val context = LocalContext.current
    when (val stage = state.stage) {
        FirmwareStage.Checking -> NsPanel {
            Row(
                modifier = Modifier.heightIn(min = NsTheme.spacing.touchTarget),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(NsTheme.spacing.xxl))
                PanelText(stringResource(R.string.scout_fw_checking))
            }
        }

        is FirmwareStage.Offer -> NsPanel {
            val offer = stage.offer
            when (offer.comparison) {
                FirmwareComparison.SAME -> HeadlineText(stringResource(R.string.scout_fw_same_title, state.scoutName, offer.version))
                FirmwareComparison.OLDER -> HeadlineText(stringResource(R.string.scout_fw_older_title, state.scoutName, offer.version))
                else -> Unit
            }
            offer.release.newerVersionSeen?.let { PanelText(stringResource(R.string.scout_fw_newer_seen, it), color = NsTheme.colors.status.warningText) }
            val size = Formatter.formatShortFileSize(context, offer.image.size.toLong())
            PanelText(stringResource(R.string.scout_fw_preflight, size, state.scoutName))
            ConsentRows(state.consents, state.accepted, actions::onAccept)
        }

        is FirmwareStage.Downloading -> NsPanel { Steps(FirmwareStepIndex.DOWNLOAD, DOWNLOAD_PROGRESS, null) }

        is FirmwareStage.Installing -> NsPanel {
            val (step, progress, detail) = installProgress(stage.phase)
            Steps(step, progress, detail)
            PanelText(stringResource(R.string.scout_fw_keep_open, state.scoutName))
        }

        is FirmwareStage.Finished -> DonePanel(state, stage)
        is FirmwareStage.Failed -> NoticePanel(
            NsTheme.colors.status.danger, R.drawable.ic_error_outline, stringResource(R.string.scout_fw_failed_title),
            FirmwareUpdateText.error(LocalResources.current, stage.error, state.scoutName),
        )
    }
}

@Composable
private fun DonePanel(state: FirmwareUpdateUi, stage: FirmwareStage.Finished) {
    val result = stage.result
    NsPanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = NsTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        ) {
            Icon(
                painterResource(R.drawable.ic_check_circle_filled), contentDescription = null,
                tint = NsTheme.colors.status.success.content, modifier = Modifier.size(DONE_ICON),
            )
            val title = if (result.newVersion == result.previousVersion) R.string.scout_fw_done_reinstalled else R.string.scout_fw_done_title
            Text(stringResource(title, result.newVersion), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (result.compatible) {
                PanelText(stringResource(R.string.scout_fw_done_body, state.scoutName))
            } else {
                PanelText(stringResource(R.string.scout_fw_done_incompatible, state.scoutName), color = NsTheme.colors.status.warningText)
            }
        }
    }
}

@Composable
private fun Steps(step: FirmwareStepIndex, progress: Float, detail: String?) {
    val steps = FirmwareStepIndex.entries
    FirmwareSteps(steps.map { it.label }, steps.indexOf(step), progress, detail)
}

/** The step, overall progress and detail line for an install [phase]. */
@Composable
private fun installProgress(phase: FirmwareInstallPhase): Triple<FirmwareStepIndex, Float, String?> = when (phase) {
    FirmwareInstallPhase.Connecting -> Triple(FirmwareStepIndex.SEND, SEND_START, null)
    is FirmwareInstallPhase.Transferring ->
        Triple(FirmwareStepIndex.SEND, SEND_START + SEND_SPAN * phase.fraction, stringResource(R.string.scout_fw_sent_percent, phase.percent))

    is FirmwareInstallPhase.Reconnecting ->
        Triple(FirmwareStepIndex.SEND, SEND_START, stringResource(R.string.scout_fw_reconnecting, phase.attempt))

    FirmwareInstallPhase.Verifying -> Triple(FirmwareStepIndex.VERIFY, VERIFY_PROGRESS, null)
    FirmwareInstallPhase.Restarting -> Triple(FirmwareStepIndex.RESTART, RESTART_PROGRESS, null)
    FirmwareInstallPhase.Confirming -> Triple(FirmwareStepIndex.CONFIRM, CONFIRM_PROGRESS, null)
}

/** The bottom bar's one main action for the stage, and Cancel while the update can still stop. */
@Composable
private fun FirmwareCtaBar(state: FirmwareUpdateUi, actions: FirmwareUpdateActions, onCancel: () -> Unit) {
    val ready = state.blocker == null && state.allAccepted
    when (val stage = state.stage) {
        is FirmwareStage.Offer -> NsCtaBar {
            val label = FirmwareUpdateText.releaseAction(LocalResources.current, stage.offer.comparison, stage.offer.version)
            NsPrimaryCta(label, actions::onUpdate, icon = R.drawable.ic_system_update, enabled = ready)
        }

        is FirmwareStage.Downloading -> NsCtaBar { CancelAction(onCancel) }
        is FirmwareStage.Installing -> if (stage.cancelable) NsCtaBar { CancelAction(onCancel) }
        is FirmwareStage.Finished -> NsCtaBar { NsPrimaryCta(stringResource(R.string.scout_fw_action_done), actions::onDone) }
        is FirmwareStage.Failed -> NsCtaBar {
            if (stage.resumable) {
                NsPrimaryCta(stringResource(R.string.scout_fw_action_resume), actions::onResume, enabled = state.blocker == null)
                NsTextAction(stringResource(R.string.scout_fw_action_start_over), actions::onStartOver, Modifier.fillMaxWidth())
            } else {
                NsPrimaryCta(stringResource(R.string.scout_fw_action_start_over), actions::onStartOver)
            }
        }

        FirmwareStage.Checking -> Unit
    }
}

@Composable
private fun CancelAction(onCancel: () -> Unit) {
    NsTextAction(stringResource(R.string.scout_fw_action_cancel), onCancel, Modifier.fillMaxWidth())
}

@Composable
private fun HeadlineText(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

/** The update steps in order. */
private enum class FirmwareStepIndex(val label: Int) {
    DOWNLOAD(R.string.scout_fw_step_download),
    SEND(R.string.scout_fw_step_send),
    VERIFY(R.string.scout_fw_step_verify),
    RESTART(R.string.scout_fw_step_restart),
    CONFIRM(R.string.scout_fw_step_confirm),
}

// Overall progress at each step, as on the prototype: sending takes most of the time
private const val DOWNLOAD_PROGRESS = 0.05f
private const val SEND_START = 0.1f
private const val SEND_SPAN = 0.7f
private const val VERIFY_PROGRESS = 0.82f
private const val RESTART_PROGRESS = 0.9f
private const val CONFIRM_PROGRESS = 0.95f
private val DONE_ICON = NsTheme.spacing.xxxl + NsTheme.spacing.l
