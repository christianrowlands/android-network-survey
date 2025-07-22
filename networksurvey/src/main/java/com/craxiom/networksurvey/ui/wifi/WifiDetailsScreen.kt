package com.craxiom.networksurvey.ui.wifi

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants.HIDDEN_SSID_PLACEHOLDER
import com.craxiom.networksurvey.fragments.WifiDetailsFragment
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.SignalChart
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.ui.wifi.model.WifiDetailsViewModel
import com.craxiom.networksurvey.util.ColorUtils
import com.craxiom.networksurvey.util.WifiUtils
import kotlinx.coroutines.launch

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

    Scaffold(
        topBar = { TitleBar("Wi-Fi Network Details", { wifiDetailsFragment.navigateBack() }) },
    ) { insetPadding ->
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(padding),
            verticalArrangement = Arrangement.spacedBy(padding),
            modifier = Modifier.padding(insetPadding)
        ) {
            chartItems(viewModel, colorResource, rssi, scanRate, wifiDetailsFragment)
        }
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
                            text = "Channel: ${getChannelInfo(viewModel.wifiNetwork)}",
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
                            text = "Standard: ${WifiUtils.formatStandard(viewModel.wifiNetwork.standard)}",
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

    // Exclusion settings at the bottom
    item {
        ExclusionCard(viewModel, wifiDetailsFragment)
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

@Composable
internal fun ExclusionCard(
    viewModel: WifiDetailsViewModel,
    detailsFragment: WifiDetailsFragment
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hiddenSsid = viewModel.wifiNetwork.ssid.isEmpty()

    // Collect service flow to properly handle exclusion state
    val service by detailsFragment.serviceFlow.collectAsStateWithLifecycle()
    var isExcluded by remember { mutableStateOf(false) }

    // Update exclusion state when service becomes available
    LaunchedEffect(service) {
        service?.let { currentService ->
            val exclusionManager = currentService.getSsidExclusionManager()
            isExcluded = exclusionManager?.isExcluded(viewModel.wifiNetwork.ssid) ?: false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(), // More subtle than elevated
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Survey Data",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    hiddenSsid -> "Hidden networks cannot be excluded"
                    isExcluded -> "This network is excluded from survey data"
                    else -> "This network is included in survey data"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = padding)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val exclusionManager = service?.getSsidExclusionManager()

                            if (exclusionManager != null) {
                                val ssid = viewModel.wifiNetwork.ssid

                                if (isExcluded) {
                                    // Remove from exclusion list
                                    exclusionManager.removeExcludedSsid(ssid)
                                    isExcluded = false
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.ssid_removed_from_exclusion,
                                            ssid
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    // Add to exclusion list
                                    if (exclusionManager.addExcludedSsid(ssid)) {
                                        isExcluded = true
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.ssid_added_to_exclusion,
                                                ssid
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        if (exclusionManager.isAtMaxCapacity()) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.exclusion_list_full),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    enabled = !hiddenSsid
                ) {
                    Text(
                        text = if (isExcluded) "Include in Surveys" else "Exclude from Surveys"
                    )
                }

                if (isExcluded) {
                    TextButton(
                        onClick = {
                            detailsFragment.navigateToExclusionList()
                        }
                    ) {
                        Text("View List")
                    }
                }
            }
        }
    }
}

/**
 * Gets the channel String that displays the center channel as well if it is different.
 */
private fun getChannelInfo(network: WifiNetwork): String {
    if (network.channel == null) return ""

    val channel = network.channel
    var centerChannel = channel
    if (network.frequency != null) {
        centerChannel =
            WifiUtils.getCenterChannel(network.channel, network.bandwidth, network.frequency)
    }

    return if (channel == centerChannel) {
        "" + channel
    } else {
        "$channel ($centerChannel)"
    }
}

private val padding = 16.dp
