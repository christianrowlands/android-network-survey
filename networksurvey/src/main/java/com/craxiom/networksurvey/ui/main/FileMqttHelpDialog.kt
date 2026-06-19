package com.craxiom.networksurvey.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.HelpItem
import com.craxiom.networksurvey.ui.common.HelpSection
import com.craxiom.networksurvey.ui.preview.NsPreview
import com.craxiom.networksurvey.ui.preview.PreviewDayNight

/**
 * Material 3 help dialog explaining the difference between file logging and MQTT streaming,
 * and how they work independently.
 */
@Composable
fun FileMqttHelpDialog(
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.file_help_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                HelpSection(title = stringResource(R.string.file_mqtt_help_file_title)) {
                    HelpItem(
                        stringResource(R.string.file_mqtt_help_file_local_label),
                        stringResource(R.string.file_mqtt_help_file_local_desc)
                    )
                    HelpItem(
                        stringResource(R.string.file_mqtt_help_file_toggle_label),
                        stringResource(R.string.file_mqtt_help_file_toggle_desc)
                    )
                    HelpItem(
                        stringResource(R.string.file_mqtt_help_file_format_label),
                        stringResource(R.string.file_mqtt_help_file_format_desc)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.file_mqtt_help_mqtt_title)) {
                    HelpItem(
                        stringResource(R.string.file_mqtt_help_mqtt_realtime_label),
                        stringResource(R.string.file_mqtt_help_mqtt_realtime_desc)
                    )
                    HelpItem(
                        stringResource(R.string.file_mqtt_help_mqtt_broker_label),
                        stringResource(R.string.file_mqtt_help_mqtt_broker_desc)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.file_mqtt_help_key_point_title)) {
                    HelpItem(
                        stringResource(R.string.file_mqtt_help_key_independent_label),
                        stringResource(R.string.file_mqtt_help_key_independent_desc)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.file_mqtt_help_dismiss))
            }
        }
    )
}

@PreviewDayNight
@Composable
private fun FileMqttHelpDialogPreview() {
    NsPreview {
        FileMqttHelpDialog(onDismissRequest = {})
    }
}
