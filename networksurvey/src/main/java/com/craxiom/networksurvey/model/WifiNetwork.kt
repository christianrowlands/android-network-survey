package com.craxiom.networksurvey.model

import java.io.Serializable

data class WifiNetwork(
    val bssid: String,
    val signalStrength: Float?,
    val ssid: String,
    val frequency: Int?,
    val channel: Int?,
    val encryptionType: String,
    val passpoint: Boolean?,
    val capabilities: String,
) : Serializable
