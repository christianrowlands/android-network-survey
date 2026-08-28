package com.craxiom.networksurvey.logging

import com.craxiom.messaging.DeviceStatus
import com.craxiom.messaging.DeviceStatusData
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.google.protobuf.BoolValue
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
            "missionId", "recordNumber",
            "gnssAge", "networkAge", "mockLocation"
        )

        assertArrayEquals("Headers must match expected order", expectedHeaders, headers)
    }

    @Test
    fun `csv version is bumped for the per provider age and mock columns`() {
        val logger = DeviceStatusCsvLogger(mock(NetworkSurveyService::class.java))

        assertArrayEquals(
            "The CSV version must be bumped whenever a column is added",
            arrayOf("CSV Version=0.6.0"), logger.headerComments
        )
    }

    @Test
    fun `per provider ages and mock flag are written at the appended indices`() {
        val logger = DeviceStatusCsvLogger(mock(NetworkSurveyService::class.java))

        val deviceStatus = buildDeviceStatus(
            gnssAge = 4_000,
            networkAge = 90_000,
            mockLocation = true
        )
        val data = logger.convertToObjectArray(deviceStatus)

        assertEquals("gnssAge at index 19", "4000", data[19])
        assertEquals("networkAge at index 20", "90000", data[20])
        assertEquals("mockLocation at index 21", "true", data[21])
    }

    @Test
    fun `a reported false mock flag is written rather than left blank`() {
        val logger = DeviceStatusCsvLogger(mock(NetworkSurveyService::class.java))

        // This app only ever sets the flag when the location really was mocked, but the logger
        // renders whatever the message carries rather than second guessing the producer.
        val deviceStatus = buildDeviceStatus(mockLocation = false)
        val data = logger.convertToObjectArray(deviceStatus)

        assertEquals("A reported false must not be confused with unknown", "false", data[21])
    }

    @Test
    fun `an age is blanked when the fix it describes was blanked`() {
        val logger = DeviceStatusCsvLogger(mock(NetworkSurveyService::class.java))

        // The 0.0 coordinate filter blanks the provider position, and an age with no position to
        // describe would leave a consumer measuring the staleness of nothing.
        val deviceStatus = buildDeviceStatus(gnssAge = 4_000, networkAge = 90_000, hasExtraFixes = false)
        val data = logger.convertToObjectArray(deviceStatus)

        assertEquals("A blanked GNSS fix must blank its age", "", data[19])
        assertEquals("A blanked network fix must blank its age", "", data[20])
    }

    @Test
    fun `unknown ages and an unreported mock flag are written as empty`() {
        val logger = DeviceStatusCsvLogger(mock(NetworkSurveyService::class.java))

        // An age of 0 means unknown, which is what an app running below API 33 always reports,
        // and an absent mock flag means the app never looked. Neither may be written as a value.
        val deviceStatus = buildDeviceStatus()
        val data = logger.convertToObjectArray(deviceStatus)

        assertEquals("An unknown gnssAge must be empty", "", data[19])
        assertEquals("An unknown networkAge must be empty", "", data[20])
        assertEquals("An unreported mockLocation must be empty", "", data[21])
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
        recordNumber: Int = 1,
        gnssAge: Int = 0,
        networkAge: Int = 0,
        mockLocation: Boolean? = null,
        hasExtraFixes: Boolean = true
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
            .setGnssLatitude(if (hasExtraFixes) 38.8978 else 0.0)
            .setGnssLongitude(if (hasExtraFixes) -77.0366 else 0.0)
            .setGnssAltitude(16.0f)
            .setGnssAccuracy(3)
            .setNetworkLatitude(if (hasExtraFixes) 38.8979 else 0.0)
            .setNetworkLongitude(if (hasExtraFixes) -77.0367 else 0.0)
            .setNetworkAltitude(14.0f)
            .setNetworkAccuracy(50)
            .setDeviceSerialNumber("TEST-SERIAL-001")
            .setLocationAge(500)
            .setGnssAge(gnssAge)
            .setNetworkAge(networkAge)
            .also { builder ->
                if (mockLocation != null) builder.setMockLocation(BoolValue.of(mockLocation))
            }
            .build()

        return DeviceStatus.newBuilder()
            .setData(data)
            .build()
    }
}
