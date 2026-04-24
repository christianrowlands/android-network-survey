package com.craxiom.networksurvey.util

import androidx.compose.ui.graphics.Color
import com.craxiom.networksurvey.ui.theme.WifiTokens

/**
 * Signal strength bucket for the Wi-Fi list and details redesign.
 *
 * Thresholds come from the handoff spec (dBm):
 *  - STRONG: >= -55
 *  - GOOD:   -56..-67
 *  - FAIR:   -68..-75
 *  - WEAK:   <= -76
 *
 * The cellular 5-bucket ramp in [ColorUtils] is intentionally not shared; that one
 * has different cut points and lives on different screens.
 */
enum class WifiSignalCategory {
    STRONG,
    GOOD,
    FAIR,
    WEAK;

    val color: Color
        get() = when (this) {
            STRONG -> WifiTokens.SignalStrong
            GOOD -> WifiTokens.SignalGood
            FAIR -> WifiTokens.SignalFair
            WEAK -> WifiTokens.SignalWeak
        }
}

fun Int.toWifiSignalCategory(): WifiSignalCategory = when {
    this >= -55 -> WifiSignalCategory.STRONG
    this >= -67 -> WifiSignalCategory.GOOD
    this >= -75 -> WifiSignalCategory.FAIR
    else -> WifiSignalCategory.WEAK
}

fun Float.toWifiSignalCategory(): WifiSignalCategory = this.toInt().toWifiSignalCategory()

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

fun wifiSignalMeterFraction(rssi: Float): Float = wifiSignalMeterFraction(rssi.toInt())
