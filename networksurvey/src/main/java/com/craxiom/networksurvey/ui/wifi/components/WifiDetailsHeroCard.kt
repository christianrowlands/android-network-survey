package com.craxiom.networksurvey.ui.wifi.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.manufacturer.rememberWifiManufacturerLabelText
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.util.frequencyMhzToWifiBand
import com.craxiom.networksurvey.util.toWifiSignalCategory

/**
 * Hero card at the top of the Wi-Fi Details screen: big SSID + large dBm, full-width meter,
 * BSSID · vendor line, and a wrapping tag row (band chip, security, standard, passpoint).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WifiDetailsHeroCard(
    network: WifiNetwork,
    currentRssi: Int?,
    modifier: Modifier = Modifier,
) {
    val hidden = network.ssid.isEmpty()
    val band = frequencyMhzToWifiBand(network.frequency)
    val signalColor = currentRssi?.toWifiSignalCategory()?.color ?: MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (hidden) {
                    val hiddenDescription = stringResource(R.string.wifi_hidden_network_a11y)
                    Text(
                        text = stringResource(R.string.wifi_hidden_ssid_placeholder),
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                        color = WifiTokens.HiddenSsid,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = hiddenDescription },
                    )
                } else {
                    Text(
                        text = network.ssid,
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                        color = WifiTokens.SsidAccent,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = if (currentRssi != null) "$currentRssi dBm" else "-- dBm",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    ),
                    color = signalColor,
                )
            }
            SignalMeterWide(rssi = currentRssi)
            BssidVendorRow(bssid = network.bssid)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (band != null) {
                    BandChip(band = band, channel = network.channel)
                }
                if (network.encryptionType.isNotBlank()) {
                    NetworkTag(
                        text = network.encryptionType,
                        variant = NetworkTagVariant.Security(classifySecurity(network.encryptionType)),
                    )
                }
                val standardText = com.craxiom.networksurvey.util.WifiUtils.formatStandard(network.standard)
                if (standardText.isNotBlank()) {
                    NetworkTag(text = standardText, variant = NetworkTagVariant.Standard)
                }
                if (network.passpoint == true) {
                    NetworkTag(
                        text = stringResource(R.string.wifi_tag_passpoint),
                        variant = NetworkTagVariant.Passpoint,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BssidVendorRow(bssid: String) {
    val label = rememberWifiManufacturerLabelText(bssid)
    val context = LocalContext.current
    val copiedToastText = stringResource(R.string.bssid_copied)
    val copyContentDescription = stringResource(R.string.bssid_copy_content_description)
    val clipLabel = stringResource(R.string.bssid_clip_label)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = bssid,
                style = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                color = WifiTokens.InkDim,
            )
            IconButton(
                onClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, bssid))
                    Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
                },
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_content_copy),
                    contentDescription = copyContentDescription,
                    tint = WifiTokens.InkDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        label.text?.let { text ->
            Text(
                text = text,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontStyle = if (label.italic) FontStyle.Italic else FontStyle.Normal,
                ),
                color = if (label.italic) WifiTokens.InkFaint else WifiTokens.InkDim,
            )
        }
    }
}
