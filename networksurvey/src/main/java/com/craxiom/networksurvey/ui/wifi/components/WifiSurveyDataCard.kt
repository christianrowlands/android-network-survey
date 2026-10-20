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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsDetailsCardFrame
import com.craxiom.networksurvey.ui.common.NsScanRateActions
import com.craxiom.networksurvey.ui.common.wifiScanRateHelp
import com.craxiom.networksurvey.ui.theme.NsTheme

private val PILL_MIN_HEIGHT = 32.dp
private val PILL_BORDER = 1.dp

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
    NsDetailsCardFrame(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs),
            ) {
                Text(
                    text = stringResource(R.string.wifi_details_survey_data),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.wifi_details_scan_rate_compact, scanRateSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NsScanRateActions(wifiScanRateHelp(), onNavigateToSettings)
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
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
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
            .minimumInteractiveComponentSize()
            .clip(NsTheme.shapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(BorderStroke(PILL_BORDER, MaterialTheme.colorScheme.outlineVariant), NsTheme.shapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = PILL_MIN_HEIGHT)
            .padding(horizontal = NsTheme.spacing.m + NsTheme.spacing.xxs, vertical = NsTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
