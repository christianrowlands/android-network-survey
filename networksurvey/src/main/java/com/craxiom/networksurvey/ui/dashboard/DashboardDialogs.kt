package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R

/**
 * Dialog warning about disabling the streaming queue limit.
 */
@Composable
fun DisableQueueLimitDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.disable_queue_limit_title)) },
        text = { Text(text = stringResource(R.string.disable_queue_limit_message)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(text = stringResource(R.string.disable_queue_limit_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * Dialog explaining Bluetooth permission requirements.
 */
@Composable
fun BluetoothPermissionRationaleDialog(
    onRequestPermissions: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.bluetooth_permissions_rationale_title)) },
        text = { Text(text = stringResource(R.string.bluetooth_permissions_rationale)) },
        confirmButton = {
            TextButton(onClick = {
                onRequestPermissions()
                onDismiss()
            }) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

/**
 * Dialog explaining required CDR permissions.
 */
@Composable
fun CdrRequiredPermissionDialog(
    onRequestPermissions: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.cdr_required_permissions_rationale_title)) },
        text = { Text(text = stringResource(R.string.cdr_required_permissions_rationale)) },
        confirmButton = {
            TextButton(onClick = {
                onRequestPermissions()
                onDismiss()
            }) {
                Text(text = stringResource(R.string.request))
            }
        },
    )
}

/**
 * Dialog explaining optional CDR permissions with an option to ignore.
 */
@Composable
fun CdrOptionalPermissionDialog(
    onRequestPermissions: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.cdr_optional_permissions_rationale_title)) },
        text = { Text(text = stringResource(R.string.cdr_optional_permissions_rationale)) },
        confirmButton = {
            TextButton(onClick = {
                onRequestPermissions()
                onDismiss()
            }) {
                Text(text = stringResource(R.string.request))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onIgnore()
                onDismiss()
            }) {
                Text(text = stringResource(R.string.ignore))
            }
        },
    )
}
