package com.craxiom.networksurvey.logging

import com.craxiom.networksurvey.scout.clients.ScoutClientRecords
import com.craxiom.networksurvey.scout.clients.ScoutClientTestData
import com.craxiom.networksurvey.scout.wifi.ScoutHeard
import com.craxiom.networksurvey.services.NetworkSurveyService
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * The Wi-Fi clients and probe request CSV files: their columns, and rows that line up with them.
 */
class WifiClientCsvLoggerTest {
    private val service = mock(NetworkSurveyService::class.java)

    private fun <O> heard(observation: O) = ScoutHeard(
        observation, "NS-ABCD", 1_792_000_030_000L, null, false,
    )

    @Test
    fun `the client file has the client columns`() {
        val logger = WifiClientCsvLogger(service)
        assertArrayEquals(
            arrayOf(
                "deviceTime", "latitude", "longitude", "altitude", "speed", "accuracy", "missionId", "recordNumber",
                "sourceAddress", "bssid", "channel", "frequencyMhz", "signalStrength", "snr", "frameTypes",
                "deviceSerialNumber", "locationAge",
            ),
            logger.headers,
        )
        assertArrayEquals(arrayOf("CSV Version=0.1.0"), logger.headerComments)
    }

    @Test
    fun `a client row lines up with the header`() {
        val logger = WifiClientCsvLogger(service)
        val record = ScoutClientRecords.client(heard(ScoutClientTestData.client(1, -51, 0, 3)), "device-1", "M", 4)!!
        val row = logger.toRow(record).toList()
        val column = logger.headers.toList()::indexOf

        assertEquals(logger.headers.size, row.size)
        assertEquals("3e:7b:20:d4:91:01", row[column("sourceAddress")])
        assertEquals("-51.0", row[column("signalStrength")])
        assertEquals("DATA;NULL_DATA", row[column("frameTypes")])
        assertEquals("device-1", row[column("deviceSerialNumber")])
        assertEquals("", row[column("locationAge")])
    }

    @Test
    fun `the probe file has the probe columns and a row lines up with them`() {
        val logger = WifiProbeCsvLogger(service)
        assertArrayEquals(
            arrayOf(
                "deviceTime", "latitude", "longitude", "altitude", "speed", "accuracy", "missionId", "recordNumber",
                "sourceAddress", "destinationAddress", "bssid", "ssid", "channel", "frequencyMhz", "signalStrength",
                "snr", "nodeType", "standard", "deviceSerialNumber", "locationAge",
            ),
            logger.headers,
        )
        val record = ScoutClientRecords.probe(heard(ScoutClientTestData.probe(2, "eduroam", -60, 0)), "device-1", "M", 9)!!
        val row = logger.toRow(record).toList()
        val column = logger.headers.toList()::indexOf

        assertEquals(logger.headers.size, row.size)
        assertEquals("eduroam", row[column("ssid")])
        assertEquals("NON_AP_STA", row[column("nodeType")])
        assertEquals("IEEE80211N", row[column("standard")])
        assertEquals("", row[column("snr")])
    }
}
