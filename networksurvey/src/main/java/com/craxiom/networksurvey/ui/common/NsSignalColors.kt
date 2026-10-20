package com.craxiom.networksurvey.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.NsPalette
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.SignalBuckets

/** The text color for a signal reading in a [SignalBuckets] bucket; plain ink when unknown. */
@Composable
fun signalTextColor(bucket: Int?): Color {
    val colors = NsTheme.colors.signalText
    return when (bucket) {
        SignalBuckets.STRONG -> colors.strong
        SignalBuckets.GOOD -> colors.good
        SignalBuckets.FAIR -> colors.fair
        SignalBuckets.WEAK -> colors.weak
        SignalBuckets.VERY_WEAK -> colors.veryWeak
        else -> MaterialTheme.colorScheme.onSurface
    }
}

/** The fill color (meters, dots) for a signal in a [SignalBuckets] bucket; muted when unknown. */
@Composable
fun signalFillColor(bucket: Int?): Color = when (bucket) {
    SignalBuckets.STRONG -> NsPalette.Signal.Strong
    SignalBuckets.GOOD -> NsPalette.Signal.Good
    SignalBuckets.FAIR -> NsPalette.Signal.Fair
    SignalBuckets.WEAK -> NsPalette.Signal.Weak
    SignalBuckets.VERY_WEAK -> NsPalette.Signal.VeryWeak
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** The word TalkBack says for a signal bucket ("strong"), or null when unknown. */
@StringRes
fun signalBucketWord(bucket: Int?): Int? = when (bucket) {
    SignalBuckets.STRONG -> R.string.wifi_signal_strong
    SignalBuckets.GOOD -> R.string.wifi_signal_good
    SignalBuckets.FAIR -> R.string.wifi_signal_fair
    SignalBuckets.WEAK -> R.string.wifi_signal_weak
    SignalBuckets.VERY_WEAK -> R.string.wifi_signal_very_weak
    else -> null
}
