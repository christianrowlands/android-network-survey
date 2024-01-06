package com.craxiom.networksurvey.ui.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A Compose screen that shows the details of a single WiFi network. The main purpose for this
 * screen is to display the RSSI chart for the selected WiFi network so that the RSSI can be viewed
 * over time.
 */
@Composable
internal fun WifiDetailsScreen(
    viewModel: WifiDetailsViewModel
) {
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(padding),
        verticalArrangement = Arrangement.spacedBy(padding),
    ) {
        chartItems(viewModel)
    }
}

private fun LazyListScope.chartItems(
    viewModel: WifiDetailsViewModel,
) {
    item {
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors()) {
            Column(modifier = Modifier.padding(padding)) {
                Text(
                    text = "BSSID: ${viewModel.wifiNetwork.bssid}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "SSID: ${if (viewModel.wifiNetwork.ssid.isEmpty()) "Hidden Network" else viewModel.wifiNetwork.ssid}",
                    style = MaterialTheme.typography.titleMedium
                )
                // TODO Update the signal strength with every scan
                //Text(text = "Signal Strength: ${viewModel.wifiNetwork.signalStrength?.toString() ?: "Unknown"} dBm", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Frequency: ${viewModel.wifiNetwork.frequency?.toString() ?: "Unknown"} MHz",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Channel: ${viewModel.wifiNetwork.channel?.toString() ?: "Unknown"}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Encryption: ${viewModel.wifiNetwork.encryptionType}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Passpoint: ${if (viewModel.wifiNetwork.passpoint == true) "Yes" else "No"}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Capabilities: ${viewModel.wifiNetwork.capabilities}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    cardItem { WifiRssiChart(viewModel.modelProducer) }
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

private const val COLOR_1_CODE = 0xffa485e0
private val color1 = Color(COLOR_1_CODE)
