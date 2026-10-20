package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.messaging.WifiBeaconRecord
import com.craxiom.messaging.WifiBeaconRecordData
import com.craxiom.messaging.wifi.EncryptionType
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.google.protobuf.FloatValue
import com.google.protobuf.Int32Value

/** Wi-Fi scan records for the Wi-Fi list, details and spectrum tests. */
object WifiRecords {
    fun wifi(
        bssid: String,
        ssid: String = "Acme-Corp",
        rssi: Float? = -60f,
        frequencyMhz: Int? = 5180,
        channel: Int? = 36,
        encryption: EncryptionType = EncryptionType.WPA2,
    ): WifiRecordWrapper {
        val data = WifiBeaconRecordData.newBuilder()
            .setBssid(bssid)
            .setSsid(ssid)
            .setEncryptionType(encryption)
        rssi?.let { data.setSignalStrength(FloatValue.of(it)) }
        frequencyMhz?.let { data.setFrequencyMhz(Int32Value.of(it)) }
        channel?.let { data.setChannel(Int32Value.of(it)) }
        return WifiRecordWrapper(WifiBeaconRecord.newBuilder().setData(data).build(), "[WPA2-PSK-CCMP][ESS]")
    }
}
