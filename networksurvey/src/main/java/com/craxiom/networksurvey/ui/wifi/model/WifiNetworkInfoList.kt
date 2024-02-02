package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.networksurvey.model.WifiRecordWrapper
import java.io.Serializable

data class WifiNetworkInfoList(
    val networks: List<WifiRecordWrapper>,
) : Serializable
