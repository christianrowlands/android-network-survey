package com.craxiom.networksurvey.ui.common.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.preview.NsPreview
import com.craxiom.networksurvey.ui.preview.PreviewDayNight

/**
 * A text-input dialog with a single [OutlinedTextField] and optional validation.
 *
 * The text value is held internally; [validate] receives the current text and returns an error
 * message (or null when valid). The confirm button is disabled while the value is invalid, so the
 * caller's [onConfirm] only ever receives a valid value.
 *
 * @param onConfirm invoked with the entered text when the user confirms a valid value.
 * @param label optional floating label for the text field.
 * @param allowBlankConfirm when false (the default) the confirm button is disabled while the field
 * is blank, without showing an error.
 * @param validate optional validator returning an error message, or null when the value is valid.
 */
@Composable
fun NsInputDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialValue: String = "",
    label: String? = null,
    singleLine: Boolean = true,
    confirmText: String = stringResource(android.R.string.ok),
    dismissText: String = stringResource(R.string.cancel),
    allowBlankConfirm: Boolean = false,
    validate: ((String) -> String?)? = null,
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    val errorMessage = validate?.invoke(value)
    val isValid = errorMessage == null && (allowBlankConfirm || value.isNotBlank())

    NsAlertDialogScaffold(
        title = title,
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onConfirm(value)
                    onDismiss()
                },
            ) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissText)
            }
        },
        body = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = label?.let { { Text(text = it) } },
                singleLine = singleLine,
                isError = errorMessage != null,
                supportingText = errorMessage?.let { { Text(text = it) } },
            )
        },
    )
}

@PreviewDayNight
@Composable
private fun NsInputDialogPreview() {
    NsPreview {
        NsInputDialog(
            title = "Add SSID",
            onConfirm = {},
            onDismiss = {},
            label = "SSID",
        )
    }
}
