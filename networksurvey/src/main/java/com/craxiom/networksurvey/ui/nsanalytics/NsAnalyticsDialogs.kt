package com.craxiom.networksurvey.ui.nsanalytics

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.common.dialogs.NsMessageDialog
import com.craxiom.networksurvey.ui.common.countText

/** The dialog the NS Analytics page is showing, if any, besides those its state drives. */
enum class NsAnalyticsDialog { NONE, DISCONNECT, CLEAR_QUEUE }

/** What the NS Analytics dialogs can ask for. */
interface NsAnalyticsDialogActions {
    fun onConfirmRegistration() {}
    fun onCancelRegistration() {}
    fun onRetryRegistration() {}
    fun onDismissRegistrationError() {}
    fun onDismissAlreadyRegistered() {}
    fun onDismissDeregistered() {}
    fun onScanAgain() {}
    fun onDismissQuota() {}
    fun onOpenUrl(url: String) {}
    fun onConfirmDisconnect() {}
    fun onConfirmClearQueue() {}
    fun onDismissDialog() {}
}

/**
 * Every dialog on the NS Analytics page: confirming a scanned workspace, a failed connection, a
 * code scanned while already connected, a phone disconnected from the web, the quota, and the
 * Disconnect and Clear queue confirmations.
 */
@Composable
fun NsAnalyticsDialogs(state: NsAnalyticsConnectionUiState, dialog: NsAnalyticsDialog, actions: NsAnalyticsDialogActions) {
    val workspace = state.workspaceName ?: stringResource(R.string.ns_analytics_unknown_workspace)
    val pending = state.pendingQrData
    when {
        pending != null && state.showRegistrationConfirmDialog -> NsConfirmationDialog(
            title = stringResource(
                R.string.ns_analytics_confirm_title,
                pending.workspaceName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.ns_analytics_unknown_workspace),
            ),
            message = stringResource(R.string.ns_analytics_confirm_body),
            confirmText = stringResource(R.string.ns_analytics_confirm),
            onConfirm = actions::onConfirmRegistration,
            onDismiss = actions::onCancelRegistration,
        )

        state.registrationError != null -> {
            val error = state.registrationError
            val message = if (error is RegistrationError.Other && !error.message.isNullOrBlank()) {
                error.message
            } else {
                stringResource(error.messageRes)
            }
            NsConfirmationDialog(
                title = stringResource(R.string.ns_analytics_error_title),
                message = message,
                confirmText = stringResource(R.string.ns_analytics_try_again),
                onConfirm = actions::onRetryRegistration,
                onDismiss = actions::onDismissRegistrationError,
            )
        }

        pending != null && state.showAlreadyRegisteredDialog -> NsMessageDialog(
            title = stringResource(R.string.ns_analytics_qr_detected_title),
            message = listOfNotNull(
                stringResource(R.string.ns_analytics_already_registered_message, workspace),
                pending.workspaceName?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.ns_analytics_new_workspace_detected, it) },
                stringResource(R.string.ns_analytics_already_connected_hint),
            ).joinToString(PARAGRAPH),
            onDismiss = actions::onDismissAlreadyRegistered,
        )

        state.deregistrationInfo != null -> NsConfirmationDialog(
            title = stringResource(R.string.ns_analytics_disconnected_title),
            message = if (state.deregistrationInfo.source == SOURCE_WEB) {
                stringResource(R.string.ns_analytics_disconnected_from_web)
            } else {
                stringResource(R.string.ns_analytics_disconnected_body, workspace)
            },
            confirmText = stringResource(R.string.ns_analytics_scan_again),
            onConfirm = actions::onScanAgain,
            onDismiss = actions::onDismissDeregistered,
            dismissText = stringResource(R.string.ok),
        )

        state.showQuotaExceededDialog -> {
            val url = state.quotaWebUrl?.takeIf { it.isNotBlank() }
            NsConfirmationDialog(
                title = stringResource(R.string.ns_analytics_quota_title),
                message = state.quotaMessage?.takeIf { it.isNotBlank() } ?: stringResource(R.string.ns_analytics_quota_body),
                confirmText = stringResource(if (url != null) R.string.ns_analytics_manage_subscription else R.string.ok),
                onConfirm = { if (url != null) actions.onOpenUrl(url) },
                onDismiss = actions::onDismissQuota,
                dismissText = if (url != null) stringResource(R.string.cancel) else null,
            )
        }

        dialog == NsAnalyticsDialog.DISCONNECT -> NsConfirmationDialog(
            title = stringResource(R.string.ns_analytics_disconnect_title),
            message = stringResource(R.string.ns_analytics_disconnect_body, workspace),
            confirmText = stringResource(R.string.ns_analytics_disconnect_action),
            destructive = true,
            onConfirm = actions::onConfirmDisconnect,
            onDismiss = actions::onDismissDialog,
        )

        dialog == NsAnalyticsDialog.CLEAR_QUEUE -> NsConfirmationDialog(
            title = stringResource(R.string.ns_analytics_clear_queue),
            message = countText(R.plurals.ns_analytics_clear_queue_confirm, state.queuedRecords),
            confirmText = stringResource(R.string.ns_analytics_clear_queue_action),
            destructive = true,
            onConfirm = actions::onConfirmClearQueue,
            onDismiss = actions::onDismissDialog,
        )
    }
}

private const val PARAGRAPH = "\n\n"

/** The deregistration source when someone disconnected the phone on the NS Analytics website. */
private const val SOURCE_WEB = "web"
