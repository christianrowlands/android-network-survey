package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.util.WifiBand

/** Opacity of the halo drawn around a band dot. */
private const val BAND_DOT_HALO_ALPHA = 0.3f

/**
 * Colored-dot band label (e.g. a small dot followed by "5 GHz · ch 149").
 * When [channel] is null, only the band label is shown.
 */
@Composable
fun NsBandChip(
    band: WifiBand,
    channel: Int?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
) {
    val bandLabel = stringResource(band.labelRes)
    val text: AnnotatedString = if (channel != null) {
        // The channel part is formatted on its own so it can be bolded wherever the language puts it
        val channelPart = stringResource(R.string.wifi_band_channel_part, channel)
        val full = stringResource(R.string.dot_join, bandLabel, channelPart)
        val start = full.lastIndexOf(channelPart)
        buildAnnotatedString {
            append(full)
            if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, start + channelPart.length)
        }
    } else {
        AnnotatedString(bandLabel)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        NsBandDot(color = band.dotColor)
        Text(
            text = text,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** An 11 dp halo (30% alpha) around a 7 dp dot, centered. Shared by band chips and filter pills. */
@Composable
fun NsBandDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(11.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = BAND_DOT_HALO_ALPHA)),
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
