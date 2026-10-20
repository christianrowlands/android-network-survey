package com.craxiom.networksurvey.ui.map

import com.craxiom.messaging.CdmaRecord
import com.craxiom.messaging.CdmaRecordData
import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.GsmRecordData
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.LteRecordData
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.NrRecordData
import com.craxiom.messaging.UmtsRecord
import com.craxiom.messaging.UmtsRecordData
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.google.protobuf.BoolValue
import com.google.protobuf.FloatValue
import com.google.protobuf.Int32Value
import com.google.protobuf.Int64Value
import com.google.protobuf.StringValue

/** Serving-cell records for the map tests, one builder per technology. */
object CellRecords {

    fun lte(
        plmn: String? = "310-260",
        eci: Int = 25_600_103,
        tac: Int = 12345,
        rsrp: Float = -79f,
        earfcn: Int? = 66_536,
        provider: String = "Mint",
        serving: Boolean = true,
    ): CellularRecordWrapper {
        val data = LteRecordData.newBuilder()
            .setMcc(Int32Value.of(310)).setMnc(Int32Value.of(260))
            .setTac(Int32Value.of(tac)).setEci(Int32Value.of(eci))
            .setRsrp(FloatValue.of(rsrp)).setRsrq(FloatValue.of(-11f))
            .setServingCell(BoolValue.of(serving))
            .setProvider(provider)
        plmn?.let { data.setPlmn(StringValue.of(it)) }
        earfcn?.let { data.setEarfcn(Int32Value.of(it)) }
        return CellularRecordWrapper(CellularProtocol.LTE, LteRecord.newBuilder().setData(data).build())
    }

    fun nr(nci: Long = 1L shl 35, narfcn: Int? = 125_400, ssRsrp: Float = -98f): CellularRecordWrapper {
        val data = NrRecordData.newBuilder()
            .setMcc(Int32Value.of(310)).setMnc(Int32Value.of(260)).setPlmn(StringValue.of("310-260"))
            .setTac(Int32Value.of(22222)).setNci(Int64Value.of(nci))
            .setSsRsrp(FloatValue.of(ssRsrp)).setSsRsrq(FloatValue.of(-10f))
            .setServingCell(BoolValue.of(true))
            .setProvider("T-Mobile")
        narfcn?.let { data.setNarfcn(Int32Value.of(it)) }
        return CellularRecordWrapper(CellularProtocol.NR, NrRecord.newBuilder().setData(data).build())
    }

    fun gsm(ci: Int = 4321, lac: Int = 210): CellularRecordWrapper {
        val data = GsmRecordData.newBuilder()
            .setMcc(Int32Value.of(234)).setMnc(Int32Value.of(10))
            .setLac(Int32Value.of(lac)).setCi(Int32Value.of(ci))
            .setSignalStrength(FloatValue.of(-80f))
            .setServingCell(BoolValue.of(true))
        return CellularRecordWrapper(CellularProtocol.GSM, GsmRecord.newBuilder().setData(data).build())
    }

    fun umts(cid: Int = 98765, lac: Int = 310): CellularRecordWrapper {
        val data = UmtsRecordData.newBuilder()
            .setMcc(Int32Value.of(262)).setMnc(Int32Value.of(2))
            .setLac(Int32Value.of(lac)).setCid(Int32Value.of(cid))
            .setSignalStrength(FloatValue.of(-70f)).setRscp(FloatValue.of(-90f))
            .setServingCell(BoolValue.of(true))
        return CellularRecordWrapper(CellularProtocol.UMTS, UmtsRecord.newBuilder().setData(data).build())
    }

    fun cdma(): CellularRecordWrapper {
        val data = CdmaRecordData.newBuilder()
            .setSid(Int32Value.of(4)).setNid(Int32Value.of(5)).setBsid(Int32Value.of(6))
            .setEcio(FloatValue.of(-7f))
            .setServingCell(BoolValue.of(true))
        return CellularRecordWrapper(CellularProtocol.CDMA, CdmaRecord.newBuilder().setData(data).build())
    }
}
