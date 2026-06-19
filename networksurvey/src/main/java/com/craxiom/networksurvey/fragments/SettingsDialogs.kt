@file:JvmName("SettingsDialogs")

package com.craxiom.networksurvey.fragments

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.annotatedStringResource
import com.craxiom.networksurvey.ui.common.dialogs.ComposeDialogFragment
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog

/**
 * Java-callable entry points for the Settings screen dialogs (CDR permission rationales and the
 * streaming queue limit warnings), shown through the shared [ComposeDialogFragment] bridge.
 */

private const val TAG_CDR_REQUIRED = "CdrRequiredPermissions"
private const val TAG_CDR_OPTIONAL = "CdrOptionalPermissions"
private const val TAG_DISABLE_QUEUE_LIMIT = "DisableQueueLimit"
private const val TAG_HIGH_QUEUE_LIMIT = "HighQueueLimit"

/**
 * Shows the rationale for the required CDR permissions, then requests them when the user proceeds.
 */
fun showCdrRequiredPermissionRationale(fragmentManager: FragmentManager, onRequest: Runnable) {
    ComposeDialogFragment.show(fragmentManager, TAG_CDR_REQUIRED) { dismiss ->
        NsConfirmationDialog(
            title = stringResource(R.string.cdr_required_permissions_rationale_title),
            confirmText = stringResource(R.string.request),
            onConfirm = { onRequest.run() },
            onDismiss = dismiss,
            dismissText = null,
            body = {
                Text(
                    text = annotatedStringResource(R.string.cdr_required_permissions_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
    }
}

/**
 * Shows the rationale for the optional CDR permission, offering to request it or continue without.
 */
fun showCdrOptionalPermissionRationale(fragmentManager: FragmentManager, onRequest: Runnable) {
    ComposeDialogFragment.show(fragmentManager, TAG_CDR_OPTIONAL) { dismiss ->
        NsConfirmationDialog(
            title = stringResource(R.string.cdr_optional_permissions_rationale_title),
            confirmText = stringResource(R.string.request),
            onConfirm = { onRequest.run() },
            onDismiss = dismiss,
            dismissText = stringResource(R.string.ignore),
            body = {
                Text(
                    text = annotatedStringResource(R.string.cdr_optional_permissions_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
    }
}

/**
 * Shows the non-cancelable warning when the user disables the streaming queue limit. Confirming
 * keeps the limit disabled; cancelling reverts to the previous value.
 */
fun showDisableQueueLimitWarning(
    fragmentManager: FragmentManager,
    onConfirm: Runnable,
    onRevert: Runnable,
) {
    ComposeDialogFragment.show(fragmentManager, TAG_DISABLE_QUEUE_LIMIT) { dismiss ->
        NsConfirmationDialog(
            title = stringResource(R.string.disable_queue_limit_title),
            message = stringResource(R.string.disable_queue_limit_message),
            confirmText = stringResource(R.string.disable_queue_limit_confirm),
            onConfirm = { onConfirm.run() },
            onDismiss = dismiss,
            dismissText = stringResource(R.string.cancel),
            onCancel = { onRevert.run() },
            cancelable = false,
            icon = Icons.Outlined.Warning,
        )
    }
}

/**
 * Shows the non-cancelable warning when the user sets a high streaming queue limit, offering to use
 * the recommended value or keep the high value.
 */
fun showHighQueueLimitWarning(
    fragmentManager: FragmentManager,
    onUseRecommended: Runnable,
    onKeepValue: Runnable,
) {
    ComposeDialogFragment.show(fragmentManager, TAG_HIGH_QUEUE_LIMIT) { dismiss ->
        NsConfirmationDialog(
            title = stringResource(R.string.high_queue_limit_title),
            message = stringResource(R.string.high_queue_limit_message),
            confirmText = stringResource(R.string.high_queue_limit_use_recommended),
            onConfirm = { onUseRecommended.run() },
            onDismiss = dismiss,
            dismissText = stringResource(R.string.high_queue_limit_keep_value),
            onCancel = { onKeepValue.run() },
            cancelable = false,
            icon = Icons.Outlined.Warning,
        )
    }
}
