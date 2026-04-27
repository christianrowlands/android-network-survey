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
 * Survey-data card. Shows the recording/exclusion status, scan rate, and the include/exclude
 * toggle for this network's SSID. The card header hosts a settings gear (deep-links to the
 * Wi-Fi scan-rate preference) and an info button (explains the scan rate). An always-visible
 * caption below the toggle teaches what excluding a network actually does.
 */
@Composable
fun WifiSurveyDataCard(
    isExcluded: Boolean,
    hiddenSsid: Boolean,
    scanRateSeconds: Int,
    onToggle: () -> Unit,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (isExcluded) {
                    stringResource(R.string.wifi_details_excluded_from_survey)
                } else {
                    stringResource(R.string.wifi_details_included_in_survey)
                },
                style = TextStyle(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (!hiddenSsid) {
                TogglePill(
                    label = if (isExcluded) {
                        stringResource(R.string.wifi_details_include)
                    } else {
                        stringResource(R.string.wifi_details_exclude)
                    },
                    onClick = onToggle,
                )
            }
        }
        Text(
            text = stringResource(R.string.wifi_exclusion_info),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
