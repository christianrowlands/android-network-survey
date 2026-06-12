package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.util.WifiBand

/**
 * Colored-dot band label (e.g. a small teal dot followed by "5 GHz · ch 149").
 * When [channel] is null, only the band label is shown.
 */
@Composable
fun BandChip(
    band: WifiBand,
    channel: Int?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle(fontSize = 11.sp),
) {
    val bandLabel = stringResource(
        when (band) {
            WifiBand.GHZ_24 -> R.string.wifi_band_24_ghz
            WifiBand.GHZ_5 -> R.string.wifi_band_5_ghz
            WifiBand.GHZ_6 -> R.string.wifi_band_6_ghz
        }
    )
    val text: AnnotatedString = if (channel != null) {
        val full = stringResource(R.string.wifi_band_with_channel, bandLabel, channel)
        val token = "ch $channel"
        val start = full.lastIndexOf(token)
        buildAnnotatedString {
            append(full)
            if (start >= 0) {
                addStyle(
                    SpanStyle(fontWeight = FontWeight.SemiBold),
                    start,
                    start + token.length,
                )
            }
        }
    } else {
        AnnotatedString(bandLabel)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        BandDot(color = band.dotColor)
        Text(
            text = text,
            style = textStyle,
            color = WifiTokens.InkMuted,
        )
    }
}

// 11-dp outer halo (30% alpha) + 7-dp inner dot, centered. Internal so the filter sheet's
// band pills can reuse the exact same dot treatment.
@Composable
internal fun BandDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(11.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}
