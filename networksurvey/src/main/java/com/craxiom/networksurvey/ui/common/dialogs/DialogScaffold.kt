package com.craxiom.networksurvey.ui.common.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.DialogProperties

/**
 * The single source of truth for dialog styling across the app.
 *
 * Every shared dialog is built on top of this scaffold, so they all get the same title typography,
 * the same scrollable body, the same optional leading icon, and the same cancelable behavior. This
 * is what keeps every dialog in the app visually consistent and prevents the styling drift that
 * happens when each dialog hand-rolls its own Material3 [AlertDialog].
 *
 * @param title the dialog title, rendered with [MaterialTheme.typography] headlineSmall.
 * @param onDismissRequest invoked when the user dismisses the dialog (back press / outside tap when
 * cancelable, or a button calling it).
 * @param confirmButton the primary action button slot (placed on the right).
 * @param dismissButton the optional secondary action button slot (placed on the left).
 * @param icon an optional leading icon shown above the title.
 * @param iconTint the tint for [icon]; [Color.Unspecified] uses the default content color.
 * @param cancelable when false, the dialog cannot be dismissed by back press or outside tap.
 * @param body the dialog content, already wrapped in a scrollable [Column].
 */
@Composable
internal fun NsAlertDialogScaffold(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    cancelable: Boolean = true,
    body: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        icon = icon?.let { vector ->
            {
                if (iconTint == Color.Unspecified) {
                    Icon(imageVector = vector, contentDescription = null)
                } else {
                    Icon(imageVector = vector, contentDescription = null, tint = iconTint)
                }
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                content = body,
            )
        },
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        properties = if (cancelable) {
            DialogProperties()
        } else {
            DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        },
    )
}

/**
 * A checkbox with a text label, used by dialogs that need inline boolean options (for example a
 * "don't show again" toggle). Shared so every dialog renders these the same way.
 */
@Composable
internal fun LabeledCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
