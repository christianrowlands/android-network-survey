package com.craxiom.networksurvey.ui.grpc

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
 * A help dialog for the gRPC connection screen explaining the connection settings
 * and data stream options.
 */
@Composable
fun GrpcHelpDialog(
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.grpc_help_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                HelpSection(title = "Quick Start") {
                    Text("1. Enter server address and port")
                    Text("2. Set a device name")
                    Text("3. Choose which data streams to enable")
                    Text("4. Toggle the connect switch")
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = "Connection Settings") {
                    HelpItem("Server Address", "IP address or hostname of the gRPC server")
                    HelpItem("Port", "Server port number (default: 2621)")
                    HelpItem("Device Name", "Name to identify this device on the server")
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = "Data Streams") {
                    HelpItem("Cellular", "LTE, NR, GSM, UMTS, CDMA survey data")
                    HelpItem("Phone State", "Cellular phone state change events")
                    HelpItem("Wi-Fi", "802.11 beacon records")
                    HelpItem("Bluetooth", "Bluetooth device discovery data")
                    HelpItem("GNSS", "GPS/GNSS satellite constellation data")
                    HelpItem("Device Status", "Location, battery, and device info")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.grpc_help_dismiss))
            }
        }
    )
}
