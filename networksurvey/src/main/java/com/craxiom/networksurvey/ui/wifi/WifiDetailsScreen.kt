package com.craxiom.networksurvey.ui.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.model.WifiNetwork

/**
 * A Compose screen that shows the details of a single WiFi network. The main purpose for this
 * screen is to display the RSSI chart for the selected WiFi network so that the RSSI can be viewed
 * over time.
 */
@Composable
internal fun WifiDetailsScreen(
    viewModel: WifiDetailsViewModel,
    wifiNetwork: WifiNetwork
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
    // TODO Add in all the other network details.
    cardItem { WifiRssiChart(viewModel.modelProducer) }
}

private fun LazyListScope.cardItem(content: @Composable () -> Unit) {
    item {
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors()) {
            Box(androidx.compose.ui.Modifier.padding(padding)) {
                content()
            }
        }
    }
}

private val padding = 16.dp

private const val COLOR_1_CODE = 0xffa485e0
private val color1 = Color(COLOR_1_CODE)
