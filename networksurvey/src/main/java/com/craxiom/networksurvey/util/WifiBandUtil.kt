package com.craxiom.networksurvey.util

import androidx.compose.ui.graphics.Color
import com.craxiom.networksurvey.ui.theme.WifiTokens

/**
 * Wi-Fi frequency band used by the redesign for band chips and grouping.
 * 6 GHz boundary matches [WifiUtils.START_OF_6_GHZ_RANGE] (5935 MHz).
 */
enum class WifiBand {
    GHZ_24,
    GHZ_5,
    GHZ_6;

    val dotColor: Color
        get() = when (this) {
            GHZ_24 -> WifiTokens.Band24
            GHZ_5 -> WifiTokens.Band5
            GHZ_6 -> WifiTokens.Band6
        }
}

/**
 * Derives the Wi-Fi band from a frequency in MHz.
 * Returns null for frequencies outside all three bands (should be rare in practice).
 */
fun frequencyMhzToWifiBand(frequencyMhz: Int?): WifiBand? {
    if (frequencyMhz == null) return null
    return when {
        frequencyMhz in 2400..2500 -> WifiBand.GHZ_24
        frequencyMhz >= WifiUtils.START_OF_6_GHZ_RANGE -> WifiBand.GHZ_6
        frequencyMhz in 5000..5935 -> WifiBand.GHZ_5
        else -> null
    }
}
