package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.ui.manufacturer.rememberWifiManufacturerLabelText
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.ui.wifi.model.WifiAccessPointDisplay
import com.craxiom.networksurvey.util.toWifiSignalCategory

/**
 * Indented child row under an expanded group. Line 1: band chip + bandwidth · standard
 * (each as its own Text with a dot separator for clear spacing).
 * Line 2: BSSID (monospace) + vendor. Right column: dBm + meter. Taps navigate to details.
 */
@Composable
fun WifiGroupChildRow(
    ap: WifiAccessPointDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(start = 28.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            BandAndBandwidthRow(ap)
            BssidVendorRow(ap)
        }
        CappedSignalCellDensity { SignalCell(ap) }
    }
}

@Composable
private fun BandAndBandwidthRow(ap: WifiAccessPointDisplay) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ap.band?.let { band ->
            BandChip(band = band, channel = ap.channel)
        }
        val hasBandwidth = ap.bandwidthLabel.isNotBlank()
        val hasStandard = ap.standardLabel.isNotBlank()
        if (hasBandwidth) {
            SubLabel(ap.bandwidthLabel)
        }
        if (hasBandwidth && hasStandard) {
            Text(
                text = "·",
                style = TextStyle(fontSize = 12.sp),
                color = WifiTokens.InkFaint,
            )
        }
        if (hasStandard) {
            SubLabel(ap.standardLabel)
        }
    }
}

@Composable
private fun SubLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
        color = WifiTokens.InkFaint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun BssidVendorRow(ap: WifiAccessPointDisplay) {
    val vendorLabel = rememberWifiManufacturerLabelText(ap.bssid)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 3.dp),
    ) {
        Text(
            text = ap.bssid,
            style = TextStyle(fontSize = 11.5.sp, fontFamily = FontFamily.Monospace),
            color = WifiTokens.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        vendorLabel.text?.let { text ->
            Text(
                text = "·",
                style = TextStyle(fontSize = 11.5.sp),
                color = WifiTokens.InkFaint,
            )
            Text(
                text = text,
                style = TextStyle(
                    fontSize = 11.5.sp,
                    fontStyle = if (vendorLabel.italic) FontStyle.Italic else FontStyle.Normal,
                ),
                color = if (vendorLabel.italic) WifiTokens.InkFaint else WifiTokens.InkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SignalCell(ap: WifiAccessPointDisplay) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.widthIn(min = 64.dp),
    ) {
        val color = ap.rssi?.toWifiSignalCategory()?.color ?: LocalContentColor.current
        Text(
            text = if (ap.rssi != null) "${ap.rssi} dBm" else "--",
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End),
            color = color,
        )
        SignalMeter(rssi = ap.rssi, width = 56.dp)
    }
}
