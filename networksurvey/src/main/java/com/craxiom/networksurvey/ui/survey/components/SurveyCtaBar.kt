package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsCtaBar
import com.craxiom.networksurvey.ui.common.NsPrimaryCta
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.survey.model.StartBlockReason
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The pinned bar with the one Start or Stop button. When Start can't run, the reason sits right
 * above it (and is read out with the button), with the fix as a button where one exists.
 */
@Composable
fun SurveyCtaBar(
    recording: Boolean,
    startBlock: StartBlockReason?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onUseLogFiles: () -> Unit,
    onConnectNsAnalytics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NsCtaBar(modifier = modifier) {
        val reason = if (recording) null else startBlock?.let { stringResource(it.messageRes) }
        if (reason != null) {
            Text(text = reason, style = MaterialTheme.typography.bodyMedium, color = NsTheme.colors.status.warningText)
            if (startBlock == StartBlockReason.NS_ANALYTICS_NOT_CONNECTED) {
                Row(horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
                    NsTextAction(stringResource(R.string.survey_action_use_log_files), onUseLogFiles)
                    NsTextAction(stringResource(R.string.survey_action_connect_ns_analytics), onConnectNsAnalytics)
                }
            }
        }
        if (recording) {
            NsPrimaryCta(
                text = stringResource(R.string.survey_stop),
                onClick = onStop,
                icon = R.drawable.ic_stop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            )
        } else {
            NsPrimaryCta(
                text = stringResource(R.string.survey_start),
                onClick = onStart,
                enabled = startBlock == null,
                stateDescription = reason,
            ) { Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(CTA_ICON)) }
        }
    }
}

private val StartBlockReason.messageRes: Int
    get() = when (this) {
        StartBlockReason.NO_TYPES -> R.string.survey_block_no_types
        StartBlockReason.NO_DESTINATIONS -> R.string.survey_block_no_destinations
        StartBlockReason.ORGANIZATION_TURNED_OFF_EVERY_DESTINATION -> R.string.survey_block_organization_all_off
        StartBlockReason.NS_ANALYTICS_NOT_CONNECTED -> R.string.survey_block_ns_analytics
        StartBlockReason.NOTHING_DELIVERED -> R.string.survey_block_nothing_delivered
    }

private val CTA_ICON = 20.dp
