package com.craxiom.networksurvey.logging.db

import com.craxiom.messaging.BluetoothRecord
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.LteRecordData
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.NrRecordData
import com.craxiom.messaging.WifiBeaconRecord
import com.craxiom.messaging.WifiBeaconRecordData
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.WifiRecordWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the location extraction that turns survey record batches into gate inputs.
 */
class SurveyedPointLocationTest {

    private fun lte(lat: Double, lon: Double, accuracy: Int, speed: Float): CellularRecordWrapper {
        val record = LteRecord.newBuilder().setData(
            LteRecordData.newBuilder().setLatitude(lat).setLongitude(lon).setAccuracy(accuracy)
                .setSpeed(speed)
        ).build()
        return CellularRecordWrapper(CellularProtocol.LTE, record)
    }

    private fun nr(lat: Double, lon: Double, accuracy: Int, speed: Float): CellularRecordWrapper {
        val record = NrRecord.newBuilder().setData(
            NrRecordData.newBuilder().setLatitude(lat).setLongitude(lon).setAccuracy(accuracy)
                .setSpeed(speed)
        ).build()
        return CellularRecordWrapper(CellularProtocol.NR, record)
    }

    @Test
    fun cellular_usesTheFirstRecordWithALocation() {
        val batch = listOf(nr(0.0, 0.0, 0, 0f), lte(38.0, -77.0, 12, 4.5f))

        val location = SurveyedPointLocation.fromCellular(batch)!!

        assertEquals(38.0, location.latitude, 0.0)
        assertEquals(-77.0, location.longitude, 0.0)
        assertEquals(12, location.accuracy)
        assertEquals(4.5f, location.speedMps)
    }

    @Test
    fun cellular_returnsNullWhenNoRecordHasALocation() {
        assertNull(SurveyedPointLocation.fromCellular(listOf(nr(0.0, 0.0, 0, 0f))))
        assertNull(SurveyedPointLocation.fromCellular(emptyList()))
    }

    @Test
    fun wifi_usesTheFirstRecord() {
        val record = WifiBeaconRecord.newBuilder().setData(
            WifiBeaconRecordData.newBuilder().setLatitude(38.5).setLongitude(-77.5).setAccuracy(8)
                .setSpeed(1f)
        ).build()

        val location = SurveyedPointLocation.fromWifi(listOf(WifiRecordWrapper(record, "")))!!

        assertEquals(38.5, location.latitude, 0.0)
        assertEquals(8, location.accuracy)
    }

    @Test
    fun bluetooth_usesTheFirstRecordWithALocation() {
        val record = BluetoothRecord.newBuilder().setData(
            BluetoothRecordData.newBuilder().setLatitude(39.0).setLongitude(-78.0).setAccuracy(20)
                .setSpeed(0f)
        ).build()

        val location = SurveyedPointLocation.fromBluetooth(listOf(record))!!

        assertEquals(39.0, location.latitude, 0.0)
        assertEquals(20, location.accuracy)
    }
}
