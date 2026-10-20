package com.craxiom.networksurvey.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.BatteryOptimizationHelper

/**
 * Dialog that prompts users to disable battery optimization, on the shared confirmation dialog.
 *
 * This dialog explains the issue, provides manufacturer-specific instructions, and allows
 * users to opt out of future prompts. Later and Go to Settings both answer it (and start the next
 * 24 hour quiet period); Back or a tap outside only closes it.
 *
 * @param onLater          Later was chosen.
 * @param onGoToSettings   Go to Settings was chosen.
 * @param onDismiss        The dialog was closed without an answer (Back or outside).
 */
@Composable
fun BatteryOptimizationDialog(
    onLater: () -> Unit,
    onGoToSettings: () -> Unit,
    onDismiss: () -> Unit,
    batteryOptimizationHelper: BatteryOptimizationHelper
) {
    var dontShowAgain by remember { mutableStateOf(false) }
    // The shared dialog closes itself after either button; only a close without an answer is a dismissal
    var answered by remember { mutableStateOf(false) }
    val manufacturerInstructions = remember { batteryOptimizationHelper.getManufacturerInstructions() }

    fun answer(choice: () -> Unit) {
        answered = true
        if (dontShowAgain) batteryOptimizationHelper.setDontShowAgain(true)
        // Either answer starts the 24 hour quiet period, so the next Start is not stopped again
        batteryOptimizationHelper.recordPromptShown()
        choice()
    }

    NsConfirmationDialog(
        title = stringResource(R.string.battery_optimization_title),
        confirmText = stringResource(R.string.battery_optimization_go_to_settings),
        onConfirm = { answer(onGoToSettings) },
        dismissText = stringResource(R.string.battery_optimization_later),
        onCancel = { answer(onLater) },
        onDismiss = {
            if (!answered) {
                if (dontShowAgain) batteryOptimizationHelper.setDontShowAgain(true)
                onDismiss()
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m)) {
            Text(text = stringResource(R.string.battery_optimization_message), style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(R.string.battery_optimization_recommendation), style = MaterialTheme.typography.bodyMedium)
            if (manufacturerInstructions != null) {
                Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs)) {
                    Text(
                        text = stringResource(R.string.battery_optimization_device_specific),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(manufacturerInstructions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // The whole row toggles, so the target is the label too, not only the box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = NsTheme.spacing.touchTarget)
                    .toggleable(value = dontShowAgain, role = Role.Checkbox, onValueChange = { dontShowAgain = it }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
            ) {
                Checkbox(checked = dontShowAgain, onCheckedChange = null)
                Text(
                    text = stringResource(R.string.battery_optimization_dont_show_again),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
