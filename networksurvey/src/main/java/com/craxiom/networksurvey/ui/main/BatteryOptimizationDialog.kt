package com.craxiom.networksurvey.ui.main

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.util.BatteryOptimizationHelper

/**
 * Dialog that prompts users to disable battery optimization.
 *
 * This dialog explains the issue, provides manufacturer-specific instructions, and allows
 * users to opt out of future prompts.
 */
@Composable
fun BatteryOptimizationDialog(
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit,
    batteryOptimizationHelper: BatteryOptimizationHelper
) {
    val context = LocalContext.current
    var dontShowAgain by remember { mutableStateOf(false) }
    val manufacturerInstructions =
        remember { batteryOptimizationHelper.getManufacturerInstructions() }

    AlertDialog(
        onDismissRequest = {
            if (dontShowAgain) {
                batteryOptimizationHelper.setDontShowAgain(true)
            }
            onDismiss()
        },
        title = {
            Text(
                text = context.getString(R.string.battery_optimization_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = context.getString(R.string.battery_optimization_message),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = context.getString(R.string.battery_optimization_recommendation),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (manufacturerInstructions != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = context.getString(R.string.battery_optimization_device_specific),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = manufacturerInstructions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Text(
                        text = context.getString(R.string.battery_optimization_dont_show_again),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (dontShowAgain) {
                        batteryOptimizationHelper.setDontShowAgain(true)
                    }
                    batteryOptimizationHelper.recordPromptShown()
                    onGoToSettings()
                }
            ) {
                Text(context.getString(R.string.battery_optimization_go_to_settings))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (dontShowAgain) {
                        batteryOptimizationHelper.setDontShowAgain(true)
                    }
                    onDismiss()
                }
            ) {
                Text(context.getString(R.string.battery_optimization_later))
            }
        }
    )
}
