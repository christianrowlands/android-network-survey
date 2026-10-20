package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.wifi.model.WifiListMode

private val TAB_MIN_HEIGHT = 36.dp
private val TAB_BORDER = 1.dp

/**
 * Mode toggle tabs: Flat · BSSID vs Group by SSID. Band filtering lives in the display filter
 * sheet ([WifiFilterSheet]) opened from the app bar funnel icon.
 */
@Composable
fun WifiModeTabs(
    mode: WifiListMode,
    onModeChanged: (WifiListMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(horizontal = NsTheme.spacing.screenGutter - NsTheme.spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeTab(
            label = stringResource(R.string.wifi_mode_flat_bssid),
            selected = mode == WifiListMode.FLAT,
            onClick = { onModeChanged(WifiListMode.FLAT) },
        )
        ModeTab(
            label = stringResource(R.string.wifi_mode_group_by_ssid),
            selected = mode == WifiListMode.GROUPED,
            onClick = { onModeChanged(WifiListMode.GROUPED) },
        )
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val border = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(NsTheme.shapes.pill)
            .background(bg)
            .border(BorderStroke(TAB_BORDER, border), NsTheme.shapes.pill)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = TAB_MIN_HEIGHT)
            .padding(horizontal = NsTheme.spacing.m + NsTheme.spacing.xxs, vertical = NsTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = NsTheme.textStyles.compact,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}
