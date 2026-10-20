package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsBandChip
import com.craxiom.networksurvey.ui.common.NsRssiColumn
import com.craxiom.networksurvey.ui.manufacturer.rememberWifiManufacturerLabelText
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.wifi.model.WifiAccessPointDisplay

/**
 * Indented child row under an expanded group. Line 1: band chip + bandwidth · standard
 * (each as its own Text with a dot separator for clear spacing).
 * Line 2: BSSID (monospace) + vendor. Right column: dBm + meter. Taps navigate to details; one
 * TalkBack item reads the radio and signal, then the BSSID.
 */
@Composable
fun WifiGroupChildRow(
    ap: WifiAccessPointDisplay,
    onClick: () -> Unit,
    onCopyBssid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vendorLabel = rememberWifiManufacturerLabelText(ap.bssid)
    Row(
        modifier = modifier
            .wifiRowSemantics(
                spoken = spokenAccessPoint(ap, vendorLabel, includeName = false),
                clickLabel = stringResource(R.string.wifi_row_open_details),
                onClick = onClick,
                copyActions = copyActions(onCopySsid = null, onCopyBssid = onCopyBssid),
            )
            .heightIn(min = NsTheme.spacing.touchTarget)
            .padding(
                start = NsTheme.spacing.screenGutter + NsTheme.spacing.m,
                end = NsTheme.spacing.screenGutter,
                top = NsTheme.spacing.s + NsTheme.spacing.xxs,
                bottom = NsTheme.spacing.s + NsTheme.spacing.xxs,
            ),
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
        ) {
            BandAndBandwidthRow(ap)
            BssidVendorRow(ap, vendorLabel, onClick = onClick, onBssidLongPress = onCopyBssid)
        }
        NsRssiColumn(rssi = ap.rssi, modifier = Modifier.clearAndSetSemantics {})
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandAndBandwidthRow(ap: WifiAccessPointDisplay) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ap.band?.let { band ->
            NsBandChip(band = band, channel = ap.channel)
        }
        val hasBandwidth = ap.bandwidthLabel.isNotBlank()
        val hasStandard = ap.standardLabel.isNotBlank()
        if (hasBandwidth) {
            SubLabel(ap.bandwidthLabel)
        }
        if (hasStandard) {
            if (hasBandwidth) {
                // The dot is grouped with the standard label so a wrap cannot strand it.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SubLabel("·")
                    SubLabel(ap.standardLabel)
                }
            } else {
                SubLabel(ap.standardLabel)
            }
        }
        if (ap.isExcluded) {
            ExcludedTag()
        }
    }
}

@Composable
private fun SubLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
