package com.craxiom.networksurvey.ui.wifi.clients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.clients.ScoutClient
import com.craxiom.networksurvey.ui.common.NsChevron
import com.craxiom.networksurvey.ui.common.relativeAgeText
import com.craxiom.networksurvey.ui.manufacturer.rememberWifiManufacturerLabelText
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.textColor
import com.craxiom.networksurvey.util.toWifiSignalCategory

/**
 * One row of the Clients list: the MAC address (monospace) with a "Randomized" tag, then the
 * manufacturer (for an address that is not randomized) and what the client is doing, with the
 * signal and how long ago it was heard on the right. One TalkBack item that reads the address in
 * pairs; a tap opens Client details.
 *
 * @param ssidOf The SSID the Wi-Fi list knows for a BSSID, or null.
 */
@Composable
fun WifiClientRow(client: ScoutClient, nowMs: Long, ssidOf: (String) -> String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val vendor = if (client.locallyAdministered) null else rememberWifiManufacturerLabelText(client.mac).text
    val activity = clientActivityText(client, ssidOf)
    val heard = relativeAgeText(client.lastHeardMs, nowMs)
    val dbm = stringResource(R.string.dbm_value, client.rssi.toString())
    val spoken = listOfNotNull(
        stringResource(R.string.wifi_client_address_spoken, WifiClientsMapper.spokenMac(client.mac)),
        stringResource(R.string.wifi_client_tag_randomized).takeIf { client.locallyAdministered },
        vendor,
        activity,
        dbm,
        heard,
    ).joinToString(", ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.wifi_client_open_details), onClick = onClick)
            .semantics { contentDescription = spoken }
            .heightIn(min = NsTheme.spacing.touchTarget)
            .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        Column(Modifier.weight(1f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs)) {
            ClientAddress(client, NsTheme.textStyles.mono)
            Text(
                text = listOfNotNull(vendor, activity).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs)) {
            DbmText(client.rssi)
            Text(text = heard, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        NsChevron()
    }
}

/**
 * A short client row (address, "Randomized" tag, signal, chevron) for an access point's Connected
 * clients and an open "network looked for" row.
 *
 * @param startPadding The row's start inset; an open network's clients sit indented under it.
 * @param endPadding The row's end inset; none inside a card, which has its own.
 */
@Composable
fun WifiClientCompactRow(
    client: ScoutClient,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    startPadding: Dp = NsTheme.spacing.screenGutter,
    endPadding: Dp = NsTheme.spacing.screenGutter,
) {
    val spoken = listOfNotNull(
        stringResource(R.string.wifi_client_address_spoken, WifiClientsMapper.spokenMac(client.mac)),
        stringResource(R.string.wifi_client_tag_randomized).takeIf { client.locallyAdministered },
        stringResource(R.string.dbm_value, client.rssi.toString()),
    ).joinToString(", ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.wifi_client_open_details), onClick = onClick)
            .semantics { contentDescription = spoken }
            .heightIn(min = NsTheme.spacing.touchTarget)
            .padding(start = startPadding, end = endPadding, top = NsTheme.spacing.s, bottom = NsTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        ClientAddress(client, NsTheme.textStyles.mono, Modifier.weight(1f).clearAndSetSemantics {})
        Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
            DbmText(client.rssi)
        }
        NsChevron()
    }
}

/** "-51 dBm" in the signal's text color. */
@Composable
internal fun DbmText(rssi: Int) {
    Text(
        text = stringResource(R.string.dbm_value, rssi.toString()),
        style = MaterialTheme.typography.labelLarge,
        color = rssi.toWifiSignalCategory().textColor,
        maxLines = 1,
    )
}
