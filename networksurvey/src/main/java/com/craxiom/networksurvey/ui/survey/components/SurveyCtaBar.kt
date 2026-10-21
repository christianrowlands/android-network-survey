package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsCtaBar
import com.craxiom.networksurvey.ui.common.NsPrimaryCta
import com.craxiom.networksurvey.ui.common.NsActionRow
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.common.rememberCompactHeight
import com.craxiom.networksurvey.ui.survey.model.StartBlockReason
import com.craxiom.networksurvey.ui.theme.NsTheme
import kotlinx.coroutines.delay

/**
 * The pinned bar with the one Start or Stop button. When Start can't run, the reason sits right
 * above it (beside it in a short window) and is read out with the button, with the fix as a button
 * where one exists.
 *
 * @param commandInFlight True while a start or stop is on its way; the button keeps its label and
 *                        color, dimmed and disabled, so a second tap can't send a second command.
 *                        Stop also stays dimmed for [STOP_GUARD_MS] after it replaces Start, since a
 *                        start can finish before the second tap of a double tap on Start lands.
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
    commandInFlight: Boolean = false,
) {
    val stopJustAppeared = rememberStopJustAppeared(recording)
    val reason = if (recording) null else startBlock?.let { stringResource(it.messageRes) }
    val button = @Composable { buttonModifier: Modifier ->
        if (recording) {
            NsPrimaryCta(
                text = stringResource(R.string.survey_stop),
                onClick = onStop,
                modifier = buttonModifier,
                icon = R.drawable.ic_stop,
                enabled = !commandInFlight && !stopJustAppeared,
                colors = dimmedWhileBusy(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError),
            )
        } else {
            NsPrimaryCta(
                text = stringResource(R.string.survey_start),
                onClick = onStart,
                modifier = buttonModifier,
                enabled = startBlock == null && !commandInFlight,
                stateDescription = reason,
                // A blocked Start keeps the standard disabled look; a busy one only dims
                colors = if (startBlock == null) {
                    dimmedWhileBusy(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(CTA_ICON)) }
        }
    }
    NsCtaBar(modifier = modifier) {
        when {
            reason == null -> button(Modifier)
            // A short window puts the reason beside Start rather than above it, to save a line or two
            rememberCompactHeight() -> Row(
                horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs)) {
                    BlockedReason(reason, startBlock, onUseLogFiles, onConnectNsAnalytics)
                }
                button(Modifier.weight(1f))
            }

            else -> {
                BlockedReason(reason, startBlock, onUseLogFiles, onConnectNsAnalytics)
                button(Modifier)
            }
        }
    }
}

/** Why Start can't run, with the fix as buttons where one exists. */
@Composable
private fun BlockedReason(reason: String, startBlock: StartBlockReason?, onUseLogFiles: () -> Unit, onConnectNsAnalytics: () -> Unit) {
    Text(text = reason, style = MaterialTheme.typography.bodyMedium, color = NsTheme.colors.status.warningText)
    if (startBlock == StartBlockReason.NS_ANALYTICS_NOT_CONNECTED) {
        NsActionRow(gap = NsTheme.spacing.s) {
            NsTextAction(stringResource(R.string.survey_action_use_log_files), onUseLogFiles)
            NsTextAction(stringResource(R.string.survey_action_connect_ns_analytics), onConnectNsAnalytics)
        }
    }
}

/**
 * @return True from the moment Stop replaces Start until [STOP_GUARD_MS] later. False when the screen
 * opens on a survey that is already recording, so Stop works right away there.
 */
@Composable
private fun rememberStopJustAppeared(recording: Boolean): Boolean {
    var wasRecording by remember { mutableStateOf(recording) }
    var guarding by remember { mutableStateOf(false) }
    // Read in this composition, so Stop is never enabled in the frame before the effect runs
    val justStarted = recording && !wasRecording
    LaunchedEffect(recording) {
        val started = recording && !wasRecording
        wasRecording = recording
        if (started) {
            guarding = true
            try {
                delay(STOP_GUARD_MS)
            } finally {
                guarding = false
            }
        }
    }
    return justStarted || guarding
}

/** [container] and [content], with the disabled pair the same colors dimmed, for a button busy with its command. */
@Composable
private fun dimmedWhileBusy(container: Color, content: Color) = ButtonDefaults.buttonColors(
    containerColor = container,
    contentColor = content,
    disabledContainerColor = container.copy(alpha = BUSY_ALPHA),
    disabledContentColor = content.copy(alpha = BUSY_ALPHA),
)

private val StartBlockReason.messageRes: Int
    get() = when (this) {
        StartBlockReason.NO_TYPES -> R.string.survey_block_no_types
        StartBlockReason.NO_DESTINATIONS -> R.string.survey_block_no_destinations
        StartBlockReason.ORGANIZATION_TURNED_OFF_EVERY_DESTINATION -> R.string.survey_block_organization_all_off
        StartBlockReason.NS_ANALYTICS_NOT_CONNECTED -> R.string.survey_block_ns_analytics
        StartBlockReason.NOTHING_DELIVERED -> R.string.survey_block_nothing_delivered
    }

private val CTA_ICON = 20.dp
private const val BUSY_ALPHA = 0.6f

/** How long Stop stays disabled after it replaces Start. */
internal const val STOP_GUARD_MS = 1_000L
