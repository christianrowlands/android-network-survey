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
 * Material 3 help dialog explaining NS Analytics cloud-based storage and analysis,
 * what happens when connected, and how to get started.
 */
@Composable
fun NsAnalyticsHelpDialog(
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.ns_analytics_help_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.ns_analytics_help_overview),
                )

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.ns_analytics_help_connected_title)) {
                    HelpItem(
                        stringResource(R.string.ns_analytics_help_connected_upload_label),
                        stringResource(R.string.ns_analytics_help_connected_upload_desc)
                    )
                    HelpItem(
                        stringResource(R.string.ns_analytics_help_connected_types_label),
                        stringResource(R.string.ns_analytics_help_connected_types_desc)
                    )
                    HelpItem(
                        stringResource(R.string.ns_analytics_help_connected_control_label),
                        stringResource(R.string.ns_analytics_help_connected_control_desc)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.ns_analytics_help_getting_started_title)) {
                    HelpItem(
                        stringResource(R.string.ns_analytics_help_getting_started_configure_label),
                        stringResource(R.string.ns_analytics_help_getting_started_configure_desc)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.ns_analytics_help_dismiss))
            }
        }
    )
}

@PreviewDayNight
@Composable
private fun NsAnalyticsHelpDialogPreview() {
    NsPreview {
        NsAnalyticsHelpDialog(onDismissRequest = {})
    }
}
