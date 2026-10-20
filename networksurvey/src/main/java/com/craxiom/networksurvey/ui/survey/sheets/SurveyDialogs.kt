package com.craxiom.networksurvey.ui.survey.sheets

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog

/** Confirms Stop on a survey the organization set up, which may start again on its own. */
@Composable
fun StopManagedSurveyDialog(restartsAtBoot: Boolean, onStop: () -> Unit, onDismiss: () -> Unit) {
    NsConfirmationDialog(
        title = stringResource(R.string.survey_stop_confirm_title),
        message = if (restartsAtBoot) stringResource(R.string.survey_stop_confirm_body) else null,
        confirmText = stringResource(R.string.survey_stop),
        dismissText = stringResource(R.string.survey_keep_surveying),
        destructive = true,
        onConfirm = onStop,
        onDismiss = onDismiss,
    )
}

/** Confirms turning on "Override organization settings". */
@Composable
fun UseOwnSettingsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NsConfirmationDialog(
        title = stringResource(R.string.survey_override_title),
        message = stringResource(R.string.survey_override_body),
        confirmText = stringResource(R.string.survey_override_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Confirms going back to the organization's settings, saying what will change. */
@Composable
fun RestoreOrganizationDialog(recording: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NsConfirmationDialog(
        title = stringResource(R.string.survey_restore_title),
        message = stringResource(if (recording) R.string.survey_restore_running else R.string.survey_restore_idle),
        confirmText = stringResource(R.string.survey_org_restore),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Confirms turning off the MQTT queue limit (moved from the old Dashboard). */
@Composable
fun DisableQueueLimitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NsConfirmationDialog(
        title = stringResource(R.string.disable_queue_limit_title),
        message = stringResource(R.string.disable_queue_limit_message),
        confirmText = stringResource(R.string.disable_queue_limit_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
