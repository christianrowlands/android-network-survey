package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.manufacturer.rememberWifiManufacturerLabelText
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.ui.util.copyTextToClipboard
import com.craxiom.networksurvey.util.frequencyMhzToWifiBand
import com.craxiom.networksurvey.util.toWifiSignalCategory

private val HeroSsidMaxFontSize = 22.sp
private val HeroSsidMinFontSize = 14.sp
private val HeroSsidAutoSizeStep = 1.sp
private val HeroDbmFontSize = 22.sp
private val HeroRowGap = 10.dp
private val HeroStackedGap = 8.dp

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
    val signalColor =
        currentRssi?.toWifiSignalCategory()?.color ?: MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeroSsidAndSignal(
                ssid = network.ssid,
                hidden = hidden,
                currentRssi = currentRssi,
                signalColor = signalColor,
            )
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
                val standardText =
                    com.craxiom.networksurvey.util.WifiUtils.formatStandard(network.standard)
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun BssidVendorRow(bssid: String) {
    val label = rememberWifiManufacturerLabelText(bssid)
    val context = LocalContext.current
    val copiedToastText = stringResource(R.string.bssid_copied)
    val copyContentDescription = stringResource(R.string.bssid_copy_content_description)
    val clipLabel = stringResource(R.string.bssid_clip_label)
    val copyBssid = { copyTextToClipboard(context, clipLabel, bssid, copiedToastText) }
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
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = copyBssid,
                ),
            )
            IconButton(
                onClick = copyBssid,
                modifier = Modifier.size(28.dp),
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

/**
 * Top section of the hero card: SSID alongside the dBm value + signal meter.
 *
 * Lays out as a single row when the SSID fits at the design font size; otherwise
 * stacks the SSID on its own full-width line (auto-shrinking from
 * [HeroSsidMaxFontSize] down to [HeroSsidMinFontSize]) with the dBm + signal bar
 * right-aligned on a second line. Long SSIDs at large font scales would otherwise
 * ellipsise, hiding the most identifying field on this screen.
 */
@Composable
private fun HeroSsidAndSignal(
    ssid: String,
    hidden: Boolean,
    currentRssi: Int?,
    signalColor: Color,
) {
    val displayedSsid = if (hidden) {
        stringResource(R.string.wifi_hidden_ssid_placeholder)
    } else {
        ssid
    }
    val dbmText = if (currentRssi != null) "$currentRssi dBm" else "-- dBm"

    val context = LocalContext.current
    val ssidClipLabel = stringResource(R.string.ssid_clip_label)
    val ssidCopiedToast = stringResource(R.string.ssid_copied)
    // Hidden SSID has no real string to copy; suppress the long-press in that case.
    val onSsidLongPress: (() -> Unit)? = if (hidden) {
        null
    } else {
        { copyTextToClipboard(context, ssidClipLabel, ssid, ssidCopiedToast) }
    }

    val ssidStyle = TextStyle(
        fontSize = HeroSsidMaxFontSize,
        fontWeight = FontWeight.SemiBold,
        fontStyle = if (hidden) FontStyle.Italic else FontStyle.Normal,
        color = if (hidden) WifiTokens.HiddenSsid else WifiTokens.SsidAccent,
    )
    val dbmStyle = TextStyle(
        fontSize = HeroDbmFontSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End,
        color = signalColor,
    )

    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val ssidWidth = measurer.measure(
            text = displayedSsid,
            style = ssidStyle,
            maxLines = 1,
            softWrap = false,
        ).size.width
        val dbmWidth = measurer.measure(
            text = dbmText,
            style = dbmStyle,
            maxLines = 1,
            softWrap = false,
        ).size.width
        val gapPx = with(LocalDensity.current) { HeroRowGap.toPx() }
        val fitsAsRow = ssidWidth + dbmWidth + gapPx <= constraints.maxWidth

        if (fitsAsRow) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HeroRowGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SsidText(
                    text = displayedSsid,
                    hidden = hidden,
                    style = ssidStyle,
                    autoSize = false,
                    onLongClick = onSsidLongPress,
                    modifier = Modifier.weight(1f),
                )
                DbmSignalColumn(
                    dbmText = dbmText,
                    dbmStyle = dbmStyle,
                    rssi = currentRssi,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(HeroStackedGap),
            ) {
                SsidText(
                    text = displayedSsid,
                    hidden = hidden,
                    style = ssidStyle,
                    autoSize = true,
                    onLongClick = onSsidLongPress,
                    modifier = Modifier.fillMaxWidth(),
                )
                DbmSignalColumn(
                    dbmText = dbmText,
                    dbmStyle = dbmStyle,
                    rssi = currentRssi,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SsidText(
    text: String,
    hidden: Boolean,
    style: TextStyle,
    autoSize: Boolean,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val a11yModifier = if (hidden) {
        val description = stringResource(R.string.wifi_hidden_network_a11y)
        Modifier.semantics { contentDescription = description }
    } else {
        Modifier
    }
    val longPressModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
    } else {
        Modifier
    }
    val combinedModifier = modifier
        .then(a11yModifier)
        .then(longPressModifier)
    if (autoSize) {
        BasicText(
            text = text,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(
                minFontSize = HeroSsidMinFontSize,
                maxFontSize = HeroSsidMaxFontSize,
                stepSize = HeroSsidAutoSizeStep,
            ),
            modifier = combinedModifier,
        )
    } else {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = combinedModifier,
        )
    }
}

@Composable
private fun DbmSignalColumn(
    dbmText: String,
    dbmStyle: TextStyle,
    rssi: Int?,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.width(IntrinsicSize.Max),
    ) {
        Text(text = dbmText, style = dbmStyle)
        SignalMeterWide(rssi = rssi)
    }
}
