package com.craxiom.networksurvey.ui.wifi.spectrum

import com.craxiom.messaging.wifi.WifiBandwidth
import com.craxiom.networksurvey.ui.wifi.model.WifiNetworkInfo
import com.craxiom.networksurvey.ui.wifi.model.WifiRecords.wifi
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which scan records the spectrum can place, and where. */
class WifiSpectrumMapperTest {

    @Test
    fun aCompleteRecord_isPlacedByItsChannel() {
        val networks = WifiSpectrumMapper.networks(listOf(wifi("02:00:A4:11:F0:21", ssid = "Acme-Corp", rssi = -52.6f, frequencyMhz = 5180, channel = 36)))

        assertEquals(listOf(WifiNetworkInfo("Acme-Corp", -52, 36, 36, WifiBandwidth.UNKNOWN, 5180)), networks)
    }

    @Test
    fun recordsMissingASignalChannelOrFrequency_areLeftOut() {
        val networks = WifiSpectrumMapper.networks(
            listOf(
                wifi("00:00:00:00:00:01", rssi = null),
                wifi("00:00:00:00:00:02", channel = null),
                wifi("00:00:00:00:00:03", frequencyMhz = null),
                wifi("00:00:00:00:00:04", ssid = ""),
            )
        )

        assertEquals(listOf(""), networks.map { it.ssid })
    }
}
