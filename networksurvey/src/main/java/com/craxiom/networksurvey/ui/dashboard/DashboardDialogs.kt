package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.ui.common.dialogs.LabeledCheckbox
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.common.dialogs.NsMessageDialog
import com.craxiom.networksurvey.util.PreferenceUtils

/**
 * Dialog warning about disabling the streaming queue limit.
 */
@Composable
fun DisableQueueLimitDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    NsConfirmationDialog(
        title = stringResource(R.string.disable_queue_limit_title),
        message = stringResource(R.string.disable_queue_limit_message),
        confirmText = stringResource(R.string.disable_queue_limit_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
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
    NsConfirmationDialog(
        title = stringResource(R.string.bluetooth_permissions_rationale_title),
        message = stringResource(R.string.bluetooth_permissions_rationale),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = onRequestPermissions,
        onDismiss = onDismiss,
        dismissText = null,
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
    NsConfirmationDialog(
        title = stringResource(R.string.cdr_required_permissions_rationale_title),
        message = stringResource(R.string.cdr_required_permissions_rationale),
        confirmText = stringResource(R.string.request),
        onConfirm = onRequestPermissions,
        onDismiss = onDismiss,
        dismissText = null,
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
    NsConfirmationDialog(
        title = stringResource(R.string.cdr_optional_permissions_rationale_title),
        message = stringResource(R.string.cdr_optional_permissions_rationale),
        confirmText = stringResource(R.string.request),
        onConfirm = onRequestPermissions,
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.ignore),
        onCancel = onIgnore,
    )
}

/**
 * Dialog for configuring community database upload options before starting the upload.
 * Allows the user to select OpenCelliD, BeaconDB, anonymous mode, retry, and a
 * "don't show again" option.
 */
@Composable
fun UploadConfirmationDialog(
    onUpload: (uploadToOcid: Boolean, anonymously: Boolean, uploadToBeaconDb: Boolean, retry: Boolean, dontShowAgain: Boolean) -> Unit,
    onNavigateToUploadSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var uploadToOcid by remember {
        mutableStateOf(
            preferences.getBoolean(
                NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID,
                NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID,
            )
        )
    }
    var anonymously by remember {
        mutableStateOf(
            preferences.getBoolean(
                NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD,
                NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID,
            )
        )
    }
    var uploadToBeaconDb by remember {
        mutableStateOf(
            preferences.getBoolean(
                NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB,
                NetworkSurveyConstants.DEFAULT_UPLOAD_TO_BEACONDB,
            )
        )
    }
    var retry by remember {
        mutableStateOf(
            preferences.getBoolean(
                NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED,
                NetworkSurveyConstants.DEFAULT_UPLOAD_RETRY_ENABLED,
            )
        )
    }
    var dontShowAgain by remember { mutableStateOf(false) }

    val showApiKeyWarning = uploadToOcid && !anonymously &&
            !PreferenceUtils.isApiKeyValid(PreferenceUtils.getUserOcidApiKey(context))

    NsConfirmationDialog(
        title = stringResource(R.string.upload_survey_records),
        confirmText = stringResource(R.string.upload),
        onConfirm = {
            onUpload(uploadToOcid, anonymously, uploadToBeaconDb, retry, dontShowAgain)
        },
        onDismiss = onDismiss,
        neutralText = stringResource(R.string.preferences),
        onNeutral = onNavigateToUploadSettings,
        body = {
            Text(
                text = stringResource(R.string.upload_dialog_message),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // OpenCelliD checkbox
            LabeledCheckbox(
                checked = uploadToOcid,
                onCheckedChange = { uploadToOcid = it },
                label = stringResource(R.string.upload_to_opencellid),
            )

            // Anonymous checkbox (indented, enabled only when OCID is checked)
            LabeledCheckbox(
                checked = anonymously,
                onCheckedChange = { anonymously = it },
                label = stringResource(R.string.anonymously),
                enabled = uploadToOcid,
                modifier = Modifier.padding(start = 24.dp),
            )

            if (showApiKeyWarning) {
                Text(
                    text = stringResource(R.string.access_token_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 24.dp),
                )
            }

            // BeaconDB checkbox
            LabeledCheckbox(
                checked = uploadToBeaconDb,
                onCheckedChange = { uploadToBeaconDb = it },
                label = stringResource(R.string.upload_to_beacondb),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Retry checkbox
            LabeledCheckbox(
                checked = retry,
                onCheckedChange = { retry = it },
                label = stringResource(R.string.retry_on_failure),
            )

            // Don't show again checkbox
            LabeledCheckbox(
                checked = dontShowAgain,
                onCheckedChange = { dontShowAgain = it },
                label = stringResource(R.string.dont_show_again),
            )
        },
    )
}

/**
 * Dialog shown when no internet connection is available for upload.
 */
@Composable
fun NoInternetDialog(
    onDismiss: () -> Unit,
) {
    NsMessageDialog(
        title = stringResource(R.string.uploader_no_internet_title),
        message = stringResource(R.string.uploader_no_internet_message),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.ok),
    )
}
