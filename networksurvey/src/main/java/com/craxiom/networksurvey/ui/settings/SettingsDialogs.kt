package com.craxiom.networksurvey.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.dialogs.NsAlertDialogScaffold
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.common.dialogs.NsInputDialog
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.common.percentText
import kotlin.math.roundToInt

private const val BATTERY_THRESHOLD_STEP = 5
private val SWATCH_SIZE = 24.dp
private val SWATCH_BORDER = 1.dp

/** A whole number typed on the number pad, with the setting's explanation above the field. */
@Composable
fun SettingsNumberDialog(title: String, message: String?, initialValue: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val invalid = stringResource(R.string.settings_whole_number)
    NsInputDialog(
        title = title,
        message = message,
        initialValue = initialValue.toString(),
        keyboardType = KeyboardType.Number,
        onConfirm = { text -> text.trim().toIntOrNull()?.let(onConfirm) },
        onDismiss = onDismiss,
        validate = { text -> if (text.isBlank() || text.trim().toIntOrNull()?.takeIf { it >= 0 } != null) null else invalid },
    )
}

/** The questions a streaming queue limit change asks; neither closes on a tap outside. */
@Composable
fun QueueLimitPromptDialog(prompt: SettingsPrompt, viewModel: SettingsViewModel) {
    when (prompt) {
        is SettingsPrompt.DisableQueueLimit -> NsConfirmationDialog(
            title = stringResource(R.string.disable_queue_limit_title),
            message = stringResource(R.string.disable_queue_limit_message),
            confirmText = stringResource(R.string.disable_queue_limit_confirm),
            onConfirm = viewModel::keepQueueLimit,
            onDismiss = {},
            dismissText = stringResource(R.string.cancel),
            onCancel = { viewModel.revertQueueLimit(prompt.previous) },
            cancelable = false,
            icon = Icons.Outlined.Warning,
        )

        SettingsPrompt.HighQueueLimit -> NsConfirmationDialog(
            title = stringResource(R.string.high_queue_limit_title),
            message = stringResource(R.string.high_queue_limit_message),
            confirmText = stringResource(R.string.high_queue_limit_use_recommended),
            onConfirm = viewModel::useRecommendedQueueLimit,
            onDismiss = {},
            dismissText = stringResource(R.string.high_queue_limit_keep_value),
            onCancel = viewModel::keepQueueLimit,
            cancelable = false,
            icon = Icons.Outlined.Warning,
        )
    }
}

/** The battery threshold slider, 0 (never pause) to 95 percent in steps of 5. */
@Composable
fun BatteryThresholdDialog(title: String, initialValue: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var sliderValue by remember { mutableFloatStateOf(initialValue.toFloat()) }
    val value = sliderValue.roundToInt()
    val max = SettingsViewModel.BATTERY_THRESHOLD_MAX

    NsAlertDialogScaffold(
        title = title,
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(value)
                onDismiss()
            }) { Text(text = stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) } },
        body = {
            Text(
                text = if (value == 0) stringResource(R.string.battery_management_disabled) else percentText(value),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(NsTheme.spacing.s))
            Text(
                text = if (value == 0) {
                    stringResource(R.string.battery_management_disabled_description)
                } else {
                    stringResource(R.string.battery_pause_active_description, value)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(NsTheme.spacing.l))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = 0f..max.toFloat(),
                steps = (max / BATTERY_THRESHOLD_STEP) - 1,
            )
        },
    )
}

/** One choice per color with its swatch; a tap applies and closes. */
@Composable
fun ColorChoiceDialog(title: String, names: List<String>, colors: List<Color>, selectedIndex: Int, onSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    NsAlertDialogScaffold(
        title = title,
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) } },
        body = {
            Column(Modifier.selectableGroup()) {
                names.forEachIndexed { index, name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = NsTheme.spacing.touchTarget)
                            .selectable(selected = index == selectedIndex, role = Role.RadioButton) {
                                onSelected(index)
                                onDismiss()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = null)
                        Spacer(
                            modifier = Modifier
                                .size(SWATCH_SIZE)
                                .clip(CircleShape)
                                .background(colors.getOrElse(index) { Color.Transparent })
                                .border(SWATCH_BORDER, MaterialTheme.colorScheme.outline, CircleShape),
                        )
                        Text(text = name)
                    }
                }
            }
        },
    )
}

/** Asks before every survey point is deleted. */
@Composable
fun ClearSurveyPointsDialog(onClear: () -> Unit, onDismiss: () -> Unit) {
    NsConfirmationDialog(
        title = stringResource(R.string.clear_survey_points_confirm_title),
        message = stringResource(R.string.clear_survey_points_confirm_message),
        confirmText = stringResource(R.string.clear_survey_points_confirm_button),
        onConfirm = onClear,
        onDismiss = onDismiss,
        destructive = true,
    )
}

/** Asks before the records waiting to be uploaded are deleted. */
@Composable
fun DeleteUploadDataDialog(onDelete: () -> Unit, onDismiss: () -> Unit) {
    NsConfirmationDialog(
        title = stringResource(R.string.delete_upload_data_confirm_title),
        message = stringResource(R.string.delete_upload_data_confirm_message),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = onDelete,
        onDismiss = onDismiss,
        destructive = true,
    )
}
