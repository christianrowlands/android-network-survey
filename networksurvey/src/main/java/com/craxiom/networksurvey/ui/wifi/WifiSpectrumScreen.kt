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
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_2_4_GHZ
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_2_4_GHZ_CHART_VIEW
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_5_GHZ_GROUP_1
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_5_GHZ_GROUP_1_CHART_VIEW
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_5_GHZ_GROUP_2
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_5_GHZ_GROUP_2_CHART_VIEW
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_5_GHZ_GROUP_3
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_5_GHZ_GROUP_3_CHART_VIEW
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_6_GHZ
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_6_GHZ_CHART_VIEW
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrum24ViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrum5Group1ViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrum5Group2ViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrum5Group3ViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrum6ViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrumScreenViewModel

/**
 * A Compose screen that shows the usage of the Wi-Fi spectrum.
 */
@Composable
internal fun WifiSpectrumScreen(
    screenViewModel: WifiSpectrumScreenViewModel,
    viewModel24Ghz: WifiSpectrum24ViewModel,
    viewModel5GhzGroup1: WifiSpectrum5Group1ViewModel,
    viewModel5GhzGroup2: WifiSpectrum5Group2ViewModel,
    viewModel5GhzGroup3: WifiSpectrum5Group3ViewModel,
    viewModel6Ghz: WifiSpectrum6ViewModel,
    wifiSpectrumFragment: WifiSpectrumFragment
) {
    val scanRate by screenViewModel.scanRate.collectAsStateWithLifecycle()

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(padding),
        verticalArrangement = Arrangement.spacedBy(padding),
    ) {
        chartItems(
            viewModel24Ghz,
            viewModel5GhzGroup1,
            viewModel5GhzGroup2,
            viewModel5GhzGroup3,
            viewModel6Ghz,
            scanRate,
            wifiSpectrumFragment
        )
    }
}

private fun LazyListScope.chartItems(
    viewModel24Ghz: WifiSpectrum24ViewModel,
    viewModel5GhzGroup1: WifiSpectrum5Group1ViewModel,
    viewModel5GhzGroup2: WifiSpectrum5Group2ViewModel,
    viewModel5GhzGroup3: WifiSpectrum5Group3ViewModel,
    viewModel6Ghz: WifiSpectrum6ViewModel,
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
            val wifiList by viewModel24Ghz.wifiNetworkInfoList.collectAsStateWithLifecycle()
            WifiSpectrumChart(
                wifiList, viewModel24Ghz.modelProducer,
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
            val wifiList by viewModel5GhzGroup1.wifiNetworkInfoList.collectAsStateWithLifecycle()
            WifiSpectrumChart(
                wifiList,
                viewModel5GhzGroup1.modelProducer,
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
            val wifiList by viewModel5GhzGroup2.wifiNetworkInfoList.collectAsStateWithLifecycle()
            WifiSpectrumChart(
                wifiList,
                viewModel5GhzGroup2.modelProducer,
                CHANNELS_5_GHZ_GROUP_2_CHART_VIEW[0].toFloat(),
                CHANNELS_5_GHZ_GROUP_2_CHART_VIEW[CHANNELS_5_GHZ_GROUP_2_CHART_VIEW.size - 1].toFloat(),
                CHANNELS_5_GHZ_GROUP_2,
                labelInterval = 2
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
            val wifiList by viewModel5GhzGroup3.wifiNetworkInfoList.collectAsStateWithLifecycle()
            WifiSpectrumChart(
                wifiList,
                viewModel5GhzGroup3.modelProducer,
                CHANNELS_5_GHZ_GROUP_3_CHART_VIEW[0].toFloat(),
                CHANNELS_5_GHZ_GROUP_3_CHART_VIEW[CHANNELS_5_GHZ_GROUP_3_CHART_VIEW.size - 1].toFloat(),
                CHANNELS_5_GHZ_GROUP_3
            )
        }
    }

    cardItem {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Wi-Fi 6 GHz",
                style = MaterialTheme.typography.titleMedium
            )
            val wifiList by viewModel6Ghz.wifiNetworkInfoList.collectAsStateWithLifecycle()
            WifiSpectrumChart(
                wifiList,
                viewModel6Ghz.modelProducer,
                CHANNELS_6_GHZ_CHART_VIEW[0].toFloat(),
                CHANNELS_6_GHZ_CHART_VIEW[CHANNELS_6_GHZ_CHART_VIEW.size - 1].toFloat(),
                CHANNELS_6_GHZ,
                labelInterval = 8
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
