package com.craxiom.networksurvey.ui.common.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.preview.NsPreview
import com.craxiom.networksurvey.ui.preview.PreviewDayNight

/**
 * A single-choice (radio) selection dialog.
 *
 * Two behaviors are supported:
 * - Default: the user picks an option then taps the confirm ("Save") button to commit.
 * - [immediateApply]: tapping an option commits and closes immediately (no Save button), matching
 *   the snappy "pick and go" behavior of sort dialogs.
 */
@Composable
fun NsSingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    immediateApply: Boolean = false,
    confirmText: String = stringResource(R.string.save),
    dismissText: String = stringResource(R.string.cancel),
) {
    var selected by remember(selectedIndex) { mutableStateOf(selectedIndex) }

    NsAlertDialogScaffold(
        title = title,
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = {
            if (immediateApply) {
                TextButton(onClick = onDismiss) {
                    Text(text = dismissText)
                }
            } else {
                TextButton(onClick = {
                    onConfirm(selected)
                    onDismiss()
                }) {
                    Text(text = confirmText)
                }
            }
        },
        dismissButton = if (immediateApply) {
            null
        } else {
            {
                TextButton(onClick = onDismiss) {
                    Text(text = dismissText)
                }
            }
        },
        body = {
            options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = index == selected,
                            onClick = {
                                selected = index
                                if (immediateApply) {
                                    onConfirm(index)
                                    onDismiss()
                                }
                            },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = index == selected,
                        onClick = {
                            selected = index
                            if (immediateApply) {
                                onConfirm(index)
                                onDismiss()
                            }
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = option)
                }
            }
        },
    )
}

/**
 * A multi-choice (checkbox) selection dialog. The user toggles any number of options and commits
 * the whole set with the confirm button.
 */
@Composable
fun NsMultiChoiceDialog(
    title: String,
    options: List<String>,
    initialChecked: List<Boolean>,
    onConfirm: (List<Boolean>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = stringResource(R.string.save),
    dismissText: String = stringResource(R.string.cancel),
) {
    val checked = remember(initialChecked) {
        mutableStateListOf<Boolean>().apply { addAll(initialChecked) }
    }

    NsAlertDialogScaffold(
        title = title,
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(checked.toList())
                onDismiss()
            }) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissText)
            }
        },
        body = {
            options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            checked[index] = !(checked.getOrNull(index) ?: false)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked.getOrNull(index) ?: false,
                        onCheckedChange = { checked[index] = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = option)
                }
            }
        },
    )
}

@PreviewDayNight
@Composable
private fun NsSingleChoiceDialogPreview() {
    NsPreview {
        NsSingleChoiceDialog(
            title = "Sort by",
            options = listOf("Name", "Signal", "Channel"),
            selectedIndex = 1,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@PreviewDayNight
@Composable
private fun NsMultiChoiceDialogPreview() {
    NsPreview {
        NsMultiChoiceDialog(
            title = "Filter",
            options = listOf("GPS", "GLONASS", "Galileo", "BeiDou"),
            initialChecked = listOf(true, true, false, true),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
