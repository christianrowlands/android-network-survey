package com.craxiom.networksurvey.ui.common

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.util.WifiSignalCategory
import com.craxiom.networksurvey.util.toWifiSignalCategory
import com.craxiom.networksurvey.util.wifiSignalMeterFraction

/** Opacity of the meter's empty track, over the muted ink color. */
private const val TRACK_ALPHA = 0.35f

/**
 * Horizontal signal strength meter: a rounded track with a signal-colored fill proportional to
 * the RSSI. Fixed-dp sizing so it does not scale with system text size; the row's text does.
 *
 * Accessibility: announces "Signal strength: -54 dBm, strong" (or "No signal" if rssi is null).
 */
@Composable
fun NsSignalMeter(
    rssi: Int?,
    modifier: Modifier = Modifier,
    width: Dp = 72.dp,
    height: Dp = 4.dp,
) {
    SignalMeterTrack(rssi = rssi, height = height, modifier = modifier.width(width))
}

/** Full-width variant used in details heroes; 6 dp tall (vs 4 dp for rows). */
@Composable
fun NsSignalMeterWide(
    rssi: Int?,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    SignalMeterTrack(rssi = rssi, height = height, modifier = modifier.fillMaxWidth())
}

/**
 * A signal meter for a reading that is not a Wi-Fi RSSI (a cellular RSRP, for example): the caller
 * has already worked out how full it is and its color.
 *
 * @param fraction           How full the meter is, 0 to 1.
 * @param contentDescription What TalkBack says, or null when the row around it already says it.
 */
@Composable
fun NsSignalMeter(
    fraction: Float,
    color: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    width: Dp = 72.dp,
    height: Dp = 4.dp,
) {
    MeterTrack(fraction, color, contentDescription, height, modifier.width(width))
}

@Composable
private fun SignalMeterTrack(rssi: Int?, height: Dp, modifier: Modifier) {
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
    MeterTrack(fraction, fillColor, contentDescription, height, modifier)
}

@Composable
private fun MeterTrack(fraction: Float, fillColor: Color, contentDescription: String?, height: Dp, modifier: Modifier) {
    Box(
        modifier = modifier
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TRACK_ALPHA))
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
        WifiSignalCategory.VERY_WEAK -> R.string.wifi_signal_very_weak
    }
