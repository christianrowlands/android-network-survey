package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.ui.wifi.model.WifiListMode

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
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    val bg = if (selected) WifiTokens.SsidSoft else androidx.compose.ui.graphics.Color.Transparent
    val border = if (selected) {
        androidx.compose.ui.graphics.Color.Transparent
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val textColor =
        if (selected) WifiTokens.SsidAccent else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = textColor,
        )
    }
}
