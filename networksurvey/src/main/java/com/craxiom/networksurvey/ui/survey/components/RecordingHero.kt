package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.engine.SurveyOrigin
import com.craxiom.networksurvey.ui.survey.model.RecordingUi
import com.craxiom.networksurvey.ui.theme.NsTheme
import java.util.Locale
import com.craxiom.networksurvey.ui.common.SMALL_ICON

/**
 * The Recording card: a big elapsed-time readout, proof of life ("1,204 records · last one 3 s
 * ago"), the Mission ID with a copy button, and what started the survey when it wasn't the user.
 */
@Composable
fun RecordingHero(recording: RecordingUi, onCopyMissionId: (String) -> Unit, modifier: Modifier = Modifier) {
    val state = if (recording.paused) NsTheme.colors.state.paused else NsTheme.colors.state.recording
    val badge = stringResource(if (recording.paused) R.string.survey_badge_paused else R.string.survey_badge_recording)
    val timer = formatElapsed(recording.elapsedMs)
    val panel = MaterialTheme.colorScheme.surfaceContainerLow

    Surface(modifier = modifier.fillMaxWidth(), shape = NsTheme.shapes.hero, color = panel, shadowElevation = 1.dp) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(state.container, panel)))
                .padding(NsTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
        ) {
            val description = stringResource(R.string.survey_timer_description, badge, timer)
            Column(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
                StateBadge(badge, state.content)
                Text(text = timer, style = NsTheme.textStyles.timer, color = MaterialTheme.colorScheme.onSurface)
            }
            Heartbeat(recording)
            recording.missionId?.let { MissionIdRow(it, onCopyMissionId) }
            originRes(recording.origin)?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StateBadge(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
        Box(
            modifier = Modifier
                .size(BADGE_DOT)
                .background(color, CircleShape)
        )
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun Heartbeat(recording: RecordingUi) {
    if (recording.deviceStatusOnly) {
        // No records to count or wait for: only MQTT's device status messages go out
        HeartbeatLine(stringResource(R.string.survey_device_status_only), stalled = false)
        return
    }
    val beat = when {
        recording.paused -> stringResource(R.string.survey_paused_nothing_recorded)
        recording.stalledMinutes != null -> stringResource(R.string.survey_last_record_minutes, recording.stalledMinutes)
        recording.secondsSinceLastRecord == null -> stringResource(R.string.survey_waiting_first_record)
        recording.secondsSinceLastRecord < JUST_NOW_SECONDS -> stringResource(R.string.survey_last_record_now)
        recording.secondsSinceLastRecord < SECONDS_PER_MINUTE ->
            stringResource(R.string.survey_last_record_seconds, recording.secondsSinceLastRecord.toInt())

        else -> stringResource(R.string.survey_last_record_minutes, (recording.secondsSinceLastRecord / SECONDS_PER_MINUTE).toInt())
    }
    HeartbeatLine(
        text = recordsText(recording.totalRecords, recording.countsSavedForUpload) + stringResource(R.string.survey_dot_separator) + beat,
        stalled = recording.stalledMinutes != null && !recording.paused,
    )
}

@Composable
private fun HeartbeatLine(text: String, stalled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SMALL_ICON))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (stalled) NsTheme.colors.status.danger.content else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun originRes(origin: SurveyOrigin?): Int? = when (origin) {
    SurveyOrigin.BOOT -> R.string.survey_origin_boot
    SurveyOrigin.INTENT -> R.string.survey_origin_intent
    SurveyOrigin.APP_OPEN -> R.string.survey_origin_app_open
    SurveyOrigin.ORGANIZATION -> R.string.survey_origin_organization
    SurveyOrigin.USER, null -> null
}

/** HH:MM:SS for the elapsed time readout. */
fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / MILLIS_PER_SECOND
    return String.format(
        Locale.ROOT, "%02d:%02d:%02d",
        totalSeconds / SECONDS_PER_HOUR, (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE, totalSeconds % SECONDS_PER_MINUTE,
    )
}

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val JUST_NOW_SECONDS = 2L
private val BADGE_DOT = 8.dp
