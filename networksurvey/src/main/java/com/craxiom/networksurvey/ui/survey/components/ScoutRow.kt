package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.ui.common.NsChevron
import com.craxiom.networksurvey.ui.scout.ScoutLevel
import com.craxiom.networksurvey.ui.scout.ScoutStatusText
import com.craxiom.networksurvey.ui.scout.components.ScoutBadge
import com.craxiom.networksurvey.ui.scout.components.ScoutName
import com.craxiom.networksurvey.ui.scout.components.ScoutStateWord
import com.craxiom.networksurvey.ui.scout.components.color
import com.craxiom.networksurvey.ui.survey.model.ScoutRowUi
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The Survey tab's NS Scout row: the Scout's name and status word, with the detail under them
 * (channel and frames heard while recording). Tapping it opens the Scout page. With this phone as
 * the Wi-Fi source it stays (a paired Scout is never silently unused) and offers "Use NS-ABCD"
 * before Start. The border turns amber or red with the status, never as its only signal.
 */
@Composable
fun ScoutRow(row: ScoutRowUi, onOpen: () -> Unit, onUseScout: () -> Unit, modifier: Modifier = Modifier) {
    val level = ScoutStatusText.level(row.state)
    val border = when (level) {
        ScoutLevel.WARN, ScoutLevel.BAD -> BorderStroke(ROW_BORDER, level.color)
        else -> null
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = NsTheme.shapes.panel,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = ROW_SHADOW,
        border = border,
    ) {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.wifi_scout_action_scout_page), onClick = onOpen)
                .heightIn(min = ROW_MIN_HEIGHT)
                .padding(start = NsTheme.spacing.m, end = NsTheme.spacing.s, top = NsTheme.spacing.m, bottom = NsTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
        ) {
            if (row.phoneIsSource) PhoneSourceContent(row, onUseScout) else ScoutSourceContent(row, level)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowScope.ScoutSourceContent(row: ScoutRowUi, level: ScoutLevel) {
    val resources = LocalResources.current
    val state = row.state
    ScoutBadge(R.drawable.ic_sensors)
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
            verticalArrangement = Arrangement.Center,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ScoutName(row.scoutName, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
            ScoutStateWord(
                word = ScoutStatusText.word(resources, state, row.recording),
                level = level,
                live = row.recording && state is ScoutState.Connected && state.capturing,
            )
        }
        ScoutStatusText.detail(resources, state, row.recording, row.lastHeardAtMs, row.nowMs)?.let { detail ->
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (row.recording && dataMissed(state)) {
            Text(
                text = stringResource(R.string.scout_detail_data_missed),
                style = MaterialTheme.typography.bodySmall,
                color = NsTheme.colors.status.warningText,
            )
        }
    }
    NsChevron()
}

@Composable
private fun RowScope.PhoneSourceContent(row: ScoutRowUi, onUseScout: () -> Unit) {
    ScoutBadge(R.drawable.ic_wifi, accent = false)
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs)) {
        Text(stringResource(R.string.scout_row_phone_title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = stringResource(R.string.scout_row_phone_detail, row.scoutName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Locked while recording: a survey uses one Wi-Fi source from start to finish
    if (!row.recording) {
        FilledTonalButton(onClick = onUseScout, modifier = Modifier.heightIn(min = NsTheme.spacing.touchTarget)) {
            Text(stringResource(R.string.scout_action_use, row.scoutName), style = MaterialTheme.typography.labelLarge)
        }
    } else {
        NsChevron()
    }
}

private fun dataMissed(state: ScoutState): Boolean = when (state) {
    is ScoutState.Connected -> state.dataMissed
    is ScoutState.CatchingUp -> state.dataMissed
    else -> false
}

private val ROW_BORDER = 1.5.dp
private val ROW_SHADOW = 1.dp
private val ROW_MIN_HEIGHT = 64.dp
