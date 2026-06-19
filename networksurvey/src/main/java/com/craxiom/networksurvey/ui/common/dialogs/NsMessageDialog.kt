package com.craxiom.networksurvey.ui.common.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.craxiom.networksurvey.ui.preview.NsPreview
import com.craxiom.networksurvey.ui.preview.PreviewDayNight

/**
 * A single-button informational dialog (title + message + one dismiss action). Use this for
 * "here is some information" or non-recoverable error messages where the only action is to
 * acknowledge and close.
 */
@Composable
fun NsMessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = stringResource(android.R.string.ok),
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    cancelable: Boolean = true,
) {
    NsMessageDialog(
        title = title,
        message = AnnotatedString(message),
        onDismiss = onDismiss,
        modifier = modifier,
        confirmText = confirmText,
        icon = icon,
        iconTint = iconTint,
        cancelable = cancelable,
    )
}

/**
 * [AnnotatedString] variant of [NsMessageDialog] for messages that carry inline styling or links
 * (for example resources resolved with `annotatedStringResource`).
 */
@Composable
fun NsMessageDialog(
    title: String,
    message: AnnotatedString,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = stringResource(android.R.string.ok),
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    cancelable: Boolean = true,
) {
    NsAlertDialogScaffold(
        title = title,
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = icon,
        iconTint = iconTint,
        cancelable = cancelable,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = confirmText)
            }
        },
        body = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    )
}

@PreviewDayNight
@Composable
private fun NsMessageDialogPreview() {
    NsPreview {
        NsMessageDialog(
            title = "Network information",
            message = "This is an informational message shown to the user inside a consistent " +
                "Material 3 dialog.",
            onDismiss = {},
            icon = Icons.Outlined.Info,
        )
    }
}
