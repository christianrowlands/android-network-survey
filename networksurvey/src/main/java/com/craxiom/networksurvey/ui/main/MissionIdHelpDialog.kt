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
import com.craxiom.networksurvey.ui.common.HelpSection
import com.craxiom.networksurvey.ui.preview.NsPreview
import com.craxiom.networksurvey.ui.preview.PreviewDayNight

/**
 * Material 3 help dialog that teaches the user what a Mission ID is (their survey's ID), when a
 * new one is generated, why it matters for finding their data in NS Analytics and their exported
 * files, and how to use it.
 */
@Composable
fun MissionIdHelpDialog(
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.mission_id_help_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(text = stringResource(R.string.mission_id_help_what))

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.mission_id_help_when_title)) {
                    Text(text = stringResource(R.string.mission_id_help_when))
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.mission_id_help_why_title)) {
                    Text(text = stringResource(R.string.mission_id_help_why))
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.mission_id_help_how_title)) {
                    Text(text = stringResource(R.string.mission_id_help_how))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.mission_id_help_dismiss))
            }
        }
    )
}

@PreviewDayNight
@Composable
private fun MissionIdHelpDialogPreview() {
    NsPreview {
        MissionIdHelpDialog(onDismissRequest = {})
    }
}
