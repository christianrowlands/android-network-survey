package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.common.HelpItem
import com.craxiom.networksurvey.ui.common.NsCardHeader
import com.craxiom.networksurvey.ui.common.NsDetailsCardFrame
import com.craxiom.networksurvey.util.WifiUtils

private const val STANDARD_PREFIX = "802.11"

/**
 * Radio card: Channel / Frequency / Bandwidth / Standard in a 2x2 grid.
 * Each cell renders the meaningful number prominently (bold titleLarge, bright ink) with
 * the unit or secondary part (e.g. "MHz", "(38)") dimmed and smaller so the eye lands
 * on the data. The Standard cell inverts the pattern: the constant "802.11" prefix is
 * dimmed and the variant suffix (e.g. "be") is emphasized, with a stacked Wi-Fi
 * generation pill (e.g. "Wi-Fi 7") underneath where applicable. A trailing info button
 * opens a dialog explaining what each cell means.
 */
@Composable
fun WifiRadioCard(network: WifiNetwork, modifier: Modifier = Modifier) {
    var showInfoDialog by remember { mutableStateOf(false) }

    NsDetailsCardFrame(modifier = modifier) {
        NsCardHeader(
            text = stringResource(R.string.wifi_details_radio),
            trailingContent = {
                IconButton(onClick = { showInfoDialog = true }) {
                    Icon(
                        painterResource(R.drawable.ic_info),
                        contentDescription = stringResource(R.string.wifi_radio_info_content_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val (channelPrimary, channelSuffix) = formatChannelParts(network)
                RadioStatCell(
                    label = stringResource(R.string.wifi_details_channel),
                    primary = channelPrimary,
                    suffix = channelSuffix,
                    modifier = Modifier.weight(1f),
                )
                RadioStatCell(
                    label = stringResource(R.string.wifi_details_frequency),
                    primary = network.frequency?.toString().orEmpty(),
                    suffix = network.frequency?.let { stringResource(R.string.band_unit_mhz) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val (bandwidthPrimary, bandwidthSuffix) = formatBandwidthParts(network)
                RadioStatCell(
                    label = stringResource(R.string.wifi_details_bandwidth),
                    primary = bandwidthPrimary,
                    suffix = bandwidthSuffix,
                    modifier = Modifier.weight(1f),
                )
                val (standardPrefix, standardPrimary) = formatStandardParts(network)
                val generationLabel =
                    WifiUtils.formatWifiGenerationLabel(network.standard, network.frequency)
                RadioStatCell(
                    label = stringResource(R.string.wifi_details_standard),
                    primary = standardPrimary,
                    prefix = standardPrefix,
                    modifier = Modifier.weight(1f),
                    belowContent = if (generationLabel != null) {
                        { WifiGenerationPill(label = generationLabel) }
                    } else {
                        null
                    },
                )
            }
        }
    }

    if (showInfoDialog) {
        RadioInfoDialog(onDismiss = { showInfoDialog = false })
    }
}

@Composable
private fun RadioInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wifi_radio_info_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HelpItem(
                    label = stringResource(R.string.wifi_details_channel),
                    description = stringResource(R.string.wifi_radio_help_channel),
                )
                HelpItem(
                    label = stringResource(R.string.wifi_details_frequency),
                    description = stringResource(R.string.wifi_radio_help_frequency),
                )
                HelpItem(
                    label = stringResource(R.string.wifi_details_bandwidth),
                    description = stringResource(R.string.wifi_radio_help_bandwidth),
                )
                HelpItem(
                    label = stringResource(R.string.wifi_details_standard),
                    description = stringResource(R.string.wifi_radio_help_standard),
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_button_got_it))
            }
        },
    )
}

@Composable
private fun WifiGenerationPill(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun RadioStatCell(
    label: String,
    primary: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    prefix: String? = null,
    belowContent: (@Composable () -> Unit)? = null,
) {
    val isBlank = primary.isBlank()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!isBlank && prefix != null) {
                Text(
                    text = prefix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Text(
                text = if (isBlank) "-" else primary,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
            if (!isBlank && suffix != null) {
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        if (!isBlank && belowContent != null) {
            belowContent()
        }
    }
}

private fun formatChannelParts(network: WifiNetwork): Pair<String, String?> {
    val channel = network.channel ?: return "" to null
    val center = if (network.frequency != null) {
        WifiUtils.getCenterChannel(channel, network.bandwidth, network.frequency)
    } else {
        channel
    }
    val suffix = if (center != channel) "($center)" else null
    return channel.toString() to suffix
}

private fun formatBandwidthParts(network: WifiNetwork): Pair<String, String?> {
    val raw = WifiUtils.formatBandwidth(network.bandwidth)
    if (raw.isBlank()) return "" to null
    val spaceIndex = raw.indexOf(' ')
    return if (spaceIndex >= 0) {
        raw.substring(0, spaceIndex) to raw.substring(spaceIndex + 1)
    } else {
        raw to null
    }
}

private fun formatStandardParts(network: WifiNetwork): Pair<String?, String> {
    val raw = WifiUtils.formatStandard(network.standard)
    if (raw.isBlank()) return null to ""
    return if (raw.startsWith(STANDARD_PREFIX) && raw.length > STANDARD_PREFIX.length) {
        STANDARD_PREFIX to raw.substring(STANDARD_PREFIX.length)
    } else {
        null to raw
    }
}
