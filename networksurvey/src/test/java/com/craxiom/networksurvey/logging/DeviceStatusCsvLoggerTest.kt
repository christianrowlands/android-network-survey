package com.craxiom.networksurvey.logging

import com.craxiom.messaging.DeviceStatus
import com.craxiom.messaging.DeviceStatusData
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.google.protobuf.Int32Value
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Unit tests for [DeviceStatusCsvLogger] verifying that the CSV header array and the data
 * array produced by [DeviceStatusCsvLogger.convertToObjectArray] stay aligned.
 */
class DeviceStatusCsvLoggerTest {
    @Test
    fun `headers and data arrays have equal length`() {
        val logger = DeviceStatusCsvLogger(mock(NetworkSurveyService::class.java))

        val deviceStatus = buildDeviceStatus()
        val headers = logger.headers
        val data = logger.convertToObjectArray(deviceStatus)

        assertEquals(
            "Header count and data column count must match",
            headers.size, data.size
        )
    }

    @Test
    fun `missionId and recordNumber appear at expected header positions`() {
        val logger = DeviceStatusCsvLogger(mock(NetworkSurveyService::class.java))

        val headers = logger.headers
        val expectedHeaders = arrayOf(
            "deviceTime", "latitude", "longitude", "altitude", "speed", "accuracy",
            "batteryLevelPercent", "gnssLatitude", "gnssLongitude", "gnssAltitude", "gnssAccuracy",
            "networkLatitude", "networkLongitude", "networkAltitude", "networkAccuracy",
            "deviceSerialNumber", "locationAge",
            "missionId", "recordNumber"
        )

        assertArrayEquals("Headers must match expected order", expectedHeaders, headers)
    }

    @Test
    fun `missionId and recordNumber values are at correct indices`() {
        val logger = DeviceStatusCsvLogger(mock(NetworkSurveyService::class.java))

        val missionId = "test-mission-42"
        val recordNumber = 7
        val deviceStatus = buildDeviceStatus(missionId = missionId, recordNumber = recordNumber)
        val data = logger.convertToObjectArray(deviceStatus)

        assertEquals("missionId at index 17", missionId, data[17])
        assertEquals("recordNumber at index 18", recordNumber.toString(), data[18])
    }

    private fun buildDeviceStatus(
        missionId: String = "default-mission",
        recordNumber: Int = 1
    ): DeviceStatus {
        val data = DeviceStatusData.newBuilder()
            .setDeviceTime("2026-03-02T12:00:00Z")
            .setLatitude(38.8977)
            .setLongitude(-77.0365)
            .setAltitude(15.0f)
            .setSpeed(1.5f)
            .setAccuracy(5)
            .setMissionId(missionId)
            .setRecordNumber(recordNumber)
            .setBatteryLevelPercent(Int32Value.of(85))
            .setGnssLatitude(38.8978)
            .setGnssLongitude(-77.0366)
            .setGnssAltitude(16.0f)
            .setGnssAccuracy(3)
            .setNetworkLatitude(38.8979)
            .setNetworkLongitude(-77.0367)
            .setNetworkAltitude(14.0f)
            .setNetworkAccuracy(50)
            .setDeviceSerialNumber("TEST-SERIAL-001")
            .setLocationAge(500)
            .build()

        return DeviceStatus.newBuilder()
            .setData(data)
            .build()
    }
}
