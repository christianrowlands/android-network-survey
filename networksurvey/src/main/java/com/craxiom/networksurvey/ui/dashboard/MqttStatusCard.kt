package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.R

private val MqttDisconnected = Color(0xFFA21919)
private val MqttConnecting = Color(0xFFFF6D00)
private val MqttConnected = Color(0xFF00912F)
private val StreamActive = Color(0xFF03A9F4)
private val StreamInactive = Color(0xFFA2A2A2)

/**
 * Card that displays MQTT connection status, a toggle switch, and streaming indicators
 * for each protocol type. The toggle is hidden when under MDM control.
 */
@Composable
fun MqttStatusCard(
    state: MqttUiState,
    onToggleMqtt: (Boolean) -> Unit,
    onNavigateToMqttSettings: () -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnectedOrConnecting = state.connectionState == ConnectionState.CONNECTED
            || state.connectionState == ConnectionState.CONNECTING

    val statusColor by animateColorAsState(
        targetValue = when (state.connectionState) {
            ConnectionState.CONNECTED -> MqttConnected
            ConnectionState.CONNECTING -> MqttConnecting
            else -> MqttDisconnected
        },
        animationSpec = tween(300),
        label = "mqttStatusColor",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DashboardCardDefaults.margin, vertical = 4.dp),
        shape = DashboardCardDefaults.shape,
        elevation = DashboardCardDefaults.elevation(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        DashboardCardHeader(
            icon = R.drawable.ic_cloud_connection,
            title = stringResource(R.string.card_title_mqtt_details),
            onHelpClick = onHelpClick,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // MQTT connection status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.connection_icon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = statusColor,
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = when (state.connectionState) {
                        ConnectionState.CONNECTED -> stringResource(R.string.mqtt_connected)
                        ConnectionState.CONNECTING -> stringResource(R.string.mqtt_connecting)
                        else -> stringResource(R.string.mqtt_off)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = DashboardCardDefaults.TextPrimary,
                    modifier = Modifier.weight(1f),
                )

                if (!state.isMqttToggleHiddenByMdm) {
                    Switch(
                        checked = isConnectedOrConnecting,
                        onCheckedChange = onToggleMqtt,
                    )
                }

                IconButton(
                    onClick = onNavigateToMqttSettings,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.mqtt_connection_settings),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Streaming indicators - only visible when connected or connecting
            if (isConnectedOrConnecting) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.what_is_configured_to_stream),
                        style = MaterialTheme.typography.bodySmall,
                        color = DashboardCardDefaults.TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )

                    MqttStreamItem(
                        label = stringResource(R.string.cellular_title),
                        enabled = state.cellularStreamEnabled,
                    )
                    MqttStreamItem(
                        label = stringResource(R.string.phone_state_title),
                        enabled = state.phoneStateStreamEnabled,
                    )
                    MqttStreamItem(
                        label = stringResource(R.string.wifi_title),
                        enabled = state.wifiStreamEnabled,
                    )
                    MqttStreamItem(
                        label = stringResource(R.string.bluetooth_title),
                        enabled = state.bluetoothStreamEnabled,
                    )
                    MqttStreamItem(
                        label = stringResource(R.string.gnss_title),
                        enabled = state.gnssStreamEnabled,
                    )
                    MqttStreamItem(
                        label = stringResource(R.string.device_status_label),
                        enabled = state.deviceStatusStreamEnabled,
                    )
                }
            }
        }
    }
}

/**
 * A single MQTT stream indicator showing the protocol icon, label, and on/off status.
 */
@Composable
private fun MqttStreamItem(
    label: String,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.circle_not_in_view),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) StreamActive else StreamInactive,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = DashboardCardDefaults.TextSecondary,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = if (enabled) stringResource(R.string.status_on) else stringResource(R.string.status_disabled),
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) StreamActive else StreamInactive,
        )
    }
}
