package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.util.WifiSignalCategory
import com.craxiom.networksurvey.util.toWifiSignalCategory
import com.craxiom.networksurvey.util.wifiSignalMeterFraction

/**
 * Horizontal signal strength meter: a rounded track with a signal-colored fill proportional
 * to the RSSI. Fixed-dp sizing (track height in dp, fill width in dp) so it does not scale
 * with system text size; typography elsewhere in the row does.
 *
 * Accessibility: announces "Signal strength: -54 dBm, strong" (or "No signal" if rssi null).
 */
@Composable
fun SignalMeter(
    rssi: Int?,
    modifier: Modifier = Modifier,
    width: Dp = 72.dp,
    height: Dp = 4.dp,
) {
    val fraction = rssi?.let { wifiSignalMeterFraction(it) } ?: 0f
    val category = rssi?.toWifiSignalCategory()
    val fillColor = category?.color ?: MaterialTheme.colorScheme.onSurfaceVariant
    val contentDescription = if (rssi != null && category != null) {
        stringResource(
            R.string.wifi_signal_strength_description,
            rssi,
            stringResource(category.descriptionResId)
        )
    } else {
        stringResource(R.string.wifi_no_signal_description)
    }

    Box(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(WifiTokens.InkFaint.copy(alpha = 0.35f))
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(fillColor)
            )
        }
    }
}

/**
 * Full-width variant used in the Details hero; 6 dp tall (vs 4 dp for rows).
 */
@Composable
fun SignalMeterWide(
    rssi: Int?,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val fraction = rssi?.let { wifiSignalMeterFraction(it) } ?: 0f
    val category = rssi?.toWifiSignalCategory()
    val fillColor = category?.color ?: MaterialTheme.colorScheme.onSurfaceVariant
    val contentDescription = if (rssi != null && category != null) {
        stringResource(
            R.string.wifi_signal_strength_description,
            rssi,
            stringResource(category.descriptionResId)
        )
    } else {
        stringResource(R.string.wifi_no_signal_description)
    }

    Box(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(WifiTokens.InkFaint.copy(alpha = 0.35f))
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(fillColor)
            )
        }
    }
}

private val WifiSignalCategory.descriptionResId: Int
    get() = when (this) {
        WifiSignalCategory.STRONG -> R.string.wifi_signal_strong
        WifiSignalCategory.GOOD -> R.string.wifi_signal_good
        WifiSignalCategory.FAIR -> R.string.wifi_signal_fair
        WifiSignalCategory.WEAK -> R.string.wifi_signal_weak
    }
