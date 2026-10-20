package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.survey.model.CommunityUploadUi
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsBesideOrBelow
import com.craxiom.networksurvey.ui.common.NsTextAction

/**
 * The compact row the Survey screen shows while a community database upload runs, so whoever
 * pressed Upload can watch it (and cancel it) without leaving the screen.
 */
@Composable
fun CommunityUploadProgressRow(upload: CommunityUploadUi, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    NsPanel(modifier = modifier) {
        NsBesideOrBelow(
            gap = 0.dp,
            main = {
                Text(
                    text = if (upload.running) {
                        stringResource(R.string.survey_community_uploading, upload.percent)
                    } else {
                        stringResource(R.string.survey_community_upload_waiting)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            end = { NsTextAction(stringResource(R.string.cancel), onCancel) },
        )
        LinearProgressIndicator(
            progress = { upload.percent / PERCENT },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NsTheme.spacing.xs),
        )
    }
}

private const val PERCENT = 100f
