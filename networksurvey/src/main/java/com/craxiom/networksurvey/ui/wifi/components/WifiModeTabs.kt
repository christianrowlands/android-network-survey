package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.main.appbar.AppBarAction
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.wifi.model.WifiListMode

private val TAB_MIN_HEIGHT = 36.dp
private val TAB_BORDER = 1.dp

/**
 * Mode toggle tabs: Flat · BSSID vs Group by SSID, with the Wi-Fi spectrum button at the end of the
 * row. When the full labels and the button don't fit on one line (a narrow phone, a large font or a
 * long translation), the tabs show their short labels and TalkBack still reads the full ones. Band
 * filtering lives in the display filter sheet ([WifiFilterSheet]) opened from the scan strip.
 */
@Composable
fun WifiModeTabs(
    mode: WifiListMode,
    onModeChanged: (WifiListMode) -> Unit,
    onOpenSpectrum: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flat = stringResource(R.string.wifi_mode_flat_bssid)
    val grouped = stringResource(R.string.wifi_mode_group_by_ssid)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = NsTheme.textStyles.compact.copy(fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current

    // Capped like the strip above it, so the spectrum button lines up with the strip's controls
    BoxWithConstraints(modifier.nsContentWidth()) {
        val gutter = NsTheme.spacing.screenGutter - NsTheme.spacing.xxs
        val tabPadding = (NsTheme.spacing.m + NsTheme.spacing.xxs) * 2
        val fullWidth = with(density) {
            listOf(flat, grouped).sumOf { textMeasurer.measure(it, labelStyle).size.width }.toDp()
        } + tabPadding * 2 + NsTheme.spacing.s * 3 + NsTheme.spacing.touchTarget + gutter + NsTheme.spacing.xs
        val short = fullWidth > maxWidth

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = gutter, end = NsTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WifiModePills(mode, onModeChanged, short)
            Spacer(Modifier.weight(1f))
            AppBarAction(AppBarAction(R.drawable.ic_bar_chart, R.string.open_wifi_spectrum, onOpenSpectrum))
        }
    }
}

/**
 * The Flat · BSSID and Group by SSID pills on their own, for the mode row or, in a short window,
 * the scan strip.
 *
 * @param short True for the short labels ("BSSIDs", "By SSID"); TalkBack still reads the full ones.
 */
@Composable
fun WifiModePills(mode: WifiListMode, onModeChanged: (WifiListMode) -> Unit, short: Boolean, modifier: Modifier = Modifier) {
    val flat = stringResource(R.string.wifi_mode_flat_bssid)
    val grouped = stringResource(R.string.wifi_mode_group_by_ssid)
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeTab(
            label = if (short) stringResource(R.string.wifi_mode_flat_bssid_short) else flat,
            description = flat,
            selected = mode == WifiListMode.FLAT,
            onClick = { onModeChanged(WifiListMode.FLAT) },
        )
        ModeTab(
            label = if (short) stringResource(R.string.wifi_mode_group_by_ssid_short) else grouped,
            description = grouped,
            selected = mode == WifiListMode.GROUPED,
            onClick = { onModeChanged(WifiListMode.GROUPED) },
        )
    }
}

@Composable
private fun ModeTab(label: String, description: String, selected: Boolean, onClick: () -> Unit) {
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
            // A short label is read out in full
            modifier = if (label != description) Modifier.semantics { contentDescription = description } else Modifier,
        )
    }
}
