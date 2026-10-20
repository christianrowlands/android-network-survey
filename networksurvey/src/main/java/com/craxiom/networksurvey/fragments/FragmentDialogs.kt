@file:JvmName("FragmentDialogs")

package com.craxiom.networksurvey.fragments

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.dialogs.ComposeDialogFragment
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.common.dialogs.NsMessageDialog
import com.craxiom.networksurvey.ui.common.styledCharSequenceToAnnotatedString

/**
 * Java-callable entry points that show the app's shared Compose dialogs from the legacy
 * Fragment-based screens. Each function bridges into a [ComposeDialogFragment] so the Java code
 * stays a single call and all dialog styling lives in the shared Compose components.
 */

private const val TAG_QR_TOO_LARGE = "QrCodeTooLarge"

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

