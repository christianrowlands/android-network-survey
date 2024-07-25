package com.craxiom.networksurvey.ui.cellular.model

import com.craxiom.networksurvey.model.CellularProtocol
import java.io.Serializable

data class ServingSignalInfo(
    val cellularProtocol: CellularProtocol,
    val signalOne: Int,
    val signalTwo: Int,
) : Serializable {
    override fun toString(): String {
        val protocolSpecificIdentifier = when (cellularProtocol) {
            CellularProtocol.GSM -> "RSSI: $signalOne"
            CellularProtocol.CDMA -> "ECIO: $signalOne"
            CellularProtocol.UMTS -> "RSSI: $signalOne\nRSCP: $signalTwo"
            CellularProtocol.LTE -> "RSRP: $signalOne\nRSRQ: $signalTwo"
            CellularProtocol.NR -> "SS-RSRP: $signalOne\nSS-RSRQ: $signalTwo"
            else -> "Unknown: $signalOne"
        }
        return protocolSpecificIdentifier
    }
}
