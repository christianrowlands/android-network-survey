package com.craxiom.networksurvey.ui.wifi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R

/**
 * Info button used by the Wi-Fi Spectrum screen and the Wi-Fi Details survey-data card to
 * explain what "scan rate" means.
 */
@Composable
fun ScanRateInfoButton() {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showDialog = true }) {
        Icon(
            Icons.Default.Info,
            contentDescription = stringResource(R.string.wifi_scan_rate_info_content_description),
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.wifi_scan_rate_info_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.wifi_scan_rate_info_body))
                }
            },
            confirmButton = {
                Button(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.dialog_button_got_it))
                }
            }
        )
    }
}

/**
 * Settings gear button that navigates to the Wi-Fi scan-rate preference. Mirrors the parallel
 * pattern used by the Bluetooth Details screen.
 */
@Composable
fun OpenWifiSettingsButton(onNavigate: () -> Unit) {
    IconButton(onClick = onNavigate) {
        Icon(
            Icons.Default.Settings,
            contentDescription = stringResource(R.string.wifi_settings_content_description),
        )
    }
}
