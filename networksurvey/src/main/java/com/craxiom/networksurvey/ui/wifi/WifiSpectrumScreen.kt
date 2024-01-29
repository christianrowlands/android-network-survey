package com.craxiom.networksurvey.ui.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.fragments.WifiSpectrumFragment

/**
 * A Compose screen that shows the usage of the Wi-Fi spectrum.
 */
@Composable
internal fun WifiSpectrumScreen(
    viewModel: WifiSpectrumChartViewModel,
    wifiSpectrumFragment: WifiSpectrumFragment
) {
    val scanRate by viewModel.scanRate.collectAsStateWithLifecycle()

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(padding),
        verticalArrangement = Arrangement.spacedBy(padding),
    ) {
        chartItems(viewModel, scanRate, wifiSpectrumFragment)
    }
}

private fun LazyListScope.chartItems(
    viewModel: WifiSpectrumChartViewModel,
    scanRate: Int,
    wifiSpectrumFragment: WifiSpectrumFragment
) {
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

                OpenSettingsBtn(wifiSpectrumFragment)
            }
        }
    }

    cardItem {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Wi-Fi 2.4 GHz Spectrum",
                style = MaterialTheme.typography.titleMedium
            )
            WifiSpectrumChart(
                viewModel, viewModel.modelProducer2Point4Ghz,
                CHANNELS_2_4_GHZ_CHART_VIEW[0].toFloat(),
                CHANNELS_2_4_GHZ_CHART_VIEW[CHANNELS_2_4_GHZ_CHART_VIEW.size - 1].toFloat(),
                CHANNELS_2_4_GHZ
            )
        }
    }

    cardItem {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Wi-Fi 5 GHz Group 1",
                style = MaterialTheme.typography.titleMedium
            )
            WifiSpectrumChart(
                viewModel,
                viewModel.modelProducer5GhzGroup1,
                CHANNELS_5_GHZ_GROUP_1_CHART_VIEW[0].toFloat(),
                CHANNELS_5_GHZ_GROUP_1_CHART_VIEW[CHANNELS_5_GHZ_GROUP_1_CHART_VIEW.size - 1].toFloat(),
                CHANNELS_5_GHZ_GROUP_1
            )
        }
    }

    cardItem {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Wi-Fi 5 GHz Group 2",
                style = MaterialTheme.typography.titleMedium
            )
            WifiSpectrumChart(
                viewModel,
                viewModel.modelProducer5GhzGroup2,
                CHANNELS_5_GHZ_GROUP_2_CHART_VIEW[0].toFloat(),
                CHANNELS_5_GHZ_GROUP_2_CHART_VIEW[CHANNELS_5_GHZ_GROUP_2_CHART_VIEW.size - 1].toFloat(),
                CHANNELS_5_GHZ_GROUP_2,
                everyOtherLabel = true
            )
        }
    }

    cardItem {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Wi-Fi 5 GHz Group 3",
                style = MaterialTheme.typography.titleMedium
            )
            WifiSpectrumChart(
                viewModel,
                viewModel.modelProducer5GhzGroup3,
                CHANNELS_5_GHZ_GROUP_3_CHART_VIEW[0].toFloat(),
                CHANNELS_5_GHZ_GROUP_3_CHART_VIEW[CHANNELS_5_GHZ_GROUP_3_CHART_VIEW.size - 1].toFloat(),
                CHANNELS_5_GHZ_GROUP_3
            )
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
fun OpenSettingsBtn(fragment: WifiSpectrumFragment) {

    IconButton(onClick = {
        fragment.navigateToSettings()
    }) {
        Icon(
            Icons.Default.Settings,
            contentDescription = "Settings Button",
        )
    }
}

private val padding = 16.dp
