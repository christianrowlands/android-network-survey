package com.craxiom.networksurvey.ui.main

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.annotatedStringResource
import com.craxiom.networksurvey.ui.common.dialogs.LabeledCheckbox
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.preview.NsPreview
import com.craxiom.networksurvey.ui.preview.PreviewDayNight

/**
 * The actions the host activity performs in response to the startup permission/location dialogs.
 * The activity keeps ownership of the permission decision logic (requesting permissions, opening
 * settings, persisting a denial) and implements this interface so the Compose dialogs can invoke it.
 */
interface StartupDialogActions {
    fun onPermissionRationaleAcknowledged()
    fun onEnableGpsConfirmed()
    fun onBackgroundLocationConfirmed()
    fun onBackgroundLocationDenied()
    fun onGnssFailureAcknowledged(rememberDecision: Boolean)
}

/**
 * Renders the startup permission, GPS, and GNSS-failure dialogs using the shared Compose dialog
 * components. Visibility is driven by [SharedViewModel] state that the host activity sets, and the
 * dialog actions are delegated back to the activity through [StartupDialogActions].
 */
@Composable
fun StartupDialogs(
    actions: StartupDialogActions,
    viewModel: SharedViewModel,
) {
    val startupDialog by viewModel.startupDialog.collectAsStateWithLifecycle()
    val gnssFailureVisible by viewModel.gnssFailureDialogVisible.collectAsStateWithLifecycle()

    when (startupDialog) {
        StartupDialog.PermissionRationale -> NsConfirmationDialog(
            title = stringResource(R.string.permissions_rationale_title),
            message = stringResource(R.string.permissions_rationale),
            confirmText = stringResource(android.R.string.ok),
            onConfirm = actions::onPermissionRationaleAcknowledged,
            onDismiss = viewModel::clearStartupDialog,
            dismissText = null,
        )

        StartupDialog.BackgroundLocationRationale -> NsConfirmationDialog(
            title = stringResource(R.string.background_location_permission_rationale_title),
            message = stringResource(R.string.background_location_permission_rationale),
            confirmText = stringResource(R.string.open_settings),
            onConfirm = actions::onBackgroundLocationConfirmed,
            onDismiss = viewModel::clearStartupDialog,
            dismissText = stringResource(R.string.deny_permission),
            onCancel = actions::onBackgroundLocationDenied,
        )

        StartupDialog.EnableGps -> NsConfirmationDialog(
            title = stringResource(R.string.enable_gps_title),
            message = stringResource(R.string.enable_gps_message),
            confirmText = stringResource(R.string.enable_gps_positive_button),
            onConfirm = actions::onEnableGpsConfirmed,
            onDismiss = viewModel::clearStartupDialog,
            dismissText = stringResource(R.string.enable_gps_negative_button),
        )

        null -> Unit
    }

    if (gnssFailureVisible) {
        GnssFailureDialog(
            onDismiss = viewModel::clearGnssFailureDialog,
            onAcknowledge = actions::onGnssFailureAcknowledged,
        )
    }
}

/**
 * Dialog shown when the device is not providing raw GNSS measurements. Explains the limitation with
 * a link to Android's documentation and lets the user suppress future notifications.
 */
@Composable
fun GnssFailureDialog(
    onDismiss: () -> Unit,
    onAcknowledge: (rememberDecision: Boolean) -> Unit,
) {
    var rememberDecision by remember { mutableStateOf(false) }

    NsConfirmationDialog(
        title = stringResource(R.string.not_supported_title),
        confirmText = stringResource(R.string.ok),
        onConfirm = { onAcknowledge(rememberDecision) },
        onDismiss = onDismiss,
        dismissText = null,
        body = {
            Text(
                text = annotatedStringResource(R.string.not_supported_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            LabeledCheckbox(
                checked = rememberDecision,
                onCheckedChange = { rememberDecision = it },
                label = stringResource(R.string.remember_my_decision),
            )
        },
    )
}

@PreviewDayNight
@Composable
private fun GnssFailureDialogPreview() {
    NsPreview {
        GnssFailureDialog(onDismiss = {}, onAcknowledge = {})
    }
}
