package com.craxiom.networksurvey.ui.grpc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsCtaBar
import com.craxiom.networksurvey.ui.common.NsFormField
import com.craxiom.networksurvey.ui.common.NsManagedLockSheet
import com.craxiom.networksurvey.ui.common.NsPageScaffold
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsPrimaryCta
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsStatusPanel
import com.craxiom.networksurvey.ui.common.NsSummaryText
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.survey.DestinationPageStatus
import com.craxiom.networksurvey.ui.survey.components.DestinationStatusPanel
import com.craxiom.networksurvey.ui.survey.sheets.UseOwnSettingsDialog
import com.craxiom.networksurvey.ui.theme.NsTheme

/** Everything the gRPC page can ask for. */
interface GrpcServerActions {
    fun onBack() {}
    fun onEdit(change: (GrpcServerForm) -> GrpcServerForm) {}
    fun onSave() {}
    fun onSeeOrganizationSettings() {}
    fun onUseOwnSettings() {}
}

/**
 * The gRPC page, stateless: how the saved server is doing, the server's host, port and this phone's
 * device name, and what is sent. Save is pinned at the bottom.
 */
@Composable
fun GrpcServerContent(
    form: GrpcServerForm,
    actions: GrpcServerActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    liveStatus: DestinationPageStatus? = null,
) {
    var showLockSheet by rememberSaveable { mutableStateOf(false) }
    var confirmOwnSettings by rememberSaveable { mutableStateOf(false) }
    val hostFocus = remember { FocusRequester() }
    val portFocus = remember { FocusRequester() }

    // A failed save moves focus to the first field that needs fixing.
    LaunchedEffect(form.errorEpoch) {
        if (form.errorEpoch == 0) return@LaunchedEffect
        if (form.hostError) hostFocus.requestFocus() else if (form.portError) portFocus.requestFocus()
    }

    NsPageScaffold(
        title = stringResource(R.string.survey_dest_grpc),
        onBack = actions::onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        bottomBar = { SaveBar(form, actions) },
    ) {
        StatusPanel(form.status, liveStatus)
        if (form.organizationOff) {
            NsRowGroup {
                NsRow(
                    title = stringResource(R.string.grpc_organization_off),
                    icon = R.drawable.ic_domain,
                    locked = true,
                    onClickLabel = stringResource(R.string.survey_lock_learn_why),
                    onClick = { showLockSheet = true },
                )
            }
        }
        NsPanel {
            Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m)) {
                NsFormField(
                    R.string.mqtt_field_host, form.host,
                    modifier = Modifier.focusRequester(hostFocus),
                    placeholder = stringResource(R.string.grpc_host_placeholder),
                    error = if (form.hostError) stringResource(R.string.grpc_error_host) else null,
                    keyboardType = KeyboardType.Uri,
                ) { v -> actions.onEdit { it.copy(host = v) } }
                NsFormField(
                    R.string.mqtt_field_port, form.port,
                    modifier = Modifier.focusRequester(portFocus),
                    error = if (form.portError) stringResource(R.string.mqtt_error_port) else null,
                    keyboardType = KeyboardType.Number,
                    digitsOnly = true,
                ) { v -> actions.onEdit { it.copy(port = v) } }
                NsFormField(
                    R.string.grpc_field_device_name, form.deviceName,
                    supporting = stringResource(R.string.grpc_field_device_name_help),
                    imeAction = ImeAction.Done,
                    onDone = actions::onSave,
                ) { v -> actions.onEdit { it.copy(deviceName = v) } }
            }
        }
        SectionHeader(stringResource(R.string.destination_what_is_sent_title))
        NsSummaryText(stringResource(R.string.destination_what_is_sent))
    }

    if (showLockSheet) {
        NsManagedLockSheet(
            onSeeAll = actions::onSeeOrganizationSettings,
            onUseOwnSettings = {
                showLockSheet = false
                confirmOwnSettings = true
            },
            onDismiss = { showLockSheet = false },
        )
    }
    if (confirmOwnSettings) {
        UseOwnSettingsDialog(
            onConfirm = {
                confirmOwnSettings = false
                actions.onUseOwnSettings()
            },
            onDismiss = { confirmOwnSettings = false },
        )
    }
}

@Composable
private fun StatusPanel(status: GrpcStatus, liveStatus: DestinationPageStatus?) {
    when (status) {
        GrpcStatus.NotSetUp -> NsStatusPanel(
            icon = R.drawable.ic_dns,
            title = stringResource(R.string.survey_dest_not_set_up),
            subtitle = stringResource(R.string.grpc_status_not_set_up_detail),
        )

        is GrpcStatus.Ready -> NsStatusPanel(
            icon = R.drawable.ic_dns,
            title = stringResource(R.string.grpc_status_ready),
            subtitle = status.address,
            iconTint = MaterialTheme.colorScheme.primary,
        )

        is GrpcStatus.OffInSurvey -> NsStatusPanel(
            icon = R.drawable.ic_dns,
            title = stringResource(R.string.grpc_status_off),
            subtitle = status.address,
        )

        // The same status and detail as the Survey screen's row
        is GrpcStatus.Streaming -> liveStatus?.let { DestinationStatusPanel(it) }
            ?: NsStatusPanel(icon = R.drawable.ic_dns, title = stringResource(R.string.grpc_status_ready), subtitle = status.address)

        is GrpcStatus.Unreachable -> NsStatusPanel(
            icon = R.drawable.ic_dns,
            title = stringResource(R.string.grpc_status_unreachable),
            subtitle = stringResource(R.string.grpc_status_unreachable_detail, status.address),
            iconTint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SaveBar(form: GrpcServerForm, actions: GrpcServerActions) {
    NsCtaBar {
        if (form.streaming) {
            Text(
                text = stringResource(R.string.grpc_streaming_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NsPrimaryCta(
            text = stringResource(
                when {
                    form.savesAndTurnsOn -> R.string.grpc_save_and_turn_on
                    form.configured -> R.string.grpc_save_changes
                    else -> R.string.save
                }
            ),
            onClick = actions::onSave,
            icon = R.drawable.ic_check,
            enabled = !form.loading,
        )
    }
}
