package com.craxiom.networksurvey.ui.mqtt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.HelpItem
import com.craxiom.networksurvey.ui.common.HelpSection

/**
 * A help dialog for the MQTT connection screen explaining all the connection settings
 * and data stream options.
 */
@Composable
fun MqttHelpDialog(
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.mqtt_help_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                HelpSection(title = "Quick Start") {
                    Text("1. Enter broker address")
                    Text("2. Set port (8883 for TLS, 1883 for plain)")
                    Text("3. Toggle the connect switch")
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = "Connection Settings") {
                    HelpItem("Server", "IP address or hostname of your MQTT broker")
                    HelpItem("Port", "8883 (TLS) or 1883 (plain text)")
                    HelpItem("Client ID", "Unique name to identify this device")
                    HelpItem("TLS", "Enable for encrypted connections")
                    HelpItem("Username/Password", "Optional broker authentication")
                    HelpItem("Topic Prefix", "Custom prefix for all MQTT topics")
                    HelpItem(
                        "QoS",
                        "Message delivery guarantee (0=at most once, 1=at least once, 2=exactly once)"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = "Data Streams") {
                    HelpItem("Cellular", "LTE, NR, GSM, UMTS, CDMA survey data")
                    HelpItem("Wi-Fi", "802.11 beacon and probe data")
                    HelpItem("Bluetooth", "Bluetooth device discovery data")
                    HelpItem("GNSS", "GPS/GNSS satellite constellation data")
                    HelpItem("Device Status", "Location, battery, and device info")
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = "QR Code") {
                    HelpItem("Auto Configure", "Scan a QR code to import broker settings")
                    HelpItem("Share", "Export current settings as a QR code")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.mqtt_help_dismiss))
            }
        }
    )
}

