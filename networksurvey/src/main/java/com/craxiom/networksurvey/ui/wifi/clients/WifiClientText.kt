package com.craxiom.networksurvey.ui.wifi.clients

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.craxiom.messaging.wifi.ClientFrameType
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.clients.ScoutClient
import com.craxiom.networksurvey.ui.common.NetworkTagVariant
import com.craxiom.networksurvey.ui.common.NsNetworkTag
import com.craxiom.networksurvey.ui.theme.NsTheme

/** The words for a kind of frame a client was heard sending, or null for an unknown kind. */
@StringRes
fun ClientFrameType.labelRes(): Int? = when (this) {
    ClientFrameType.DATA -> R.string.wifi_frame_data
    ClientFrameType.NULL_DATA -> R.string.wifi_frame_null_data
    ClientFrameType.PROBE_REQUEST -> R.string.wifi_frame_probe_request
    ClientFrameType.ASSOCIATION_REQUEST -> R.string.wifi_frame_association_request
    ClientFrameType.REASSOCIATION_REQUEST -> R.string.wifi_frame_reassociation_request
    ClientFrameType.AUTHENTICATION -> R.string.wifi_frame_authentication
    ClientFrameType.ACTION -> R.string.wifi_frame_action
    ClientFrameType.UNKNOWN, ClientFrameType.UNRECOGNIZED -> null
}

/**
 * What a client is doing, in one line: "Connected to Toast" (the access point's SSID, or its BSSID
 * when the list has not heard it), else "Looking for eduroam, HOME-5G", else that only its frames
 * were heard.
 *
 * @param ssidOf The SSID the Wi-Fi list knows for a BSSID, or null.
 */
@Composable
fun clientActivityText(client: ScoutClient, ssidOf: (String) -> String?): String {
    val bssid = client.bssid
    return when {
        bssid != null -> stringResource(R.string.wifi_client_connected_to, WifiClientsMapper.networkName(bssid, ssidOf))
        client.probedSsids.isNotEmpty() -> stringResource(
            R.string.wifi_client_looking_for,
            client.probedSsids.asReversed().joinToString(stringResource(R.string.wifi_client_list_separator)),
        )

        else -> stringResource(R.string.wifi_client_heard_only)
    }
}

/**
 * A client's MAC address in the monospace style, with the "Randomized" text tag after it when the
 * address is locally administered (text, so color is never the only sign).
 */
@Composable
fun ClientAddress(client: ScoutClient, style: TextStyle, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
    ) {
        Text(
            text = client.mac,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (client.locallyAdministered) RandomizedTag()
    }
}

/** The "Randomized" tag for a locally administered address. */
@Composable
fun RandomizedTag(modifier: Modifier = Modifier) {
    NsNetworkTag(text = stringResource(R.string.wifi_client_tag_randomized), variant = NetworkTagVariant.Standard, modifier = modifier)
}
