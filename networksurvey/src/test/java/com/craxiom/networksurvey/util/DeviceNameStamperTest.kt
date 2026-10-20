package com.craxiom.networksurvey.util

import com.craxiom.messaging.DeviceStatus
import com.craxiom.messaging.DeviceStatusData
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.LteRecordData
import com.craxiom.messaging.WifiBeaconRecord
import com.craxiom.messaging.WifiBeaconRecordData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Tests the device name the streaming destinations (MQTT and gRPC) set on outgoing messages. */
class DeviceNameStamperTest {

    private val lte = LteRecord.newBuilder()
        .setData(LteRecordData.newBuilder().setDeviceSerialNumber(SERIAL).setDeviceName(OLD_NAME))
        .build()

    @Test
    fun `a name replaces the device name and keeps the rest of the record`() {
        val stamped = DeviceNameStamper.stamp(lte, NAME)

        assertEquals(NAME, stamped.data.deviceName)
        assertEquals(SERIAL, stamped.data.deviceSerialNumber)
    }

    @Test
    fun `an empty or missing name leaves the record as it is`() {
        assertSame(lte, DeviceNameStamper.stamp(lte, ""))
        assertSame(lte, DeviceNameStamper.stamp(lte, null))
    }

    @Test
    fun `every message type is stamped`() {
        val wifi = WifiBeaconRecord.newBuilder().setData(WifiBeaconRecordData.newBuilder().setSsid("lab")).build()
        val status = DeviceStatus.newBuilder().setData(DeviceStatusData.newBuilder().setDeviceSerialNumber(SERIAL)).build()

        assertEquals(NAME, DeviceNameStamper.stamp(wifi, NAME).data.deviceName)
        assertEquals("lab", DeviceNameStamper.stamp(wifi, NAME).data.ssid)
        assertEquals(NAME, DeviceNameStamper.stamp(status, NAME).data.deviceName)
    }

    private companion object {
        const val NAME = "field-phone-1"
        const val OLD_NAME = "old-name"
        const val SERIAL = "serial-1"
    }
}
