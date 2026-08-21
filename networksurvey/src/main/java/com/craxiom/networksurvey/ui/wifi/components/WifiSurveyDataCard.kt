package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.craxiom.networksurvey.ui.wifi.OpenWifiSettingsButton
import com.craxiom.networksurvey.ui.wifi.ScanRateInfoButton

/**
 * Survey-data card. Shows the recording/exclusion status and include/exclude toggle for this
 * network's SSID, plus the watchlist status and Watch/View action for this network. The card
 * header hosts a settings gear (deep-links to the Wi-Fi scan-rate preference) and an info button
 * (explains the scan rate). Each action row is followed by an always-visible caption that teaches
 * what the action actually does. The exclude pill is hidden for hidden-SSID networks (exclusion is
 * SSID-based), but the watchlist row stays because a specific AP can be watched by BSSID.
 */
@Composable
fun WifiSurveyDataCard(
    isExcluded: Boolean,
    isWatched: Boolean,
    hiddenSsid: Boolean,
    scanRateSeconds: Int,
    onToggle: () -> Unit,
    onWatchlistClick: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailsCardFrame(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.wifi_details_survey_data),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.wifi_details_scan_rate_compact, scanRateSeconds),
                    style = TextStyle(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ScanRateInfoButton()
            OpenWifiSettingsButton(onNavigate = onNavigateToSettings)
        }
        StatusPillRow(
            statusText = if (isExcluded) {
                stringResource(R.string.wifi_details_excluded_from_survey)
            } else {
                stringResource(R.string.wifi_details_included_in_survey)
            },
            pillLabel = if (hiddenSsid) {
                null
            } else if (isExcluded) {
                stringResource(R.string.wifi_details_include)
            } else {
                stringResource(R.string.wifi_details_exclude)
            },
            onPillClick = onToggle,
        )
        CaptionText(stringResource(R.string.wifi_exclusion_info))
        StatusPillRow(
            statusText = if (isWatched) {
                stringResource(R.string.wifi_details_on_watchlist)
            } else {
                stringResource(R.string.wifi_details_not_on_watchlist)
            },
            pillLabel = if (isWatched) {
                stringResource(R.string.wifi_details_view_watchlist)
            } else {
                stringResource(R.string.wifi_details_watch)
            },
            onPillClick = onWatchlistClick,
        )
        CaptionText(stringResource(R.string.wifi_details_watchlist_info))
    }
}

/**
 * A status line with an optional trailing action pill. Passing a null [pillLabel] renders the
 * status text alone (used when the action does not apply, like excluding a hidden SSID).
 */
@Composable
private fun StatusPillRow(
    statusText: String,
    pillLabel: String?,
    onPillClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = statusText,
            style = TextStyle(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (pillLabel != null) {
            TogglePill(label = pillLabel, onClick = onPillClick)
        }
    }
}

@Composable
private fun CaptionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TogglePill(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = WifiTokens.SsidAccent,
        )
    }
}
