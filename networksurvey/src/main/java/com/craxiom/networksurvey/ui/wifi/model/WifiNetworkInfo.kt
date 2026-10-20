package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.messaging.wifi.WifiBandwidth
import com.craxiom.networksurvey.util.WifiUtils

/** One access point as the spectrum charts draw it: where it sits in the band and how strong it is. */
data class WifiNetworkInfo(
    val ssid: String,
    val signalStrength: Int,
    val channel: Int,
    val centerChannel: Int,
    val bandwidth: WifiBandwidth,
    val frequency: Int
) {
    companion object {
        fun create(
            ssid: String,
            signalStrength: Int,
            channel: Int,
            bandwidth: WifiBandwidth,
            frequency: Int
        ): WifiNetworkInfo {
            val centerChannel = WifiUtils.getCenterChannel(channel, bandwidth, frequency)
            return WifiNetworkInfo(ssid, signalStrength, channel, centerChannel, bandwidth, frequency)
        }
    }
}
