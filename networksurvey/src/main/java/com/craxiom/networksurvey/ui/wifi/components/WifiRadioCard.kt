package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.util.WifiUtils

/**
 * Radio card: Channel / Frequency / Bandwidth / Standard in a 2x2 grid.
 * Channel value includes the center channel when different (e.g. "149 (151)").
 */
@Composable
fun WifiRadioCard(network: WifiNetwork, modifier: Modifier = Modifier) {
    DetailsCardFrame(modifier = modifier) {
        CardHeader(text = stringResource(R.string.wifi_details_radio))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KeyValueCell(
                label = stringResource(R.string.wifi_details_channel),
                value = formatChannel(network),
                modifier = Modifier.weight(1f),
            )
            KeyValueCell(
                label = stringResource(R.string.wifi_details_frequency),
                value = network.frequency?.let {
                    stringResource(R.string.wifi_details_frequency_value, it)
                } ?: "",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KeyValueCell(
                label = stringResource(R.string.wifi_details_bandwidth),
                value = WifiUtils.formatBandwidth(network.bandwidth),
                modifier = Modifier.weight(1f),
            )
            KeyValueCell(
                label = stringResource(R.string.wifi_details_standard),
                value = WifiUtils.formatStandard(network.standard),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun DetailsCardFrame(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun CardHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.em,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun KeyValueCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.06.em,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { "-" },
            style = TextStyle(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatChannel(network: WifiNetwork): String {
    val channel = network.channel ?: return ""
    val center = if (network.frequency != null) {
        WifiUtils.getCenterChannel(channel, network.bandwidth, network.frequency)
    } else {
        channel
    }
    return if (center == channel) "$channel" else "$channel ($center)"
}
