package com.craxiom.networksurvey.util

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.NsPalette

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
            GHZ_24 -> NsPalette.Band.Ghz24
            GHZ_5 -> NsPalette.Band.Ghz5
            GHZ_6 -> NsPalette.Band.Ghz6
        }

    /** "2.4 GHz", "5 GHz" or "6 GHz". */
    @get:StringRes
    val labelRes: Int
        get() = when (this) {
            GHZ_24 -> R.string.wifi_band_24_ghz
            GHZ_5 -> R.string.wifi_band_5_ghz
            GHZ_6 -> R.string.wifi_band_6_ghz
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
