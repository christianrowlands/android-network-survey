package com.craxiom.networksurvey.ui.scout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsSegmentedToggle
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.common.SegmentedOption
import com.craxiom.networksurvey.ui.scout.components.ScoutName
import com.craxiom.networksurvey.ui.scout.components.ScoutStateWord
import com.craxiom.networksurvey.ui.scout.components.color
import com.craxiom.networksurvey.ui.theme.NsTheme

/** The fixes the Scout page and its pairing sheet both offer. */
interface ScoutFixActions {
    fun onTurnOnBluetooth() {}
    fun onAllowNearby() {}
    fun onOpenBluetoothSettings() {}
}

/** What the paired Scout page can ask for. */
interface ScoutPairedActions : ScoutFixActions {
    fun onSetSource(source: WifiSource) {}
    fun onStopSurvey() {}
    fun onPairAgain() {}
    fun onRemove() {}
    fun onErase() {}
    fun onUpdateFirmware() {}
}

/**
 * The NS Scout page with a Scout paired: its status, "Wi-Fi comes from" (locked while recording,
 * with Stop survey right there), what it is doing now, its details (the firmware line says whether
 * it is up to date), Update firmware, Remove, and the advanced Erase pairings. The firmware update
 * is blocked while recording.
 */
@Composable
fun ColumnScope.ScoutPairedContent(state: ScoutPageUi, actions: ScoutPairedActions) {
    StatusPanel(state, actions)
    SourcePanel(state, actions)
    val resources = LocalResources.current
    if (state.state !is ScoutState.NotInUse) {
        SectionHeader(stringResource(R.string.scout_now_heading))
        NsRowGroup {
            ValueRow(R.string.scout_now_doing, ScoutPageText.doing(resources, state.state, state.details))
            NsRowDivider()
            ValueRow(R.string.scout_now_hearing, ScoutPageText.hearing(resources, state.state, state.details))
            NsRowDivider()
            val missed = state.details.drops.dataMissed
            ValueRow(
                R.string.scout_now_missed, ScoutPageText.missed(resources, state.details.drops),
                if (missed) NsTheme.colors.status.warningText else Color.Unspecified,
            )
        }
    }
    val scout = state.state.scout ?: return
    SectionHeader(stringResource(R.string.scout_info_heading))
    NsRowGroup {
        ValueRow(R.string.scout_info_model, scout.modelDisplayName.ifEmpty { stringResource(R.string.scout_info_unknown) })
        NsRowDivider()
        ValueRow(
            R.string.scout_info_firmware, ScoutPageText.firmware(resources, state.state, state.details, state.firmwareStatus),
            if (state.state is ScoutState.NeedsFirmwareUpdate) NsTheme.colors.status.danger.content else Color.Unspecified,
        )
        NsRowDivider()
        ValueRow(R.string.scout_info_bands, ScoutStatusText.bands(resources, scout.bands) ?: stringResource(R.string.scout_info_unknown))
        NsRowDivider()
        ValueRow(R.string.scout_info_battery, ScoutPageText.battery(resources, state.details))
    }
    NsRowGroup {
        NsRow(
            title = stringResource(R.string.scout_fw_title),
            subtitle = ScoutPageText.updateRow(resources, state),
            icon = R.drawable.ic_system_update,
            onClick = if (state.recording) null else actions::onUpdateFirmware,
        ) { if (!state.recording) Chevron() }
        NsRowDivider()
        NsRow(
            title = stringResource(R.string.scout_remove),
            subtitle = if (state.recording) stringResource(R.string.scout_remove_blocked) else null,
            icon = R.drawable.ic_link_off,
            titleColor = NsTheme.colors.status.danger.content,
            iconTint = NsTheme.colors.status.danger.content,
            onClick = actions::onRemove,
        )
    }
    SectionHeader(stringResource(R.string.scout_advanced_heading))
    NsRowGroup {
        NsRow(
            title = stringResource(R.string.scout_erase),
            subtitle = stringResource(
                when {
                    state.recording -> R.string.scout_fw_row_recording
                    state.linkReady -> R.string.scout_erase_subtitle
                    else -> R.string.scout_erase_needs_link
                },
            ),
            icon = R.drawable.ic_restart_alt,
            onClick = actions::onErase,
        )
    }
}

