package com.craxiom.networksurvey.ui.cellular.bands

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.band.BandTechnology
import com.craxiom.networksurvey.data.band.CellularBand
import com.craxiom.networksurvey.ui.theme.WifiTokens

/**
 * Everything known about one band, opened by tapping a row or a search result.
 *
 * Row order is deliberate: identity, then the frequency range, then the ambiguity warning where
 * one applies, then channels, then uplink, then the rest. Identity, frequency and the warning are
 * the reasons the sheet exists, so they come before anything that is merely useful.
 *
 * Uplink rows are omitted entirely on TDD bands rather than repeating the downlink, and on SDL
 * bands, which have no uplink at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BandDetailSheet(band: CellularBand, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        BandDetailContent(band)
    }
}

/**
 * The body of the band detail, without any container.
 *
 * Separated from [BandDetailSheet] because the cellular details screen reaches this content from
 * an XML fragment, where a modal bottom sheet would mean nesting one dialog window inside
 * another. That entry point hosts the same content in a dialog instead, and both stay identical
 * because there is only one of them.
 */
@Composable
fun BandDetailContent(
    band: CellularBand,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Header(band)
            band.referenceOnlyReason?.let {
                Explainer(stringResource(R.string.band_reference_only_explainer, it))
            }
            if (band.isFullyShadowed) {
                Explainer(
                    stringResource(
                        R.string.band_never_resolvable,
                        band.designator,
                        band.shadowedBy.joinToString(", ") { "n$it" },
                    )
                )
            }

            BandFormatting.range(band.primaryMhz)?.let {
                val label = if (band.downlinkMhz != null) R.string.band_detail_range
                else R.string.band_detail_uplink
                Field(stringResource(label), "$it ${stringResource(R.string.band_unit_mhz)}")
            }
            band.widthMhz?.let {
                Field(stringResource(R.string.band_detail_width), "${BandFormatting.mhz(it)} ${stringResource(R.string.band_unit_mhz)}")
            }
            band.duplex?.let { Field(stringResource(R.string.band_detail_duplex), it.name) }

            // Channel numbers for whichever direction identifies the band. An SUL band has no
            // downlink, so keying this off downlinkChannels would leave it with no channel row
            // at all even though search matches on exactly those numbers.
            val primaryChannels = BandFormatting.channels(band.primaryChannels)
            if (primaryChannels != null) {
                val label = if (band.downlinkChannels != null) R.string.band_detail_downlink
                else R.string.band_detail_channels
                Field(stringResource(label), primaryChannels)
            } else {
                Explainer(stringResource(R.string.band_detail_no_channel_data))
            }

            if (isPaired(band)) {
                // Spacing first: it is one number and it is what characterises a paired band at a
                // glance, where the raw uplink edges are a second lookup.
                BandFormatting.duplexSpacingMhz(band)?.let {
                    Field(
                        stringResource(R.string.band_detail_duplex_spacing),
                        "${BandFormatting.mhz(it)} ${stringResource(R.string.band_unit_mhz)}",
                    )
                }
                BandFormatting.range(band.uplinkMhz)?.let {
                    Field(
                        stringResource(R.string.band_detail_uplink),
                        "$it ${stringResource(R.string.band_unit_mhz)}",
                    )
                }
            }
            // Shown for paired bands and for SUL alike, but not when the uplink channels are
            // already the primary channel row above.
            if (band.uplinkChannels != null && band.uplinkChannels != band.primaryChannels) {
                BandFormatting.channels(band.uplinkChannels)?.let {
                    Field(stringResource(R.string.band_detail_channels), it)
                }
            }

            BandFormatting.bandwidths(band.bandwidthsMhz)?.let {
                Field(
                    stringResource(R.string.band_detail_bandwidths),
                    "$it ${stringResource(R.string.band_unit_mhz)}",
                )
            }
            band.subcarrierSpacingsKhz.takeIf { it.isNotEmpty() }?.let {
                Field(
                    stringResource(R.string.band_detail_scs),
                    "${it.joinToString(", ")} ${stringResource(R.string.band_unit_khz)}",
                )
            }
        }
}

/**
 * True only for genuinely paired spectrum.
 *
 * A TDD band transmits both directions on the same range, so an uplink row would repeat the
 * downlink. An SUL band has no downlink at all, which means the sheet's primary range row is
 * already its uplink and a second uplink row would say the same thing twice.
 */
private fun isPaired(band: CellularBand): Boolean =
    band.downlinkMhz != null && band.uplinkMhz != null && band.uplinkMhz != band.downlinkMhz

@Composable
private fun Header(band: CellularBand) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = band.designator,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                ),
                color = WifiTokens.SsidAccent,
            )
            band.name?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = WifiTokens.Ink,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BandFormatting.spectrumColor(band.spectrumGroup))
            )
            Text(
                text = subtitle(band),
                style = TextStyle(fontSize = 12.sp),
                color = WifiTokens.InkFaint,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun subtitle(band: CellularBand): String = buildList {
    add(BandFormatting.spectrumLabel(band.spectrumGroup))
    add(if (band.technology == BandTechnology.NR) "5G NR" else "LTE")
    if (band.technology == BandTechnology.NR) {
        add(stringResource(if (band.isFr2) R.string.band_fr2 else R.string.band_fr1))
    }
}.joinToString(" · ")

@Composable
private fun Field(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = TextStyle(fontSize = 13.sp),
            color = WifiTokens.InkFaint,
            // Weighted rather than a fixed width so the value keeps room on a narrow screen or
            // at a large font scale, where a fixed label column squeezed it to nothing.
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = value,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            color = WifiTokens.Ink,
            modifier = Modifier
                .weight(0.55f)
                .padding(start = 8.dp),
        )
    }
}

@Composable
private fun Explainer(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 12.sp),
        color = WifiTokens.InkMuted,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(10.dp),
    )
}
