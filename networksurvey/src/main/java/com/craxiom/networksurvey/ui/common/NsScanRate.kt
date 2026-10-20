package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.dialogs.NsMessageDialog

/** What a scan interval's info button explains, and the description of its settings gear. */
data class ScanRateHelp(val title: String, val body: String, val infoDescription: String, val settingsDescription: String)

/**
 * The info button that explains a radio's scan interval and the gear that opens its setting, as on
 * the Wi-Fi and Bluetooth details pages and the spectrum.
 */
@Composable
fun NsScanRateActions(help: ScanRateHelp, onOpenSettings: () -> Unit) {
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { infoOpen = true }) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = help.infoDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    IconButton(onClick = onOpenSettings) {
        Icon(painterResource(R.drawable.ic_settings), contentDescription = help.settingsDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (infoOpen) {
        NsMessageDialog(
            title = help.title,
            message = help.body,
            onDismiss = { infoOpen = false },
            confirmText = stringResource(R.string.dialog_button_got_it),
        )
    }
}

/** A card saying how often the radio scans ("Scans every 15 seconds"), with [NsScanRateActions]. */
@Composable
fun NsScanRateCard(seconds: Int, help: ScanRateHelp, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    NsDetailsCardFrame(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pluralStringResource(R.plurals.details_scan_interval, seconds, seconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            NsScanRateActions(help, onOpenSettings)
        }
    }
}

/** The Wi-Fi scan interval's help. */
@Composable
fun wifiScanRateHelp() = ScanRateHelp(
    title = stringResource(R.string.wifi_scan_rate_info_title),
    body = stringResource(R.string.wifi_scan_rate_info_body),
    infoDescription = stringResource(R.string.wifi_scan_rate_info_content_description),
    settingsDescription = stringResource(R.string.wifi_settings_content_description),
)

/** The Bluetooth scan interval's help. */
@Composable
fun bluetoothScanRateHelp() = ScanRateHelp(
    title = stringResource(R.string.bluetooth_scan_rate_info_title),
    body = stringResource(R.string.bluetooth_scan_rate_info_body),
    infoDescription = stringResource(R.string.bluetooth_scan_rate_info_content_description),
    settingsDescription = stringResource(R.string.bluetooth_settings_content_description),
)
