package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.util.PreferenceUtils

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.upload_survey_records)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onUpload(uploadToOcid, anonymously, uploadToBeaconDb, retry, dontShowAgain)
                onDismiss()
            }) {
                Text(text = stringResource(R.string.upload))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onNavigateToUploadSettings()
                    onDismiss()
                }) {
                    Text(text = stringResource(R.string.preferences))
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        },
    )
}

/**
 * A checkbox with a clickable text label.
 */
@Composable
private fun LabeledCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

/**
 * Dialog shown when no internet connection is available for upload.
 */
@Composable
fun NoInternetDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.uploader_no_internet_title)) },
        text = { Text(text = stringResource(R.string.uploader_no_internet_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok))
            }
        },
    )
}
