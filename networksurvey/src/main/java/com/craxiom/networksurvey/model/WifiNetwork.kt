package com.craxiom.networksurvey.model

import com.craxiom.messaging.wifi.WifiBandwidth
import java.io.Serializable

data class WifiNetwork(
    val bssid: String,
    val signalStrength: Float?,
    val ssid: String,
    val frequency: Int?,
    val channel: Int?,
    val bandwidth: WifiBandwidth?,
    val encryptionType: String,
    val passpoint: Boolean?,
    val capabilities: String,
) : Serializable
