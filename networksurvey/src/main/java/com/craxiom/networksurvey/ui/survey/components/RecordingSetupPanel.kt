package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsTextAction

/**
 * "What it's surveying" (or the saved setup's name when the survey matches one) while Recording: every record type as a chip (on ones tinted in their own
 * color, off ones dashed), and Change, which opens the sheet that applies immediately.
 *
 * @param deviceStatusOnly True when the survey records no types and MQTT sends only device status;
 *                         the panel says so instead of six chips that are all off.
 */
@Composable
fun RecordingSetupPanel(
    types: List<RecordType>,
    canChange: Boolean,
    onChange: () -> Unit,
    onWhatGoesWhere: () -> Unit,
    modifier: Modifier = Modifier,
    setupName: String? = null,
    notSent: Set<RecordType> = emptySet(),
    deviceStatusOnly: Boolean = false,
) {
    NsPanel(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = setupName ?: stringResource(R.string.survey_running_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (canChange) {
                NsTextAction(stringResource(R.string.survey_change), onChange)
            } else {
                Text(
                    stringResource(R.string.survey_set_by_organization),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (deviceStatusOnly) {
            Text(
                text = stringResource(R.string.survey_device_status_only),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = NsTheme.spacing.s),
            )
        } else {
            TypeChips(on = types.toSet(), notSent = notSent, onNotSent = onWhatGoesWhere)
        }
        NsTextAction(stringResource(R.string.survey_what_goes_where), onWhatGoesWhere)
        if (canChange) {
            Text(
                text = stringResource(R.string.survey_changes_apply_now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = NsTheme.spacing.s),
            )
        }
    }
}

/**
 * All six record types as chips; [on] ones are tinted with their own color. A type in [notSent]
 * was selected but no destination takes it: it shows a warning and opens "What goes where".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypeChips(on: Set<RecordType>, notSent: Set<RecordType>, onNotSent: () -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.padding(top = NsTheme.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
    ) {
        RecordType.entries.forEach { type ->
            if (type in notSent) NotSentChip(type, onNotSent) else TypeChip(type, type in on)
        }
    }
}

/** A selected type no destination takes: amber, "Call records · not sent", tap for the table. */
@Composable
private fun NotSentChip(type: RecordType, onClick: () -> Unit) {
    val warning = NsTheme.colors.status.warningText
    Row(
        modifier = Modifier
            .clip(NsTheme.shapes.chip)
            .background(warning.copy(alpha = ON_TINT_ALPHA))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.survey_what_goes_where), onClick = onClick)
            .padding(horizontal = NsTheme.spacing.s, vertical = NsTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = warning, modifier = Modifier.size(CHIP_ICON))
        Text(
            text = stringResource(R.string.survey_chip_not_sent, stringResource(type.labelRes)),
            style = MaterialTheme.typography.bodyMedium,
            color = warning,
        )
    }
}

@Composable
private fun TypeChip(type: RecordType, on: Boolean) {
    val colors = MaterialTheme.colorScheme
    val outline = colors.outline
    val chipModifier = if (on) {
        Modifier
            .clip(NsTheme.shapes.chip)
            .background(type.color.copy(alpha = ON_TINT_ALPHA))
    } else {
        Modifier.drawBehind {
            drawRoundRect(
                color = outline,
                style = Stroke(width = DASH_WIDTH.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))),
                cornerRadius = CornerRadius(CHIP_RADIUS.toPx()),
            )
        }
    }
    Row(
        modifier = chipModifier.padding(horizontal = NsTheme.spacing.s, vertical = NsTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
    ) {
        Icon(
            painterResource(type.iconRes),
            contentDescription = null,
            tint = if (on) type.color else outline,
            modifier = Modifier.size(CHIP_ICON),
        )
        Text(
            text = stringResource(type.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
            color = if (on) colors.onSurface else outline,
        )
    }
}

private const val ON_TINT_ALPHA = 0.14f
private const val DASH = 8f
private val DASH_WIDTH = 1.dp
private val CHIP_RADIUS = 8.dp
private val CHIP_ICON = 16.dp
