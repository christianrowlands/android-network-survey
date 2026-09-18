package com.craxiom.networksurvey.ui.cellular.bands

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.band.BandTechnology
import com.craxiom.networksurvey.data.band.CellularBand
import com.craxiom.networksurvey.ui.theme.WifiTokens

/**
 * One band in the browsable list.
 *
 * Two lines. The first carries the designator, the common name and the frequency range, which is
 * what a reader scans for. The second carries the duplex mode, the frequency range class and the
 * channel numbers, dimmed. A leading bar colours the row by spectrum group so the list reads as
 * spectrum while scrolling.
 *
 * Bands that exist but never label a live cell are drawn with a desaturated bar and a trailing
 * chip, and stay fully tappable, because a reference table with holes in it reads as a bug.
 */
@Composable
fun BandRow(band: CellularBand, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = BandFormatting.spectrumColor(band.spectrumGroup)
    val barColor = if (band.resolvesLiveCells) accent else accent.copy(alpha = 0.35f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(barColor)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = band.designator,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                    color = if (band.resolvesLiveCells) WifiTokens.SsidAccent
                    else WifiTokens.InkFaint,
                )
                band.name?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WifiTokens.Ink,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            Text(
                text = secondLine(band),
                style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                color = WifiTokens.InkFaint,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            BandFormatting.range(band.primaryMhz)?.let {
                Text(
                    text = it,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = WifiTokens.Ink,
                    textAlign = TextAlign.End,
                )
                Text(
                    text = stringResource(R.string.band_unit_mhz),
                    style = TextStyle(fontSize = 11.sp),
                    color = WifiTokens.InkFaint,
                )
            }
            if (!band.resolvesLiveCells) {
                Text(
                    text = stringResource(R.string.band_chip_reference_only),
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    color = WifiTokens.InkFaint,
                )
            }
        }
    }
}

/**
 * Duplex, frequency range class and channel numbers, joined with a middle dot. Parts that a band
 * does not have are dropped rather than rendered empty, so an SUL band does not show a blank slot
 * where its downlink would be.
 */
private fun secondLine(band: CellularBand): String = buildList {
    band.duplex?.let { add(it.name) }
    if (band.technology == BandTechnology.NR) add(if (band.isFr2) "FR2" else "FR1")
    BandFormatting.channels(band.primaryChannels)?.let { add(it) }
}.joinToString(" · ")
