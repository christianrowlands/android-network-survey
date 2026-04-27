package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.manufacturer.rememberWifiManufacturerLabelText
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.ui.wifi.model.WifiAccessPointDisplay
import com.craxiom.networksurvey.util.toWifiSignalCategory

/**
 * "Variant A – Calm Data" row for the flat Wi-Fi list.
 *
 * Three lines:
 *  1. SSID (accent) + optional Passpoint pill. dBm (signal-colored, tabular) on the right.
 *  2. BSSID (monospace) · vendor/status text. Meter bar on the right.
 *  3. Band chip + Security tag + Standard tag, spanning full width (wraps at large text sizes).
 */
@Composable
fun WifiRowA(
    ap: WifiAccessPointDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Line 3 (chips) lives in an outer Column row so it can span the full row width and wrap
    // at large system text sizes. Lines 1 + 2 stay in a constrained Row with the signal column.
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                TopSsidRow(ap)
                BssidVendorRow(ap)
            }
            CappedSignalCellDensity { SignalColumn(ap) }
        }
        Spacer(Modifier.height(2.dp))
        ChipsRow(ap)
    }
}

@Composable
private fun TopSsidRow(ap: WifiAccessPointDisplay) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ap.ssidIsHidden) {
            val hiddenDescription = stringResource(R.string.wifi_hidden_network_a11y)
            Text(
                text = stringResource(R.string.wifi_hidden_ssid_placeholder),
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = WifiTokens.HiddenSsid,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics { contentDescription = hiddenDescription },
            )
        } else {
            Text(
                text = ap.ssid,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = WifiTokens.SsidAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (ap.passpoint) {
            NetworkTag(
                text = stringResource(R.string.wifi_tag_passpoint),
                variant = NetworkTagVariant.Passpoint,
            )
        }
    }
}

@Composable
private fun BssidVendorRow(ap: WifiAccessPointDisplay) {
    val vendorLabel = rememberWifiManufacturerLabelText(ap.bssid)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = ap.bssid,
            style = TextStyle(
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
            ),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsRow(ap: WifiAccessPointDisplay) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ap.band?.let { band ->
            BandChip(band = band, channel = ap.channel)
        }
        if (ap.encryptionLabel.isNotBlank()) {
            NetworkTag(
                text = ap.encryptionLabel,
                variant = NetworkTagVariant.Security(classifySecurity(ap.encryptionLabel)),
            )
        }
        if (ap.standardLabel.isNotBlank()) {
            NetworkTag(
                text = ap.standardLabel,
                variant = NetworkTagVariant.Standard,
            )
        }
    }
}

@Composable
private fun SignalColumn(ap: WifiAccessPointDisplay) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.widthIn(min = 76.dp),
    ) {
        val color = ap.rssi?.toWifiSignalCategory()?.color ?: LocalContentColor.current
        Text(
            text = if (ap.rssi != null) "${ap.rssi} dBm" else "--",
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End),
            color = color,
        )
        SignalMeter(rssi = ap.rssi)
    }
}

