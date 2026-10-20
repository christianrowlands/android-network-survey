package com.craxiom.networksurvey.ui.wifi.spectrum

import androidx.annotation.StringRes
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_2_4_GHZ
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_2_4_GHZ_CHART_VIEW
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_5_GHZ_GROUP_1
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_5_GHZ_GROUP_1_CHART_VIEW
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_5_GHZ_GROUP_2
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_5_GHZ_GROUP_2_CHART_VIEW
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_5_GHZ_GROUP_3
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_5_GHZ_GROUP_3_CHART_VIEW
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_6_GHZ
import com.craxiom.networksurvey.ui.wifi.model.CHANNELS_6_GHZ_CHART_VIEW

/**
 * One chart of the spectrum page: its title, the channel range on its x axis, the channels it
 * labels and how often.
 */
enum class SpectrumBand(
    @StringRes val title: Int,
    val chartView: List<Int>,
    val channels: List<Float>,
    val labelInterval: Int = 1,
) {
    GHZ_24(R.string.wifi_spectrum_band_24, CHANNELS_2_4_GHZ_CHART_VIEW, CHANNELS_2_4_GHZ),
    GHZ_5_LOW(R.string.wifi_spectrum_band_5_low, CHANNELS_5_GHZ_GROUP_1_CHART_VIEW, CHANNELS_5_GHZ_GROUP_1),
    GHZ_5_MID(R.string.wifi_spectrum_band_5_mid, CHANNELS_5_GHZ_GROUP_2_CHART_VIEW, CHANNELS_5_GHZ_GROUP_2, labelInterval = 2),
    GHZ_5_HIGH(R.string.wifi_spectrum_band_5_high, CHANNELS_5_GHZ_GROUP_3_CHART_VIEW, CHANNELS_5_GHZ_GROUP_3),
    GHZ_6(R.string.wifi_spectrum_band_6, CHANNELS_6_GHZ_CHART_VIEW, CHANNELS_6_GHZ, labelInterval = 8),
}
