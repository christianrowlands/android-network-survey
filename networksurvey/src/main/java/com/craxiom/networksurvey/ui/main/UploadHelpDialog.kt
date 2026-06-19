package com.craxiom.networksurvey.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.HelpItem
import com.craxiom.networksurvey.ui.common.HelpSection
import com.craxiom.networksurvey.ui.preview.NsPreview
import com.craxiom.networksurvey.ui.preview.PreviewDayNight

private const val MANUAL_URL = "https://networksurvey.app/manual#data-upload"

/**
 * Material 3 help dialog explaining the data upload feature, where data goes,
 * what is uploaded, and when records are written.
 */
@Composable
fun UploadHelpDialog(
    onDismissRequest: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.upload_help_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.upload_help_overview),
                )

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.upload_help_how_title)) {
                    HelpItem(
                        stringResource(R.string.upload_help_how_started_label),
                        stringResource(R.string.upload_help_how_started_desc)
                    )
                    HelpItem(
                        stringResource(R.string.upload_help_how_stopped_label),
                        stringResource(R.string.upload_help_how_stopped_desc)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.upload_help_data_destination_title)) {
                    HelpItem(
                        stringResource(R.string.upload_help_data_opencellid_label),
                        stringResource(R.string.upload_help_data_opencellid_desc)
                    )
                    HelpItem(
                        stringResource(R.string.upload_help_data_beacondb_label),
                        stringResource(R.string.upload_help_data_beacondb_desc)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.upload_help_what_title)) {
                    HelpItem(
                        stringResource(R.string.upload_help_what_location_label),
                        stringResource(R.string.upload_help_what_location_desc)
                    )
                    HelpItem(
                        stringResource(R.string.upload_help_what_cellular_label),
                        stringResource(R.string.upload_help_what_cellular_desc)
                    )
                    HelpItem(
                        stringResource(R.string.upload_help_what_wifi_label),
                        stringResource(R.string.upload_help_what_wifi_desc)
                    )
                    HelpItem(
                        stringResource(R.string.upload_help_what_timestamp_label),
                        stringResource(R.string.upload_help_what_timestamp_desc)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.upload_help_when_title)) {
                    HelpItem(
                        stringResource(R.string.upload_help_when_distance_label),
                        stringResource(R.string.upload_help_when_distance_desc)
                    )
                    HelpItem(
                        stringResource(R.string.upload_help_when_filter_label),
                        stringResource(R.string.upload_help_when_filter_desc)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.upload_help_privacy_note),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { uriHandler.openUri(MANUAL_URL) }) {
                Text(text = stringResource(R.string.view_manual))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.upload_help_dismiss))
            }
        }
    )
}

@PreviewDayNight
@Composable
private fun UploadHelpDialogPreview() {
    NsPreview {
        UploadHelpDialog(onDismissRequest = {})
    }
}
