package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.textColor
import com.craxiom.networksurvey.util.toWifiSignalCategory

/** Keeps the dBm column one width down a list, so the meters line up. */
private val RSSI_COLUMN_MIN_WIDTH = 76.dp

/**
 * The right side of a Signals list row: "-63 dBm" in its signal bucket's text color over an
 * [NsSignalMeter], or "--" over an empty meter when the record carries no signal.
 */
@Composable
fun NsRssiColumn(rssi: Int?, modifier: Modifier = Modifier) {
    val category = rssi?.toWifiSignalCategory()
    NsRssiColumn(
        text = if (rssi != null) stringResource(R.string.dbm_value, rssi.toString()) else stringResource(R.string.signal_none),
        textColor = category?.textColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    ) {
        NsSignalMeter(rssi = rssi)
    }
}

/**
 * The same column for a reading that is not a Wi-Fi RSSI (a cellular RSRP, for example), with the
 * caller's text, color and meter fill.
 */
@Composable
fun NsRssiColumn(
    text: String,
    textColor: Color,
    fraction: Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    NsRssiColumn(text = text, textColor = textColor, modifier = modifier) {
        NsSignalMeter(fraction = fraction, color = fillColor, contentDescription = null)
    }
}

@Composable
private fun NsRssiColumn(text: String, textColor: Color, modifier: Modifier, meter: @Composable () -> Unit) {
    Column(
        modifier = modifier.widthIn(min = RSSI_COLUMN_MIN_WIDTH),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s - NsTheme.spacing.xxs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            color = textColor,
            maxLines = 1,
        )
        meter()
    }
}
