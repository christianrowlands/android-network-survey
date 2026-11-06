package com.craxiom.networksurvey.ui.wifi.clients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.clients.ScoutClient
import com.craxiom.networksurvey.ui.common.NsCardHeader
import com.craxiom.networksurvey.ui.common.NsChevron
import com.craxiom.networksurvey.ui.common.NsDetailsCardFrame
import com.craxiom.networksurvey.ui.common.relativeAgeText
import com.craxiom.networksurvey.ui.manufacturer.rememberWifiManufacturerLabelText
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.wifi.components.WifiHeardByChip
import com.craxiom.networksurvey.util.textColor
import com.craxiom.networksurvey.util.toWifiSignalCategory

/**
 * The Client details hero: the address (monospace, read in pairs), the manufacturer for an address
 * that is not randomized, the "Randomized" tag and "Heard by NS-ABCD", the signal, and when and on
 * which channel it was last heard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WifiClientHeroCard(state: WifiClientDetailsState, modifier: Modifier = Modifier) {
    val client = state.client
    NsDetailsCardFrame(modifier = modifier) {
        Text(
            text = state.mac,
            style = NsTheme.textStyles.mono.copy(fontWeight = FontWeight.SemiBold),
            fontSize = MaterialTheme.typography.titleLarge.fontSize,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics {
                contentDescription = WifiClientsMapper.spokenMac(state.mac)
            },
        )
        if (client != null && !client.locallyAdministered) {
            rememberWifiManufacturerLabelText(client.mac).text?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (client?.locallyAdministered == true) RandomizedTag()
            if (state.scoutName.isNotEmpty()) WifiHeardByChip(state.scoutName)
        }
        if (client == null) {
            Text(
                text = stringResource(R.string.wifi_client_not_heard, state.scoutName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NsDetailsCardFrame
        }
        Text(
            text = stringResource(R.string.dbm_value_label, client.rssi.toString()),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (state.heardNow) client.rssi.toWifiSignalCategory().textColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val ago = relativeAgeText(client.lastHeardMs, state.nowMs.coerceAtLeast(client.lastHeardMs), midSentence = true)
        Text(
            text = client.channel?.let { stringResource(R.string.wifi_client_last_heard_channel, ago, it) }
                ?: stringResource(R.string.wifi_client_last_heard, ago),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The access point the client talks to: its SSID (when the Wi-Fi list heard it) and BSSID. A tap
 * opens the access point's details when [onOpenNetwork] is given (the access point is in the list).
 */
@Composable
fun WifiClientConnectedCard(bssid: String, ssid: String?, onOpenNetwork: (() -> Unit)?, modifier: Modifier = Modifier) {
    NsDetailsCardFrame(modifier = modifier) {
        NsCardHeader(text = stringResource(R.string.wifi_client_connected_to_title))
        val label = stringResource(R.string.wifi_client_open_network)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onOpenNetwork != null) Modifier.clickable(onClickLabel = label, onClick = onOpenNetwork) else Modifier)
                .heightIn(min = NsTheme.spacing.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        ) {
            // The name and address share the row; the chevron always sits at the end
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
            ) {
                if (!ssid.isNullOrEmpty()) {
                    Text(
                        text = ssid,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    text = bssid,
                    style = NsTheme.textStyles.mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = WifiClientsMapper.spokenMac(bssid) },
                )
            }
            if (onOpenNetwork != null) NsChevron()
        }
    }
}

/** The network names the client asked for in its probe requests, newest first. */
@Composable
fun WifiClientLookedForCard(client: ScoutClient, modifier: Modifier = Modifier) {
    NsDetailsCardFrame(modifier = modifier) {
        NsCardHeader(text = stringResource(R.string.wifi_client_looked_for_title)) {
            Text(
                text = stringResource(R.string.wifi_client_looked_for_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = NsTheme.spacing.xs),
            )
        }
        ChipRow(client.probedSsids.asReversed())
    }
}

/** The kinds of frames the client was heard sending, and what "Randomized" means. */
@Composable
fun WifiClientSeenInCard(client: ScoutClient, modifier: Modifier = Modifier) {
    NsDetailsCardFrame(modifier = modifier) {
        NsCardHeader(text = stringResource(R.string.wifi_client_seen_in_title))
        ChipRow(client.frameTypes.mapNotNull { it.labelRes() }.map { stringResource(it) })
        if (client.locallyAdministered) {
            Text(
                text = stringResource(R.string.wifi_client_randomized_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * An access point's Connected clients: the clients heard talking to it, strongest first; each
 * opens Client details. Shown only for an access point an NS Scout heard, and only with clients.
 */
@Composable
fun WifiConnectedClientsCard(clients: List<ScoutClient>, onClientClick: (ScoutClient) -> Unit, modifier: Modifier = Modifier) {
    NsDetailsCardFrame(modifier = modifier) {
        NsCardHeader(text = stringResource(R.string.wifi_connected_clients_title)) {
            Text(
                text = pluralStringResource(R.plurals.wifi_connected_clients_heard, clients.size, clients.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = NsTheme.spacing.xs),
            )
        }
        clients.take(MAX_CONNECTED_ROWS).forEach { client ->
            WifiClientCompactRow(client, onClick = { onClientClick(client) }, startPadding = 0.dp, endPadding = 0.dp)
        }
    }
}

/** How many connected clients the access point's card lists. */
private const val MAX_CONNECTED_ROWS = 5

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(labels: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        modifier = Modifier.fillMaxWidth(),
    ) {
        labels.forEach { label ->
            Surface(shape = NsTheme.shapes.chip, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = NsTheme.spacing.m, vertical = NsTheme.spacing.xs),
                )
            }
        }
    }
}