/** The trailing arrow of a row that opens another page. */
@Composable
private fun Chevron() {
    Icon(painterResource(R.drawable.ic_chevron_right_small), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The status at the top: the word, the Scout's name, the detail and the one fix it offers. */
@Composable
private fun StatusPanel(state: ScoutPageUi, actions: ScoutPairedActions) {
    val resources = LocalResources.current
    val scoutState = state.state
    val level = ScoutStatusText.level(scoutState)
    val status = NsTheme.colors.status
    val container = when (level) {
        ScoutLevel.WARN -> status.warning.container
        ScoutLevel.BAD -> status.danger.container
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    NsPanel(color = container) {
        Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs)) {
            ScoutStateWord(
                word = ScoutStatusText.word(resources, scoutState, state.recording),
                level = level,
                live = state.recording && scoutState is ScoutState.Connected && scoutState.capturing,
            )
            ScoutName(state.scoutName, style = NsTheme.textStyles.monoReadout, color = MaterialTheme.colorScheme.onSurface)
            val detail = if (scoutState is ScoutState.NotInUse) {
                stringResource(R.string.scout_not_in_use_body)
            } else {
                ScoutStatusText.detail(resources, scoutState, state.recording, state.details.lastHeardAtMs, state.nowMs)
            }
            detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            StatusHint(state)
            StatusAction(scoutState, state.nearbyAccess, actions)
        }
    }
}

/** A longer tip where the fix is physical: bring it close, or reinstall from a computer. */
@Composable
private fun StatusHint(state: ScoutPageUi) {
    val hint = when (state.state) {
        is ScoutState.OutOfRange, is ScoutState.Connecting -> if (state.recording) null else stringResource(R.string.scout_out_hint)
        else -> null
    } ?: return
    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = NsTheme.spacing.xs))
}

@Composable
private fun StatusAction(state: ScoutState, nearbyAccess: PermissionAccess, actions: ScoutPairedActions) {
    val (label, onClick) = when (state) {
        is ScoutState.BluetoothOff -> stringResource(R.string.wifi_scout_action_turn_on_bluetooth) to actions::onTurnOnBluetooth
        is ScoutState.PermissionMissing -> stringResource(nearbyFixLabel(nearbyAccess)) to actions::onAllowNearby
        is ScoutState.PairingLost, is ScoutState.PairingRefused -> stringResource(R.string.scout_action_pair_again) to actions::onPairAgain
        is ScoutState.NeedsFirmwareUpdate -> stringResource(R.string.scout_fw_title) to actions::onUpdateFirmware
        else -> return
    }
    Button(onClick = onClick, modifier = Modifier.padding(top = NsTheme.spacing.s)) { Text(label) }
}

/** "Wi-Fi comes from: NS-ABCD | This phone", locked while a survey records. */
@Composable
private fun SourcePanel(state: ScoutPageUi, actions: ScoutPairedActions) {
    val scoutSelected = state.source == WifiSource.SCOUT
    NsPanel {
        Text(
            stringResource(R.string.scout_source_heading),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        NsSegmentedToggle(
            options = listOf(
                SegmentedOption(state.scoutName, ImageVector.vectorResource(R.drawable.ic_sensors)),
                SegmentedOption(stringResource(R.string.scout_source_phone), ImageVector.vectorResource(R.drawable.ic_smartphone)),
            ),
            selectedIndex = if (scoutSelected) 0 else 1,
            onSelected = { actions.onSetSource(if (it == 0) WifiSource.SCOUT else WifiSource.PHONE) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NsTheme.spacing.s),
            enabled = !state.recording,
            segmentMinHeight = NsTheme.spacing.touchTarget,
        )
        val note = when {
            !state.recording -> stringResource(R.string.scout_source_note)
            scoutSelected -> stringResource(R.string.scout_source_locked_scout)
            else -> stringResource(R.string.scout_source_locked_phone, state.scoutName)
        }
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = NsTheme.spacing.s),
        )
        if (state.recording) IconTextAction(R.drawable.ic_stop, R.string.survey_stop, actions::onStopSurvey)
    }
}

/**
 * A label on the left and its value on the right. Every value is end aligned in the same wide
 * column, so the Now and Scout values line up whatever their length (a long value wraps within the
 * column, still end aligned, instead of starting under the label).
 */
@Composable
private fun ValueRow(title: Int, value: String, valueColor: Color = Color.Unspecified) {
    NsRow(title = stringResource(title), role = null) {
        // The value takes the larger share and hugs the right edge, as on the prototype
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(VALUE_WEIGHT),
        )
    }
}

/** The value column's share against the label's 1, wide enough that the usual values fit on one line. */
private const val VALUE_WEIGHT = 2.2f
