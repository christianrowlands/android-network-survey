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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants.HIDDEN_SSID_PLACEHOLDER
import com.craxiom.networksurvey.util.ColorUtils

/**
 * A Compose screen that shows the details of a single WiFi network. The main purpose for this
 * screen is to display the RSSI chart for the selected WiFi network so that the RSSI can be viewed
 * over time.
 */
@Composable
internal fun WifiDetailsScreen(
    viewModel: WifiDetailsViewModel
) {
    val context = LocalContext.current
    val rssi by viewModel.rssiFlow.collectAsStateWithLifecycle()
    val colorId = ColorUtils.getColorForWifiSignalStrength(rssi)
    val colorResource = Color(context.getColor(colorId))

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(padding),
        verticalArrangement = Arrangement.spacedBy(padding),
    ) {
        chartItems(viewModel, colorResource, rssi)
    }
}

private fun LazyListScope.chartItems(
    viewModel: WifiDetailsViewModel,
    signalStrengthColor: Color,
    rssi: Float
) {
    val hiddenSsid = viewModel.wifiNetwork.ssid.isEmpty()
// TODO Make text selectable
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
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

    cardItem {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Signal Strength (Last 2 Minutes)",
                style = MaterialTheme.typography.titleMedium
            )
            WifiRssiChart(viewModel.modelProducer)
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

private val padding = 16.dp
