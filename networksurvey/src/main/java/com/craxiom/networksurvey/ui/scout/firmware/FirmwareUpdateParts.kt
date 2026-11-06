package com.craxiom.networksurvey.ui.scout.firmware

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.firmware.FirmwareConsent
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsSwitchRow
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.common.ROW_ICON
import com.craxiom.networksurvey.ui.common.SMALL_ICON
import com.craxiom.networksurvey.ui.scout.components.ScoutName
import com.craxiom.networksurvey.ui.theme.NsColorPair
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The top panel: what is being updated and to what. "Update", the Scout's name, "0.1.0 → 0.2.0"
 * (read by TalkBack as "From firmware 0.1.0 to 0.2.0") and who signed it. A reinstall of the
 * version it runs reads "0.1.0 · latest release".
 */
@Composable
internal fun FirmwareHeader(name: String, installed: String, target: String?, signedLine: String?) {
    NsPanel {
        Text(stringResource(R.string.scout_fw_overline), style = NsTheme.textStyles.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ScoutName(name, style = NsTheme.textStyles.monoReadout, color = MaterialTheme.colorScheme.onSurface)
        val unknown = stringResource(R.string.scout_info_unknown)
        val from = installed.ifEmpty { unknown }
        if (target != null && target == installed) {
            Text(stringResource(R.string.scout_fw_version_latest, target), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = NsTheme.spacing.xs))
        } else if (target != null) {
            val spoken = stringResource(R.string.scout_fw_versions_spoken, from, target)
            Text(
                stringResource(R.string.scout_fw_versions, from, target),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(top = NsTheme.spacing.xs)
                    .clearAndSetSemantics { contentDescription = spoken },
            )
        } else {
            Text(from, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = NsTheme.spacing.xs))
        }
        signedLine?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Why an update can't start now, with its one fix (none while recording or still connecting). */
@Composable
internal fun FirmwareBlockerPanel(blocker: FirmwareBlocker, name: String, nearbyAccess: PermissionAccess, onFix: () -> Unit) {
    val (body, action) = when (blocker) {
        FirmwareBlocker.RECORDING -> R.string.scout_fw_blocked_recording to null
        FirmwareBlocker.PERMISSION -> R.string.scout_fw_blocked_permission to
            if (nearbyAccess == PermissionAccess.BLOCKED) R.string.open_settings else R.string.survey_action_allow
        FirmwareBlocker.BLUETOOTH_OFF -> R.string.scout_fw_blocked_bluetooth to R.string.wifi_scout_action_turn_on_bluetooth
        FirmwareBlocker.PAIRING_LOST -> R.string.scout_fw_blocked_pairing to R.string.scout_action_scout_page
        FirmwareBlocker.WAITING -> R.string.scout_fw_blocked_waiting to null
    }
    val colors = if (blocker == FirmwareBlocker.WAITING) NsTheme.colors.status.warning else NsTheme.colors.status.danger
    NoticePanel(colors, R.drawable.ic_info, stringResource(R.string.scout_fw_blocked_title), stringResource(body, name), busy = blocker == FirmwareBlocker.WAITING) {
        action?.let { Button(onClick = onFix, modifier = Modifier.padding(top = NsTheme.spacing.s)) { Text(stringResource(it)) } }
    }
}

/** A tinted panel with an icon, a title and a body, announced politely when it appears. */
@Composable
internal fun NoticePanel(
    colors: NsColorPair,
    @DrawableRes icon: Int,
    title: String,
    body: String,
    busy: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    NsPanel(color = colors.container) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(ROW_ICON), strokeWidth = SPINNER_STROKE, color = colors.content)
            } else {
                Icon(painterResource(icon), contentDescription = null, tint = colors.content, modifier = Modifier.size(ROW_ICON))
            }
            Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action?.invoke()
    }
}

/**
 * The install steps with an overall progress bar: done steps get a filled check, the current one
 * a spinner, later ones an empty circle and a muted label. Each row tells TalkBack its state.
 */
@Composable
internal fun FirmwareSteps(@StringRes steps: List<Int>, current: Int, progress: Float, detail: String?) {
    val resources = LocalResources.current
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    detail?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Column(modifier = Modifier.padding(top = NsTheme.spacing.s)) {
        steps.forEachIndexed { index, label ->
            val state = when {
                index < current -> R.string.scout_fw_step_done
                index == current -> R.string.scout_fw_step_current
                else -> R.string.scout_fw_step_waiting
            }
            val spokenState = resources.getString(state)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = NsTheme.spacing.touchTarget)
                    .semantics(mergeDescendants = true) { stateDescription = spokenState },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
            ) {
                StepMark(index, current)
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (index > current) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun StepMark(index: Int, current: Int) {
    when {
        index < current -> Icon(
            painterResource(R.drawable.ic_check_circle_filled), contentDescription = null,
            tint = NsTheme.colors.status.success.content, modifier = Modifier.size(SMALL_ICON),
        )

        index == current -> CircularProgressIndicator(modifier = Modifier.size(SMALL_ICON), strokeWidth = SPINNER_STROKE)
        else -> Icon(
            painterResource(R.drawable.ic_radio_button_unchecked), contentDescription = null,
            tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(SMALL_ICON),
        )
    }
}

/** One switch per consent the user must accept explicitly. */
@Composable
internal fun ConsentRows(consents: List<FirmwareConsent>, accepted: Set<FirmwareConsent>, onAccept: (FirmwareConsent, Boolean) -> Unit) {
    val resources = LocalResources.current
    consents.forEach { consent ->
        NsSwitchRow(
            title = FirmwareUpdateText.consent(resources, consent),
            checked = consent in accepted,
            onCheckedChange = { onAccept(consent, it) },
        )
    }
}

/** A short line in a panel, muted unless [color] says otherwise. */
@Composable
internal fun PanelText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = modifier)
}

private val SPINNER_STROKE = NsTheme.spacing.xxs
