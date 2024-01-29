package com.craxiom.networksurvey.ui.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants.HIDDEN_SSID_PLACEHOLDER
import com.craxiom.networksurvey.fragments.WifiDetailsFragment
import com.craxiom.networksurvey.ui.SignalChart
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
import com.craxiom.networksurvey.util.ColorUtils
import com.craxiom.networksurvey.util.WifiUtils

/**
 * A Compose screen that shows the details of a single WiFi network. The main purpose for this
 * screen is to display the RSSI chart for the selected WiFi network so that the RSSI can be viewed
 * over time.
 */
@Composable
internal fun WifiDetailsScreen(
    viewModel: WifiDetailsViewModel,
    wifiDetailsFragment: WifiDetailsFragment
) {
    val context = LocalContext.current
    val rssi by viewModel.rssiFlow.collectAsStateWithLifecycle()
    val scanRate by viewModel.scanRate.collectAsStateWithLifecycle()
    val colorId = ColorUtils.getColorForSignalStrength(rssi)
    val colorResource = Color(context.getColor(colorId))

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(padding),
        verticalArrangement = Arrangement.spacedBy(padding),
    ) {
        chartItems(viewModel, colorResource, rssi, scanRate, wifiDetailsFragment)
    }
}

private fun LazyListScope.chartItems(
    viewModel: WifiDetailsViewModel,
    signalStrengthColor: Color,
    rssi: Float,
    scanRate: Int,
    wifiDetailsFragment: WifiDetailsFragment
) {
    val hiddenSsid = viewModel.wifiNetwork.ssid.isEmpty()
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            SelectionContainer {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(vertical = padding / 2)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (hiddenSsid) HIDDEN_SSID_PLACEHOLDER else viewModel.wifiNetwork.ssid,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color(
                                        LocalContext.current.getColor(
                                            if (hiddenSsid) R.color.red else R.color.colorAccent
                                        )
                                    )
                                )
                            )
                            Text(
                                text = "SSID",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (rssi == UNKNOWN_RSSI) "Unknown" else "${rssi.toInt()} dBm",
                                style = MaterialTheme.typography.titleMedium.copy(color = signalStrengthColor)
                            )
                            Text(
                                text = "Signal Strength",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .padding(horizontal = padding, vertical = padding / 2)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "BSSID: ${viewModel.wifiNetwork.bssid}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .padding(start = padding, end = padding, bottom = padding / 2)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = viewModel.wifiNetwork.encryptionType,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .padding(start = padding, end = padding, bottom = padding / 2)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "Channel: ${viewModel.wifiNetwork.channel?.toString() ?: "Unknown"}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.width(padding * 2))
                        Text(
                            text = "${viewModel.wifiNetwork.frequency?.toString() ?: "Unknown"} MHz",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .padding(start = padding, end = padding, bottom = padding / 2)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "Bandwidth: ${WifiUtils.formatBandwidth(viewModel.wifiNetwork.bandwidth)}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .padding(start = padding, end = padding, bottom = padding / 2)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = viewModel.wifiNetwork.capabilities,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (viewModel.wifiNetwork.passpoint != null && viewModel.wifiNetwork.passpoint == true) {

                        Row(
                            modifier = Modifier
                                .padding(start = padding, end = padding, bottom = padding / 2)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(
                                text = "Passpoint",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color(
                                        LocalContext.current.getColor(R.color.colorAccent)
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    item {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = padding)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "Scan Rate: ",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "$scanRate seconds",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.weight(1f))

                ScanRateInfoButton()

                OpenSettingsButton(wifiDetailsFragment)
            }
        }
    }

    cardItem {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Signal Strength (Last 2 Minutes)",
                style = MaterialTheme.typography.titleMedium
            )
            SignalChart(viewModel)
        }
    }
}


private fun LazyListScope.cardItem(content: @Composable () -> Unit) {
    item {
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors()) {
            Box(Modifier.padding(padding)) {
                content()
            }
        }
    }
}


@Composable
fun ScanRateInfoButton() {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showDialog = true }) {
        Icon(
            Icons.Default.Info,
            contentDescription = "About Wi-Fi Scan Rate",
        )
    }

    // Info Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Wi-Fi Scan Rate Info") },
            text = {
                Text(
                    "The rate at which Wi-Fi networks will be scanned for in " +
                            "seconds. Smaller values will decrease battery life but larger values will " +
                            "cause the Signal Strength Graph to be out of date. If you want values " +
                            "closer to real time then set the scan rate to 4 seconds or less."
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDialog = false }
                ) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
fun OpenSettingsButton(detailsFragment: WifiDetailsFragment) {

    IconButton(onClick = {
        detailsFragment.navigateToSettings()
    }) {
        Icon(
            Icons.Default.Settings,
            contentDescription = "Settings Button",
        )
    }
}

private val padding = 16.dp
