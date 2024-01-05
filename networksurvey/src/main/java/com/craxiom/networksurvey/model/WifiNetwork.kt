package com.craxiom.networksurvey.model

import java.io.Serializable

data class WifiNetwork(val bssid: String, val signalStrength: Float?) : Serializable
