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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.HelpItem
import com.craxiom.networksurvey.ui.common.HelpSection
import com.craxiom.networksurvey.ui.preview.NsPreview
import com.craxiom.networksurvey.ui.preview.PreviewDayNight

private const val MANUAL_URL = "https://networksurvey.app/manual#cdr"

/**
 * Material 3 help dialog explaining Call Detail Records, the events logged,
 * and differences between Regular and CDR app variants.
 */
@Composable
fun CdrHelpDialog(
    onDismissRequest: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.cdr_help_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.cdr_help_overview),
                )

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.cdr_help_events_title)) {
                    HelpItem(
                        stringResource(R.string.cdr_help_event_calls_label),
                        stringResource(R.string.cdr_help_event_calls_desc)
                    )
                    HelpItem(
                        stringResource(R.string.cdr_help_event_sms_label),
                        stringResource(R.string.cdr_help_event_sms_desc)
                    )
                    HelpItem(
                        stringResource(R.string.cdr_help_event_tower_label),
                        stringResource(R.string.cdr_help_event_tower_desc)
                    )
                    HelpItem(
                        stringResource(R.string.cdr_help_event_rcs_label),
                        stringResource(R.string.cdr_help_event_rcs_desc)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.cdr_help_variants_title)) {
                    HelpItem(
                        stringResource(R.string.cdr_help_variant_regular_label),
                        stringResource(R.string.cdr_help_variant_regular_desc)
                    )
                    HelpItem(
                        stringResource(R.string.cdr_help_variant_cdr_label),
                        stringResource(R.string.cdr_help_variant_cdr_desc)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { uriHandler.openUri(MANUAL_URL) }) {
                Text(text = stringResource(R.string.view_manual))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.cdr_help_dismiss))
            }
        }
    )
}

@PreviewDayNight
@Composable
private fun CdrHelpDialogPreview() {
    NsPreview {
        CdrHelpDialog(onDismissRequest = {})
    }
}
