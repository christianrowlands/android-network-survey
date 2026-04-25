package com.craxiom.networksurvey.util

import androidx.compose.ui.graphics.Color
import com.craxiom.networksurvey.ui.theme.WifiTokens

/**
 * Signal strength bucket for the Wi-Fi list and details UI.
 *
 * Thresholds and colors intentionally mirror [ColorUtils.getColorForSignalStrength] so WiFi
 * rows match the cellular and Bluetooth screens app-wide.
 */
enum class WifiSignalCategory {
    STRONG,
    GOOD,
    FAIR,
    WEAK,
    VERY_WEAK;

    val color: Color
        get() = when (this) {
            STRONG -> WifiTokens.SignalStrong
            GOOD -> WifiTokens.SignalGood
            FAIR -> WifiTokens.SignalFair
            WEAK -> WifiTokens.SignalWeak
            VERY_WEAK -> WifiTokens.SignalVeryWeak
        }
}

fun Int.toWifiSignalCategory(): WifiSignalCategory = when {
    this > -60 -> WifiSignalCategory.STRONG
    this > -70 -> WifiSignalCategory.GOOD
    this > -80 -> WifiSignalCategory.FAIR
    this > -90 -> WifiSignalCategory.WEAK
    else -> WifiSignalCategory.VERY_WEAK
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
