package com.craxiom.networksurvey.logging.db

import com.craxiom.messaging.BluetoothRecord
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.messaging.ConnectionStatus
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.LteRecordData
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.NrRecordData
import com.craxiom.messaging.WifiBeaconRecord
import com.craxiom.messaging.WifiBeaconRecordData
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.craxiom.networksurvey.util.SignalBuckets
import com.google.protobuf.BoolValue
import com.google.protobuf.FloatValue
import com.google.protobuf.Int32Value
import com.google.protobuf.Int64Value
import com.google.protobuf.StringValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the extraction of the descriptive columns (technology, identity, signal, counts) that
 * make a surveyed point self-explanatory.
 */
class SurveyedPointDetailsTest {

    private fun lte(
        serving: Boolean,
        rsrp: Float = -97f,
        plmn: String? = null,
        missionId: String = "NS abc 20260914-101500",
    ): CellularRecordWrapper {
        val data = LteRecordData.newBuilder()
            .setMissionId(missionId)
            .setMcc(Int32Value.of(310)).setMnc(Int32Value.of(260))
            .setTac(Int32Value.of(12345)).setEci(Int32Value.of(67890123))
            .setRsrp(FloatValue.of(rsrp)).setRsrq(FloatValue.of(-12f))
            .setServingCell(BoolValue.of(serving))
            .setProvider("T-Mobile")
        plmn?.let { data.setPlmn(StringValue.of(it)) }
        return CellularRecordWrapper(
            CellularProtocol.LTE,
            LteRecord.newBuilder().setData(data).build()
        )
    }

    private fun nr(
        serving: Boolean,
        status: ConnectionStatus,
        ssRsrp: Float = -104f
    ): CellularRecordWrapper {
        val data = NrRecordData.newBuilder()
            .setMissionId("NS abc 20260914-101500")
            .setMcc(Int32Value.of(310)).setMnc(Int32Value.of(260))
            .setTac(Int32Value.of(22222)).setNci(Int64Value.of(1L shl 35))
            .setSsRsrp(FloatValue.of(ssRsrp)).setSsRsrq(FloatValue.of(-10f))
            .setServingCell(BoolValue.of(serving))
            .setConnectionStatus(status)
            .setProvider("T-Mobile")
        return CellularRecordWrapper(
            CellularProtocol.NR,
            NrRecord.newBuilder().setData(data).build()
        )
    }

    @Test
    fun nsaBatchUsesTheLteAnchorAndFlagsTheNrLeg() {
        val details = SurveyedPointDetails.fromCellular(
            listOf(nr(false, ConnectionStatus.SECONDARY_SERVING), lte(true))
        )

        assertEquals(SurveyedPointEntity.PROTOCOL_LTE, details.protocol)
        assertEquals("310-260", details.plmn)
        assertEquals("T-Mobile", details.provider)
        assertEquals(12345, details.area)
        assertEquals(67890123L, details.cid)
        assertEquals("310-260-12345-67890123", details.cellId)
        assertEquals(-97, details.signal)
        assertEquals(-12, details.signal2)
        assertEquals(SignalBuckets.FAIR, details.signalBucket)
        assertEquals(1, details.nrScg)
        assertEquals("NS abc 20260914-101500", details.missionId)
    }

    @Test
    fun nrStandaloneServingCellIsAnNrPointWithoutTheScgFlag() {
        val details = SurveyedPointDetails.fromCellular(
            listOf(
                nr(
                    true,
                    ConnectionStatus.PRIMARY_SERVING,
                    -80f
                )
            )
        )

        assertEquals(SurveyedPointEntity.PROTOCOL_NR, details.protocol)
        assertEquals(1L shl 35, details.cid)
        assertEquals(-80, details.signal)
        assertEquals(SignalBuckets.STRONG, details.signalBucket)
        assertEquals(0, details.nrScg)
    }

    @Test
    fun noServingRecordStillYieldsAPointWithUnknownTechnology() {
        val details = SurveyedPointDetails.fromCellular(
            listOf(
                lte(false),
                nr(false, ConnectionStatus.UNKNOWN)
            )
        )

        assertEquals(SurveyedPointEntity.PROTOCOL_NONE, details.protocol)
        assertNull(details.plmn)
        assertNull(details.cellId)
        assertEquals(0, details.signal)
        assertEquals(SignalBuckets.UNKNOWN, details.signalBucket)
        assertEquals("NS abc 20260914-101500", details.missionId)
    }

    @Test
    fun plmnStringKeepsLeadingZerosAndBlankMissionIdBecomesNull() {
        val details =
            SurveyedPointDetails.fromCellular(listOf(lte(true, plmn = "310-04", missionId = "")))

        assertEquals("310-04", details.plmn)
        assertEquals("310-04-12345-67890123", details.cellId)
        assertNull(details.missionId)
    }

    @Test
    fun wifiUsesTheStrongestAccessPoint() {
        fun ap(ssid: String, bssid: String, rssi: Float) = WifiRecordWrapper(
            WifiBeaconRecord.newBuilder().setData(
                WifiBeaconRecordData.newBuilder().setSsid(ssid).setBssid(bssid)
                    .setSignalStrength(FloatValue.of(rssi))
                    .setMissionId("")
            ).build(), ""
        )

        val details = SurveyedPointDetails.fromWifi(
            listOf(
                ap("A", "aa:aa", -70f),
                ap("", "bb:bb", -55f),
                ap("C", "cc:cc", -80f)
            )
        )

        assertEquals(3, details.deviceCount)
        assertEquals(-55, details.signal)
        assertEquals("bb:bb", details.label)
        assertEquals(SignalBuckets.STRONG, details.signalBucket)
        assertEquals(SurveyedPointEntity.PROTOCOL_NONE, details.protocol)
    }

    @Test
    fun bluetoothUsesTheStrongestDeviceAndFallsBackToItsAddress() {
        fun device(name: String?, address: String, rssi: Float): BluetoothRecord {
            val data = BluetoothRecordData.newBuilder().setSourceAddress(address)
                .setSignalStrength(FloatValue.of(rssi))
            name?.let { data.setOtaDeviceName(it) }
            return BluetoothRecord.newBuilder().setData(data).build()
        }

        val details = SurveyedPointDetails.fromBluetooth(
            listOf(
                device("Headset", "11:11", -80f),
                device(null, "22:22", -62f)
            )
        )

        assertEquals(2, details.deviceCount)
        assertEquals(-62, details.signal)
        assertEquals("22:22", details.label)
        assertEquals(SignalBuckets.GOOD, details.signalBucket)
    }
}
