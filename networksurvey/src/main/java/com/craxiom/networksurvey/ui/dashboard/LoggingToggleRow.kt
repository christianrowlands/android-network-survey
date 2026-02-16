package com.craxiom.networksurvey.ui.dashboard

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R

/**
 * A single toggle row for the local file logging card. Displays an icon, label, optional help
 * button, and a switch to enable/disable logging for a given protocol.
 */
@Composable
fun LoggingToggleRow(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    subtitle: String? = null,
    onHelpClick: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (onHelpClick != null) {
                    IconButton(
                        onClick = onHelpClick,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_help),
                            contentDescription = "$label help",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
            )
        }

        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }
}

/**
 * The full logging control content for the local file logging card. Renders all 6 toggle rows
 * (Cellular, Phone State, Wi-Fi, Bluetooth, GNSS, CDR) in a single column with dividers.
 */
@Composable
fun LoggingControlContent(
    cellularEnabled: Boolean,
    phoneStateEnabled: Boolean,
    phoneStateAutoStarted: Boolean,
    wifiEnabled: Boolean,
    bluetoothEnabled: Boolean,
    gnssEnabled: Boolean,
    cdrEnabled: Boolean,
    onCellularToggle: (Boolean) -> Unit,
    onPhoneStateToggle: (Boolean) -> Unit,
    onWifiToggle: (Boolean) -> Unit,
    onBluetoothToggle: (Boolean) -> Unit,
    onGnssToggle: (Boolean) -> Unit,
    onCdrToggle: (Boolean) -> Unit,
    onPhoneStateHelpClick: () -> Unit,
    onCdrHelpClick: () -> Unit,
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 4.dp),
    ) {
        LoggingToggleRow(
            icon = R.drawable.ic_cellular,
            label = stringResource(R.string.cellular_title),
            enabled = cellularEnabled,
            onToggle = onCellularToggle,
        )

        HorizontalDivider(
            modifier = Modifier.padding(start = 52.dp),
            thickness = 1.dp,
            color = dividerColor,
        )

        LoggingToggleRow(
            icon = R.drawable.ic_phone_state,
            label = stringResource(R.string.phone_state_title),
            enabled = phoneStateEnabled,
            onToggle = onPhoneStateToggle,
            subtitle = if (phoneStateAutoStarted) stringResource(R.string.phone_state_logging_status_auto) else null,
            onHelpClick = onPhoneStateHelpClick,
        )

        HorizontalDivider(
            modifier = Modifier.padding(start = 52.dp),
            thickness = 1.dp,
            color = dividerColor,
        )

        LoggingToggleRow(
            icon = R.drawable.ic_wifi,
            label = stringResource(R.string.wifi_title),
            enabled = wifiEnabled,
            onToggle = onWifiToggle,
        )

        HorizontalDivider(
            modifier = Modifier.padding(start = 52.dp),
            thickness = 1.dp,
            color = dividerColor,
        )

        LoggingToggleRow(
            icon = R.drawable.ic_bluetooth,
            label = stringResource(R.string.bluetooth_title),
            enabled = bluetoothEnabled,
            onToggle = onBluetoothToggle,
        )

        HorizontalDivider(
            modifier = Modifier.padding(start = 52.dp),
            thickness = 1.dp,
            color = dividerColor,
        )

        LoggingToggleRow(
            icon = R.drawable.ic_gnss,
            label = stringResource(R.string.gnss_title),
            enabled = gnssEnabled,
            onToggle = onGnssToggle,
        )

        HorizontalDivider(
            modifier = Modifier.padding(start = 52.dp),
            thickness = 1.dp,
            color = dividerColor,
        )

        LoggingToggleRow(
            icon = R.drawable.ic_cdr,
            label = stringResource(R.string.cdr_title),
            enabled = cdrEnabled,
            onToggle = onCdrToggle,
            onHelpClick = onCdrHelpClick,
        )
    }
}
