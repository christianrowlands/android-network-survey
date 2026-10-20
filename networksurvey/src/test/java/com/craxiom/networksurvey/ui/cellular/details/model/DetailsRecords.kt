package com.craxiom.networksurvey.ui.cellular.details.model

import android.telephony.TelephonyManager
import com.craxiom.messaging.ConnectionStatus
import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.GsmRecordData
import com.craxiom.messaging.LteBandwidth
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.LteRecordData
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.NrRecordData
import com.craxiom.messaging.UmtsRecord
import com.craxiom.messaging.UmtsRecordData
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.NetworkTechnologyInfo
import com.craxiom.networksurvey.model.NrRecordWrapper
import com.craxiom.networksurvey.util.TelephonyStateUtils
import com.google.protobuf.BoolValue
import com.google.protobuf.FloatValue
import com.google.protobuf.Int32Value
import com.google.protobuf.Int64Value
import com.google.protobuf.StringValue

/** Records and technology snapshots for the Cellular view's model tests. */
object DetailsRecords {

    fun lte(
        serving: Boolean = true,
        pci: Int? = 101,
        earfcn: Int? = 66_811,
        rsrp: Float? = -79f,
        rsrq: Float? = -8f,
        eci: Int? = 25_600_003,
        plmn: String? = "310-260",
        ta: Int? = 0,
        provider: String = "Mint",
    ): CellularRecordWrapper {
        val data = LteRecordData.newBuilder()
            .setMcc(Int32Value.of(310)).setMnc(Int32Value.of(260))
            .setTac(Int32Value.of(12_345))
            .setSnr(FloatValue.of(9f)).setCqi(Int32Value.of(13))
            .setLteBandwidth(LteBandwidth.MHZ_15)
            .setServingCell(BoolValue.of(serving))
            .setProvider(provider)
        plmn?.let { data.setPlmn(StringValue.of(it)) }
        pci?.let { data.setPci(Int32Value.of(it)) }
        earfcn?.let { data.setEarfcn(Int32Value.of(it)) }
        rsrp?.let { data.setRsrp(FloatValue.of(it)) }
        rsrq?.let { data.setRsrq(FloatValue.of(it)) }
        eci?.let { data.setEci(Int32Value.of(it)) }
        ta?.let { data.setTa(Int32Value.of(it)) }
        return CellularRecordWrapper(CellularProtocol.LTE, LteRecord.newBuilder().setData(data).build())
    }

    fun nr(
        serving: Boolean = true,
        status: ConnectionStatus = ConnectionStatus.PRIMARY_SERVING,
        narfcn: Int? = 520_110,
        pci: Int? = 271,
        ssRsrp: Float? = -84f,
        nci: Long? = 68_719_476_000L,
        bands: IntArray? = intArrayOf(41),
    ): NrRecordWrapper {
        val data = NrRecordData.newBuilder()
            .setMcc(Int32Value.of(310)).setMnc(Int32Value.of(260)).setPlmn(StringValue.of("310-260"))
            .setTac(Int32Value.of(22_222))
            .setSsRsrq(FloatValue.of(-11f)).setSsSinr(FloatValue.of(14f))
            .setServingCell(BoolValue.of(serving))
            .setConnectionStatus(status)
            .setProvider("T-Mobile")
        narfcn?.let { data.setNarfcn(Int32Value.of(it)) }
        pci?.let { data.setPci(Int32Value.of(it)) }
        ssRsrp?.let { data.setSsRsrp(FloatValue.of(it)) }
        nci?.let { data.setNci(Int64Value.of(it)) }
        return NrRecordWrapper(NrRecord.newBuilder().setData(data).build(), bands)
    }

    /** The NR leg of a 5G NSA connection: not the serving cell, reported as secondary serving. */
    fun nrSecondary(pci: Int = 271, narfcn: Int = 520_110): NrRecordWrapper =
        nr(serving = false, status = ConnectionStatus.SECONDARY_SERVING, pci = pci, narfcn = narfcn, nci = null)

    fun gsm(serving: Boolean = true, rssi: Float? = -80f, bsic: Int? = 10, arfcn: Int? = 128): CellularRecordWrapper {
        val data = GsmRecordData.newBuilder()
            .setMcc(Int32Value.of(234)).setMnc(Int32Value.of(10))
            .setLac(Int32Value.of(210)).setCi(Int32Value.of(4321))
            .setServingCell(BoolValue.of(serving))
        rssi?.let { data.setSignalStrength(FloatValue.of(it)) }
        bsic?.let { data.setBsic(Int32Value.of(it)) }
        arfcn?.let { data.setArfcn(Int32Value.of(it)) }
        return CellularRecordWrapper(CellularProtocol.GSM, GsmRecord.newBuilder().setData(data).build())
    }

    fun umts(serving: Boolean = true, rscp: Float? = -90f, psc: Int? = 300, uarfcn: Int? = 10_700): CellularRecordWrapper {
        val data = UmtsRecordData.newBuilder()
            .setMcc(Int32Value.of(262)).setMnc(Int32Value.of(2))
            .setLac(Int32Value.of(310)).setCid(Int32Value.of(0x0ABC1234))
            .setSignalStrength(FloatValue.of(-70f))
            .setServingCell(BoolValue.of(serving))
        rscp?.let { data.setRscp(FloatValue.of(it)) }
        psc?.let { data.setPsc(Int32Value.of(it)) }
        uarfcn?.let { data.setUarfcn(Int32Value.of(it)) }
        return CellularRecordWrapper(CellularProtocol.UMTS, UmtsRecord.newBuilder().setData(data).build())
    }

    fun technology(
        nrMode: TelephonyStateUtils.NrMode = TelephonyStateUtils.NrMode.NONE,
        baseDataRat: Int = TelephonyManager.NETWORK_TYPE_LTE,
        voice: String? = "VoLTE",
        data: String? = "LTE",
        overrideDisplay: String? = "None",
        overrideNetworkType: Int = 0,
        bandwidthsKhz: IntArray? = null,
        rows: List<TelephonyStateUtils.RegistrationRow>? = null,
    ) = NetworkTechnologyInfo(1, voice, data, overrideDisplay, overrideNetworkType, baseDataRat, nrMode, bandwidthsKhz, rows)
}
