package com.craxiom.networksurvey.model

import android.os.Parcel
import com.craxiom.messaging.wifi.Standard
import com.craxiom.messaging.wifi.WifiBandwidth
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Wi-Fi details page gets its network through the navigation saved state, which is parceled
 * when Android kills the app in the background; nothing may be lost on the way.
 */
@RunWith(RobolectricTestRunner::class)
class WifiNetworkParcelTest {

    @Test
    fun aParcelRoundTrip_keepsEveryField() {
        val network = WifiNetwork(
            bssid = "02:00:A4:11:F0:21",
            signalStrength = -52f,
            ssid = "Acme-Corp",
            frequency = 5180,
            channel = 36,
            bandwidth = WifiBandwidth.MHZ_80,
            encryptionType = "WPA2",
            passpoint = true,
            capabilities = "[WPA2-PSK-CCMP][ESS]",
            standard = Standard.IEEE80211AX,
        )

        assertEquals(network, roundTrip(network))
    }

    @Test
    fun missingValues_comeBackMissing() {
        val network = WifiNetwork("02:00:70:20:1D:55", null, "", null, null, null, "", null, "", null)

        assertEquals(network.copy(bandwidth = WifiBandwidth.UNKNOWN, standard = Standard.UNKNOWN), roundTrip(network))
    }

    private fun roundTrip(network: WifiNetwork): WifiNetwork {
        val parcel = Parcel.obtain()
        try {
            network.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return WifiNetwork.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
