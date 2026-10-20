package com.craxiom.networksurvey.ui.wifi.spectrum

import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.craxiom.networksurvey.ui.wifi.model.WifiNetworkInfo

/** Turns a Wi-Fi scan into what the spectrum charts draw. Pure, so it is tested on the JVM. */
object WifiSpectrumMapper {
    /**
     * @return One entry per record that can be placed on a chart: it has a signal, an SSID (empty
     * for a hidden network), a channel and a frequency.
     */
    fun networks(records: List<WifiRecordWrapper>): List<WifiNetworkInfo> = records
        .map { it.wifiBeaconRecord.data }
        .filter { it.hasSignalStrength() && it.ssid != null && it.hasChannel() && it.hasFrequencyMhz() }
        .map { WifiNetworkInfo.create(it.ssid, it.signalStrength.value.toInt(), it.channel.value, it.bandwidth, it.frequencyMhz.value) }
}
