package com.craxiom.networksurvey.ui.common.dialogs

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.preview.NsPreview
import com.craxiom.networksurvey.ui.preview.PreviewDayNight

/**
 * A confirm/cancel dialog with an optional third (neutral) action.
 *
 * The confirm action is on the right; the dismiss (and optional neutral) actions are on the left.
 * When [destructive] is true the confirm button is rendered in the error color (and an unspecified
 * [iconTint] also resolves to the error color), which is the standard treatment for irreversible
 * actions such as deleting data.
 *
 * Each action lambda runs and then automatically dismisses the dialog, so callers never need to
 * remember to also flip their visibility state inside the action.
 *
 * @param message the body text; omit and provide [body] instead for richer content (checkboxes,
 * grouped lists, etc.).
 * @param dismissText the cancel button label; pass null to omit the cancel button entirely (useful
 * for rationale dialogs whose only choice is to proceed).
 * @param onCancel optional action run only when the cancel button is tapped (not on confirm). Use
 * this when cancelling has a side effect, such as reverting a setting.
 * @param neutralText optional third action label (for example "Preferences" or "Ignore").
 * @param body optional custom body rendered below [message]; receives a scrollable column scope.
 */
@Composable
fun NsConfirmationDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissText: String? = stringResource(R.string.cancel),
    onCancel: (() -> Unit)? = null,
    destructive: Boolean = false,
    neutralText: String? = null,
    onNeutral: (() -> Unit)? = null,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    cancelable: Boolean = true,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val resolvedIconTint = if (iconTint == Color.Unspecified && destructive) {
        MaterialTheme.colorScheme.error
    } else {
        iconTint
    }

    NsAlertDialogScaffold(
        title = title,
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = icon,
        iconTint = resolvedIconTint,
        cancelable = cancelable,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(text = confirmText)
            }
        },
        dismissButton = if (dismissText == null && (neutralText == null || onNeutral == null)) {
            null
        } else {
            {
                Row {
                    if (neutralText != null && onNeutral != null) {
                        TextButton(onClick = {
                            onNeutral()
                            onDismiss()
                        }) {
                            Text(text = neutralText)
                        }
                    }
                    if (dismissText != null) {
                        TextButton(onClick = {
                            onCancel?.invoke()
                            onDismiss()
                        }) {
                            Text(text = dismissText)
                        }
                    }
                }
            }
        },
        body = {
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            body?.invoke(this)
        },
    )
}

@PreviewDayNight
@Composable
private fun NsConfirmationDialogPreview() {
    NsPreview {
        NsConfirmationDialog(
            title = "Disable queue limit?",
            message = "Disabling the streaming queue limit can use a large amount of memory if the " +
                "connection is slow.",
            confirmText = stringResource(android.R.string.ok),
            onConfirm = {},
            onDismiss = {},
            icon = Icons.Outlined.Warning,
        )
    }
}

@PreviewDayNight
@Composable
private fun NsConfirmationDialogDestructivePreview() {
    NsPreview {
        NsConfirmationDialog(
            title = "Delete uploaded data?",
            message = "This permanently deletes the record of which data has already been uploaded.",
            confirmText = stringResource(android.R.string.ok),
            onConfirm = {},
            onDismiss = {},
            destructive = true,
        )
    }
}
