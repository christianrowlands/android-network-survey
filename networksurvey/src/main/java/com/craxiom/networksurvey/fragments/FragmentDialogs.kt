@file:JvmName("FragmentDialogs")

package com.craxiom.networksurvey.fragments

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.dialogs.ComposeDialogFragment
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.common.dialogs.NsMessageDialog
import com.craxiom.networksurvey.ui.common.dialogs.NsSingleChoiceDialog
import com.craxiom.networksurvey.ui.common.styledCharSequenceToAnnotatedString
import java.util.function.IntConsumer

/**
 * Java-callable entry points that show the app's shared Compose dialogs from the legacy
 * Fragment-based screens. Each function bridges into a [ComposeDialogFragment] so the Java code
 * stays a single call and all dialog styling lives in the shared Compose components.
 */

private const val TAG_BLUETOOTH_PERMISSION = "BluetoothPermissionRationale"
private const val TAG_BLUETOOTH_SORT = "BluetoothSort"
private const val TAG_OVERRIDE_NETWORK_INFO = "OverrideNetworkInfo"
private const val TAG_CELLULAR_INFO = "CellularInfo"
private const val TAG_QR_TOO_LARGE = "QrCodeTooLarge"
private const val TAG_DELETE_UPLOAD_DATA = "DeleteUploadData"
private const val TAG_LOCATION_SERVICES_DISABLED = "LocationServicesDisabled"

/**
 * Shows an informational dialog explaining that location services are disabled, so the user's
 * location cannot be shown on the map.
 */
fun showLocationServicesDisabled(fragmentManager: FragmentManager) {
    ComposeDialogFragment.show(fragmentManager, TAG_LOCATION_SERVICES_DISABLED) { dismiss ->
        NsMessageDialog(
            title = stringResource(R.string.location_services_disabled_title),
            message = stringResource(R.string.location_services_disabled_message),
            onDismiss = dismiss,
            icon = Icons.Outlined.Info,
        )
    }
}

/**
 * Shows the Bluetooth permission rationale, then requests the permissions when the user proceeds.
 * Shared by the Bluetooth and MQTT screens, which both need Bluetooth permissions.
 */
fun showBluetoothPermissionRationale(fragmentManager: FragmentManager, onRequest: Runnable) {
    ComposeDialogFragment.show(fragmentManager, TAG_BLUETOOTH_PERMISSION) { dismiss ->
        NsConfirmationDialog(
            title = stringResource(R.string.bluetooth_permissions_rationale_title),
            message = stringResource(R.string.bluetooth_permissions_rationale),
            confirmText = stringResource(android.R.string.ok),
            onConfirm = { onRequest.run() },
            onDismiss = dismiss,
            dismissText = null,
        )
    }
}

/**
 * Shows the Bluetooth device sort dialog. The selection is applied immediately on tap.
 */
fun showBluetoothSortDialog(
    fragmentManager: FragmentManager,
    selectedIndex: Int,
    onSelected: IntConsumer,
) {
    ComposeDialogFragment.show(fragmentManager, TAG_BLUETOOTH_SORT) { dismiss ->
        NsSingleChoiceDialog(
            title = stringResource(R.string.menu_option_sort_by),
            options = stringArrayResource(R.array.bluetooth_sort_options).toList(),
            selectedIndex = selectedIndex,
            immediateApply = true,
            onConfirm = { index -> onSelected.accept(index) },
            onDismiss = dismiss,
        )
    }
}

/**
 * Shows the informational dialog explaining the override network type field.
 */
fun showOverrideNetworkInfo(fragmentManager: FragmentManager) {
    ComposeDialogFragment.show(fragmentManager, TAG_OVERRIDE_NETWORK_INFO) { dismiss ->
        NsMessageDialog(
            title = stringResource(R.string.override_network_info_title),
            message = stringResource(R.string.override_network_explanation),
            onDismiss = dismiss,
            icon = Icons.Outlined.Info,
        )
    }
}

/**
 * Shows the cellular terms information dialog. The title and styled body are resolved by the caller
 * (they vary by cellular protocol), so the body is passed through as a [CharSequence] and rendered
 * with its inline styling preserved.
 */
fun showCellularInfo(fragmentManager: FragmentManager, title: String, message: CharSequence) {
    ComposeDialogFragment.show(fragmentManager, TAG_CELLULAR_INFO) { dismiss ->
        NsMessageDialog(
            title = title,
            message = styledCharSequenceToAnnotatedString(message, MaterialTheme.colorScheme.primary),
            onDismiss = dismiss,
            icon = Icons.Outlined.Info,
        )
    }
}

/**
 * Shows the non-cancelable error dialog when MQTT connection settings are too large for a QR code.
 * The provided action runs when the user acknowledges the dialog (it navigates back to the
 * connection screen).
 */
fun showQrCodeTooLargeError(fragmentManager: FragmentManager, onAcknowledge: Runnable) {
    ComposeDialogFragment.show(fragmentManager, TAG_QR_TOO_LARGE) { dismiss ->
        NsMessageDialog(
            title = stringResource(R.string.qr_code_data_too_large_title),
            message = stringResource(R.string.qr_code_data_too_large_message),
            onDismiss = {
                onAcknowledge.run()
                dismiss()
            },
            icon = Icons.Outlined.Warning,
            iconTint = MaterialTheme.colorScheme.error,
            cancelable = false,
        )
    }
}

/**
 * Shows the destructive confirmation dialog before deleting the record of uploaded survey data.
 */
fun showDeleteUploadDataConfirmation(fragmentManager: FragmentManager, onDelete: Runnable) {
    ComposeDialogFragment.show(fragmentManager, TAG_DELETE_UPLOAD_DATA) { dismiss ->
        NsConfirmationDialog(
            title = stringResource(R.string.delete_upload_data_confirm_title),
            message = stringResource(R.string.delete_upload_data_confirm_message),
            confirmText = stringResource(android.R.string.ok),
            onConfirm = { onDelete.run() },
            onDismiss = dismiss,
            destructive = true,
        )
    }
}
