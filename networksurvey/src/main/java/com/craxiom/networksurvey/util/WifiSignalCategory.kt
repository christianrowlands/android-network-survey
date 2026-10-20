package com.craxiom.networksurvey.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.craxiom.networksurvey.ui.theme.NsPalette
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * Signal strength bucket for the Wi-Fi list and details UI.
 *
 * Colors come from the shared [NsPalette.Signal] ramp so Wi-Fi rows match the cellular and
 * Bluetooth screens app-wide.
 */
enum class WifiSignalCategory {
    STRONG,
    GOOD,
    FAIR,
    WEAK,
    VERY_WEAK;

    val color: Color
        get() = when (this) {
            STRONG -> NsPalette.Signal.Strong
            GOOD -> NsPalette.Signal.Good
            FAIR -> NsPalette.Signal.Fair
            WEAK -> NsPalette.Signal.Weak
            VERY_WEAK -> NsPalette.Signal.VeryWeak
        }
}

/**
 * The color for text (a dBm readout) in this bucket, readable on the current scheme's panels.
 * [WifiSignalCategory.color] is the fill color for meters, dots and map layers.
 */
val WifiSignalCategory.textColor: Color
    @Composable
    @ReadOnlyComposable
    get() = with(NsTheme.colors.signalText) {
        when (this@textColor) {
            WifiSignalCategory.STRONG -> strong
            WifiSignalCategory.GOOD -> good
            WifiSignalCategory.FAIR -> fair
            WifiSignalCategory.WEAK -> weak
            WifiSignalCategory.VERY_WEAK -> veryWeak
        }
    }

fun Int.toWifiSignalCategory(): WifiSignalCategory = when {
    this > -60 -> WifiSignalCategory.STRONG
    this > -70 -> WifiSignalCategory.GOOD
    this > -80 -> WifiSignalCategory.FAIR
    this > -90 -> WifiSignalCategory.WEAK
    else -> WifiSignalCategory.VERY_WEAK
}

private const val METER_MIN_DBM = -95
private const val METER_MAX_DBM = -30

/**
 * Maps a dBm value to a 0f..1f fill ratio for the meter bar.
 * -95 dBm -> 0f, -30 dBm -> 1f, clamped at both ends.
 */
fun wifiSignalMeterFraction(rssi: Int): Float {
    val numerator = (rssi - METER_MIN_DBM).toFloat()
    val denominator = (METER_MAX_DBM - METER_MIN_DBM).toFloat()
    return (numerator / denominator).coerceIn(0f, 1f)
}
